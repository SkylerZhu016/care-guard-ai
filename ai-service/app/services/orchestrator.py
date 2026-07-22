"""流水线编排器 —— STRUCTURE → RETRIEVE → RISK → SAFETY → SUMMARY"""
import json
import time
import logging
from datetime import datetime
from sqlalchemy.orm import Session
from app.models import (
    AgentRun, AgentRunStep, SymptomExtraction, TriageResult, Citation,
    SafetyAlert, Visit, RuleHit, KnowledgeChunk, SystemConfig,
)
from app.agents import run_structure, run_retrieval, run_risk, run_safety, run_summary, _form_to_text
from app.rag.retriever import hybrid_search
from app.safety.checker import check_input_injection
from app.core.config import get_settings
import httpx

logger = logging.getLogger(__name__)
settings = get_settings()

STEP_ORDER = ["STRUCTURE", "RETRIEVE", "RISK", "SAFETY", "SUMMARY"]


def run_pipeline(db: Session, run_id: int, visit_id: int):
    """执行完整流水线，逐步写 agent_run_steps，完成后回调后端"""
    run = db.query(AgentRun).filter_by(id=run_id).first()
    if not run:
        logger.error(f"AgentRun {run_id} 不存在")
        return

    visit = db.query(Visit).filter_by(id=visit_id).first()
    if not visit:
        _fail(db, run, "问诊单不存在")
        return

    form_data = visit.form_data or {}
    text = _form_to_text(form_data)

    # 输入侧注入检测
    inj = check_input_injection(text)
    if inj:
        _create_alert(db, "PROMPT_INJECTION", "HIGH", visit_id, run_id, f"输入命中注入特征：{inj}")
        _fail(db, run, f"输入被安全拦截：{inj}")
        _callback(run_id, "REVIEW_FAILED")
        return

    run.status = "RUNNING"
    run.started_at = datetime.utcnow()
    run.model_name = "mock-llm-v1" if settings.MOCK_LLM else settings.LLM_MODEL
    db.commit()

    total_tokens = 0
    knowledge_version = _get_config(db, "knowledge_version", "KV-1")
    rule_version = _get_config(db, "rule_version", "RV-1")

    try:
        # ===== Step 1: STRUCTURE =====
        structured, tokens = _exec_step(db, run, "STRUCTURE", lambda: run_structure(form_data))
        total_tokens += tokens
        _save_extraction(db, visit_id, run_id, text, structured, tokens)

        # ===== Step 2: RETRIEVE =====
        queries, tokens = _exec_step(db, run, "RETRIEVE", lambda: run_retrieval(structured))
        total_tokens += tokens

        # 检索知识库
        query_text = " ".join(queries)
        chunks = hybrid_search(db, query_text, top_k=settings.RAG_TOP_K)
        citations = _save_citations(db, visit_id, run_id, chunks, knowledge_version)
        has_citations = len(citations) > 0

        # 读取规则命中（由 Spring Boot 写入）
        rule_hits = _get_rule_hits(db, visit_id)

        # ===== Step 3: RISK =====
        risk_analysis, tokens = _exec_step(db, run, "RISK", lambda: run_risk(structured, rule_hits, citations))
        total_tokens += tokens

        # 合并风险等级：规则优先
        rule_max = _max_risk([rh.get("risk_level") for rh in rule_hits])
        ai_risk = risk_analysis.get("risk_level", "LOW")
        final_risk = _merge_risk(rule_max, ai_risk)
        risk_analysis["risk_level"] = final_risk

        # ===== Step 4: SAFETY =====
        safety_content = json.dumps(risk_analysis, ensure_ascii=False)
        safety_result, tokens = _exec_step(db, run, "SAFETY",
            lambda: run_safety(safety_content, risk_analysis.get("risk_points", []), rule_hits, has_citations))
        total_tokens += tokens

        if safety_result.get("verdict") == "FAIL":
            run.status = "REVIEW_FAILED"
            run.finished_at = datetime.utcnow()
            run.total_tokens = total_tokens
            run.knowledge_version = knowledge_version
            run.rule_version = rule_version
            db.commit()
            _create_alert(db, "REVIEW_FAILED", "HIGH", visit_id, run_id,
                          "AI 输出未通过安全审查", {"issues": safety_result.get("issues", [])})
            _save_triage(db, visit_id, run_id, final_risk, risk_analysis, safety_result, citations,
                         knowledge_version, rule_version, has_citations=False)
            _callback(run_id, "REVIEW_FAILED")
            return

        # ===== Step 5: SUMMARY =====
        summary, tokens = _exec_step(db, run, "SUMMARY", lambda: run_summary(structured, risk_analysis, citations))
        total_tokens += tokens

        # 写 triage_results
        _save_triage(db, visit_id, run_id, final_risk, risk_analysis, safety_result, citations,
                     knowledge_version, rule_version, summary=summary)

        run.status = "COMPLETED"
        run.finished_at = datetime.utcnow()
        run.total_tokens = total_tokens
        run.knowledge_version = knowledge_version
        run.rule_version = rule_version
        run.prompt_versions = json.dumps({"STRUCTURE": "1", "RETRIEVE": "1", "RISK": "1", "SAFETY": "1", "SUMMARY": "1"})
        db.commit()

        _callback(run_id, "COMPLETED")

    except Exception as e:
        logger.exception(f"流水线异常 run={run_id}")
        _fail(db, run, str(e))
        _create_alert(db, "MODEL_ERROR", "MEDIUM", visit_id, run_id, f"AI 流水线异常：{e}")
        _callback(run_id, "FAILED")


def _exec_step(db: Session, run: AgentRun, step_name: str, fn):
    """执行单步并记录"""
    step = AgentRunStep(
        run_id=run.id, step=step_name, status="RUNNING",
        started_at=datetime.utcnow(),
    )
    db.add(step)
    db.commit()
    db.refresh(step)

    run.current_step = step_name
    db.commit()

    t0 = time.time()
    try:
        result, tokens = fn()
        step.status = "SUCCESS"
        step.tokens = tokens
        step.duration_ms = int((time.time() - t0) * 1000)
        step.finished_at = datetime.utcnow()
        try:
            step.output_json = json.dumps(result, ensure_ascii=False) if isinstance(result, (dict, list)) else str(result)
        except Exception:
            step.output_json = str(result)[:5000]
        db.commit()
        return result, tokens
    except Exception as e:
        step.status = "FAILED"
        step.error = str(e)
        step.duration_ms = int((time.time() - t0) * 1000)
        step.finished_at = datetime.utcnow()
        db.commit()
        raise


def _save_extraction(db, visit_id, run_id, text, structured, tokens):
    ext = SymptomExtraction(
        visit_id=visit_id, agent_run_id=run_id, raw_text=text[:5000],
        extracted_json=json.dumps(structured, ensure_ascii=False),
        missing_fields=json.dumps(structured.get("missing_fields", [])),
        confidence=structured.get("confidence", 0.0),
        schema_version=structured.get("schema_version", "1.0"),
        model_name="mock-llm-v1" if settings.MOCK_LLM else settings.LLM_MODEL,
        prompt_version="1",
    )
    db.add(ext)
    db.commit()


def _save_citations(db, visit_id, run_id, chunks, kv):
    cits = []
    for c in chunks:
        cit = Citation(
            visit_id=visit_id, agent_run_id=run_id,
            chunk_id=c["chunk_id"], document_id=c["document_id"],
            title=c["title"], section=c.get("section"), page_no=c.get("page_no"),
            snippet=c["snippet"], score=c["score"], knowledge_version=kv,
        )
        db.add(cit)
        cits.append(c)
    db.commit()
    return cits


def _get_rule_hits(db, visit_id):
    rows = db.query(RuleHit).filter_by(visit_id=visit_id).all()
    return [{"rule_code": r.rule_code, "rule_name": r.rule_name, "category": r.category,
             "risk_level": r.risk_level, "message": r.message} for r in rows]


def _save_triage(db, visit_id, run_id, risk_level, risk_analysis, safety_result, citations,
                 kv, rv, summary=None, has_citations=True):
    existing = db.query(TriageResult).filter_by(visit_id=visit_id).first()
    if existing:
        db.delete(existing)
        db.commit()
    tr = TriageResult(
        visit_id=visit_id, agent_run_id=run_id, risk_level=risk_level,
        risk_summary=risk_analysis.get("risk_summary", ""),
        risk_points=json.dumps(risk_analysis.get("risk_points", []), ensure_ascii=False),
        summary_for_review=json.dumps(summary, ensure_ascii=False) if summary else None,
        safety_status=safety_result.get("verdict", "PASS"),
        disclaimer="本内容由 AI 生成，仅供教学参考，不能替代医生诊断。",
        knowledge_version=kv, rule_version=rv,
        model_name="mock-llm-v1" if settings.MOCK_LLM else settings.LLM_MODEL,
        prompt_version="1",
    )
    db.add(tr)
    db.commit()


def _fail(db, run, msg):
    run.status = "FAILED"
    run.error_message = msg
    run.finished_at = datetime.utcnow()
    db.commit()


def _create_alert(db, atype, level, visit_id, run_id, desc, detail=None):
    alert = SafetyAlert(
        type=atype, level=level, visit_id=visit_id, agent_run_id=run_id,
        description=desc, detail=json.dumps(detail or {}, ensure_ascii=False),
    )
    db.add(alert)
    db.commit()


def _get_config(db, key, default=""):
    row = db.query(SystemConfig).filter_by(config_key=key).first()
    return row.config_value if row else default


def _callback(run_id, status):
    """回调后端 /api/internal/agent-runs/{runId}/completed"""
    try:
        headers = {"X-Internal-Token": settings.INTERNAL_TOKEN, "Content-Type": "application/json"}
        payload = {"runStatus": status}
        with httpx.Client(timeout=10) as client:
            client.post(f"{settings.BACKEND_URL}/api/internal/agent-runs/{run_id}/completed",
                        headers=headers, json=payload)
    except Exception as e:
        logger.warning(f"回调后端失败: {e}")


RISK_ORDER = {"LOW": 0, "MEDIUM": 1, "HIGH": 2, "CRITICAL": 3}


def _max_risk(levels):
    if not levels:
        return "LOW"
    return max(levels, key=lambda x: RISK_ORDER.get(x, 0))


def _merge_risk(rule_risk, ai_risk):
    """规则优先：规则等级不可被 AI 降级"""
    if RISK_ORDER.get(rule_risk, 0) >= RISK_ORDER.get(ai_risk, 0):
        return rule_risk
    return ai_risk
