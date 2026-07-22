import hashlib
import json
from typing import Any, Dict, List

from langgraph.graph import END, StateGraph

from .config import settings
from .guardrails import inspect_input, inspect_output, validate_citations
from .knowledge import search_guidelines
from .providers import URGENCY_RANK, get_provider
from .schemas import AnalysisRequest, AnalysisResult, Citation, SafetyDecision, SafetyResult, Urgency


def input_guard_agent(state: Dict[str, Any]) -> Dict[str, Any]:
    request: AnalysisRequest = state["request"]
    reasons = inspect_input(request.chiefComplaint, request.freeText)
    return {**state, "blockedReasons": reasons, "agentTrace": ["InputGuardAgent:COMPLETED"]}


def evidence_retriever_agent(state: Dict[str, Any]) -> Dict[str, Any]:
    request: AnalysisRequest = state["request"]
    evidence = search_guidelines(
        [symptom.code for symptom in request.symptoms],
        f"{request.chiefComplaint} {' '.join(request.ruleReasonCodes)}",
    )
    return {**state, "evidence": evidence, "agentTrace": state["agentTrace"] + ["EvidenceRetrieverAgent:COMPLETED"]}


def clinical_summary_agent(state: Dict[str, Any]) -> Dict[str, Any]:
    provider = get_provider()
    draft = provider.generate(state["request"], state["evidence"])
    return {**state, "draft": draft, "provider": provider, "providerName": provider.name,
            "agentTrace": state["agentTrace"] + ["ClinicalSummaryAgent:COMPLETED"]}


def safety_critic_agent(state: Dict[str, Any]) -> Dict[str, Any]:
    critic = state["provider"].critique(state["request"], state["draft"], state["evidence"])
    violations = list(critic.get("violations", []))
    draft = state["draft"]
    values = [str(draft.get("caseSummary", "")), *map(str, draft.get("rationale", [])), *map(str, draft.get("missingQuestions", []))]
    violations.extend(inspect_output(values))
    return {**state, "criticViolations": sorted(set(violations)),
            "agentTrace": state["agentTrace"] + ["SafetyCriticAgent:COMPLETED"]}


def citation_verifier_agent(state: Dict[str, Any]) -> Dict[str, Any]:
    request: AnalysisRequest = state["request"]
    draft = state["draft"]
    blocked: List[str] = list(state["blockedReasons"]) + list(state["criticViolations"])
    proposed_value = draft.get("proposedUrgency")
    proposed = Urgency(proposed_value) if proposed_value else None
    if proposed is not None and request.ruleUrgency is not None and URGENCY_RANK[proposed.value] < URGENCY_RANK[request.ruleUrgency.value]:
        blocked.append("AI_ATTEMPTED_RULE_DOWNGRADE")
        proposed = request.ruleUrgency
    if request.ruleUrgency is None and proposed is not None:
        blocked.append("AI_ATTEMPTED_UNSUPPORTED_URGENCY")
        proposed = None
    evidence = state["evidence"]
    valid_ids = {item["chunkId"] for item in evidence}
    evidence_ids = [item["chunkId"] for item in draft.get("evidence", evidence)]
    if not validate_citations(evidence_ids, valid_ids):
        blocked.append("INVALID_CITATION")
    selected = [item for item in evidence if item["chunkId"] in evidence_ids]
    citations = [Citation(guidelineId=item["guidelineId"], chunkId=item["chunkId"], claimKey=f"urgency_reason_{index}",
        quote=item["quote"], title=item["title"], section=item["section"], sourceUrl=item["sourceUrl"],
        licenseNote=item["licenseNote"]) for index,item in enumerate(selected,1)]
    blocked = sorted(set(blocked))
    decision = SafetyDecision.BLOCK if blocked else SafetyDecision.PASS
    trace = state["agentTrace"] + ["CitationVerifierAgent:COMPLETED"]
    unsigned = {"caseSummary": draft["caseSummary"], "proposedUrgency": proposed.value if proposed else None,
        "rationale": draft["rationale"], "missingQuestions": draft["missingQuestions"],
        "citations": [item.model_dump() for item in citations], "safety": {"decision": decision.value, "reasonCodes": blocked},
        "agentTrace": trace}
    output_hash = hashlib.sha256(json.dumps(unsigned, ensure_ascii=False, sort_keys=True).encode("utf-8")).hexdigest()
    result = AnalysisResult(caseSummary=draft["caseSummary"], proposedUrgency=proposed, rationale=draft["rationale"],
        missingQuestions=draft["missingQuestions"], citations=citations,
        safety=SafetyResult(decision=decision, reasonCodes=blocked), provider=state["providerName"], model=settings.model,
        outputHash=output_hash, versions={"prompt": settings.prompt_version, "knowledgeBase": settings.knowledge_base_version,
        "rules": settings.rule_set_version}, agentTrace=trace)
    return {**state, "result": result}


def create_graph():
    graph = StateGraph(dict)
    graph.add_node("InputGuardAgent", input_guard_agent)
    graph.add_node("EvidenceRetrieverAgent", evidence_retriever_agent)
    graph.add_node("ClinicalSummaryAgent", clinical_summary_agent)
    graph.add_node("SafetyCriticAgent", safety_critic_agent)
    graph.add_node("CitationVerifierAgent", citation_verifier_agent)
    graph.set_entry_point("InputGuardAgent")
    graph.add_edge("InputGuardAgent", "EvidenceRetrieverAgent")
    graph.add_edge("EvidenceRetrieverAgent", "ClinicalSummaryAgent")
    graph.add_edge("ClinicalSummaryAgent", "SafetyCriticAgent")
    graph.add_edge("SafetyCriticAgent", "CitationVerifierAgent")
    graph.add_edge("CitationVerifierAgent", END)
    return graph.compile()


WORKFLOW = create_graph()


def analyze(request: AnalysisRequest) -> AnalysisResult:
    return WORKFLOW.invoke({"request": request})["result"]
