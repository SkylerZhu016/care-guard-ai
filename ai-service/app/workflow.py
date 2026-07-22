import hashlib
import json
from typing import Any, Dict, List
from langgraph.graph import END, StateGraph

from .config import settings
from .guardrails import inspect_input, validate_citations
from .knowledge import VALID_CHUNK_IDS, search_guidelines
from .providers import get_provider, URGENCY_RANK
from .schemas import AnalysisRequest, AnalysisResult, Citation, SafetyDecision, SafetyResult, Urgency


def input_guard(state: Dict[str, Any]) -> Dict[str, Any]:
    request: AnalysisRequest = state["request"]
    reasons = inspect_input(request.chiefComplaint, request.freeText)
    return {**state, "blockedReasons": reasons}


def retrieve(state: Dict[str, Any]) -> Dict[str, Any]:
    request: AnalysisRequest = state["request"]
    evidence = search_guidelines([symptom.code for symptom in request.symptoms])
    return {**state, "evidence": evidence}


def review_risk(state: Dict[str, Any]) -> Dict[str, Any]:
    provider = get_provider()
    draft = provider.generate(state["request"], state["evidence"])
    return {**state, "draft": draft, "providerName": provider.name}


def build_output(state: Dict[str, Any]) -> Dict[str, Any]:
    request: AnalysisRequest = state["request"]
    draft = state["draft"]
    blocked: List[str] = state["blockedReasons"]
    proposed = Urgency(draft["proposedUrgency"])
    if URGENCY_RANK[proposed] < URGENCY_RANK[request.ruleUrgency]:
        blocked.append("AI_ATTEMPTED_RULE_DOWNGRADE")
        proposed = request.ruleUrgency
    evidence = state["evidence"]
    if not validate_citations([item["chunkId"] for item in evidence], VALID_CHUNK_IDS):
        blocked.append("INVALID_CITATION")
    citations = [
        Citation(
            guidelineId=item["guidelineId"], chunkId=item["chunkId"], claimKey=f"urgency_reason_{index}",
            quote=item["quote"], title=item["title"], section=item["section"]
        ) for index, item in enumerate(evidence, 1)
    ]
    decision = SafetyDecision.BLOCK if blocked else SafetyDecision.PASS
    unsigned = {
        "caseSummary": draft["caseSummary"], "proposedUrgency": proposed.value,
        "rationale": draft["rationale"], "missingQuestions": draft["missingQuestions"],
        "citations": [item.model_dump() for item in citations], "safety": {"decision": decision.value, "reasonCodes": blocked},
    }
    output_hash = hashlib.sha256(json.dumps(unsigned, ensure_ascii=False, sort_keys=True).encode("utf-8")).hexdigest()
    result = AnalysisResult(
        caseSummary=draft["caseSummary"], proposedUrgency=proposed, rationale=draft["rationale"],
        missingQuestions=draft["missingQuestions"], citations=citations,
        safety=SafetyResult(decision=decision, reasonCodes=blocked), provider=state["providerName"], model=settings.model,
        outputHash=output_hash, versions={"prompt": settings.prompt_version, "knowledgeBase": settings.knowledge_base_version, "rules": settings.rule_set_version},
    )
    return {**state, "result": result}


def create_graph():
    graph = StateGraph(dict)
    graph.add_node("InputGuard", input_guard)
    graph.add_node("GuidelineRetriever", retrieve)
    graph.add_node("RiskReviewer", review_risk)
    graph.add_node("SafetyAndCitationVerifier", build_output)
    graph.set_entry_point("InputGuard")
    graph.add_edge("InputGuard", "GuidelineRetriever")
    graph.add_edge("GuidelineRetriever", "RiskReviewer")
    graph.add_edge("RiskReviewer", "SafetyAndCitationVerifier")
    graph.add_edge("SafetyAndCitationVerifier", END)
    return graph.compile()


WORKFLOW = create_graph()


def analyze(request: AnalysisRequest) -> AnalysisResult:
    state = WORKFLOW.invoke({"request": request})
    return state["result"]

