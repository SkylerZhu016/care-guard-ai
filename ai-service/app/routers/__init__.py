"""FastAPI 路由"""
from fastapi import APIRouter, Depends, Header, HTTPException, BackgroundTasks
from sqlalchemy.orm import Session
from app.core.db import get_db
from app.core.config import get_settings
from app.services.orchestrator import run_pipeline
from app.services.knowledge import ingest_document, backfill_embeddings
from app.rag.retriever import hybrid_search

settings = get_settings()
router = APIRouter()


def verify_token(x_internal_token: str = Header(...)):
    if x_internal_token != settings.INTERNAL_TOKEN:
        raise HTTPException(status_code=403, detail="Invalid internal token")


@router.get("/health")
def health():
    return {"status": "ok", "mock_llm": settings.MOCK_LLM}


@router.post("/internal/pipeline/start", dependencies=[Depends(verify_token)])
def start_pipeline(payload: dict, bg: BackgroundTasks, db: Session = Depends(get_db)):
    """启动流水线（异步执行）"""
    run_id = payload.get("runId")
    visit_id = payload.get("visitId")
    if not run_id or not visit_id:
        raise HTTPException(status_code=400, detail="runId and visitId required")
    bg.add_task(run_pipeline, db, run_id, visit_id)
    return {"status": "accepted", "runId": run_id}


@router.post("/internal/knowledge/ingest", dependencies=[Depends(verify_token)])
def ingest(payload: dict, bg: BackgroundTasks, db: Session = Depends(get_db)):
    """摄取知识文档（异步）"""
    doc_id = payload.get("documentId")
    file_url = payload.get("fileUrl")
    raw_text = payload.get("rawText")
    if not doc_id:
        raise HTTPException(status_code=400, detail="documentId required")
    bg.add_task(ingest_document, db, doc_id, file_url, raw_text)
    return {"status": "accepted", "documentId": doc_id}


@router.post("/internal/search", dependencies=[Depends(verify_token)])
def search(payload: dict, db: Session = Depends(get_db)):
    """向量+关键词混合检索"""
    query = payload.get("query", "")
    top_k = payload.get("topK", 5)
    return hybrid_search(db, query, top_k)


@router.post("/internal/embeddings/backfill", dependencies=[Depends(verify_token)])
def backfill(db: Session = Depends(get_db)):
    """手动触发 embedding 补算"""
    count = backfill_embeddings(db)
    return {"backfilled": count}
