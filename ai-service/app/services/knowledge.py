"""知识库摄取服务"""
import logging
from datetime import datetime
from sqlalchemy.orm import Session
from app.models import KnowledgeDocument, KnowledgeChunk, SystemConfig
from app.rag.chunker import chunk_text
from app.rag.embedder import get_embedder
from app.core.config import get_settings
import httpx

logger = logging.getLogger(__name__)
settings = get_settings()


def ingest_document(db: Session, document_id: int, file_url: str = None, raw_text: str = None):
    """摄取文档：取文件 → 解析 → 分块 → 嵌入 → 写 chunks"""
    doc = db.query(KnowledgeDocument).filter_by(id=document_id).first()
    if not doc:
        logger.error(f"文档 {document_id} 不存在")
        return

    try:
        # 获取文本内容
        if raw_text:
            text = raw_text
        elif file_url:
            text = _fetch_text(file_url)
        else:
            text = ""

        if not text.strip():
            doc.status = "INGEST_FAILED"
            db.commit()
            return

        # 分块
        chunks = chunk_text(text)
        if not chunks:
            doc.status = "INGEST_FAILED"
            db.commit()
            return

        # 删除旧分块
        db.query(KnowledgeChunk).filter_by(document_id=document_id).delete()
        db.commit()

        # 嵌入并写入
        embedder = get_embedder()
        for c in chunks:
            vec = embedder.embed(c["content"])
            chunk = KnowledgeChunk(
                document_id=document_id, chunk_no=c["chunk_no"],
                content=c["content"], section=c.get("section"),
                page_no=c.get("page_no"), embedding=vec,
                version=doc.version, enabled=True,
            )
            db.add(chunk)

        doc.status = "ENABLED"
        doc.updated_at = datetime.utcnow()
        db.commit()

        # 递增知识库版本
        _bump_version(db, "knowledge_version")
        logger.info(f"文档 {document_id} 摄取完成，{len(chunks)} 个分块")

    except Exception as e:
        logger.exception(f"摄取文档 {document_id} 失败")
        doc.status = "INGEST_FAILED"
        db.commit()


def backfill_embeddings(db: Session):
    """启动时为缺失 embedding 的分块补算向量"""
    chunks = db.query(KnowledgeChunk).filter(KnowledgeChunk.embedding.is_(None)).all()
    if not chunks:
        return 0

    embedder = get_embedder()
    for c in chunks:
        c.embedding = embedder.embed(c.content)
    db.commit()
    logger.info(f"补算 {len(chunks)} 个分块的 embedding")
    return len(chunks)


def _fetch_text(file_url: str) -> str:
    """从后端下载文件并解析文本"""
    headers = {"X-Internal-Token": settings.INTERNAL_TOKEN}
    with httpx.Client(timeout=30) as client:
        resp = client.get(file_url, headers=headers)
        resp.raise_for_status()
        content_type = resp.headers.get("content-type", "")

        if "pdf" in content_type or file_url.endswith(".pdf"):
            return _parse_pdf(resp.content)
        return resp.text


def _parse_pdf(data: bytes) -> str:
    try:
        from pypdf import PdfReader
        import io
        reader = PdfReader(io.BytesIO(data))
        return "\n\n".join(page.extract_text() or "" for page in reader.pages)
    except Exception as e:
        logger.warning(f"PDF 解析失败: {e}")
        return ""


def _bump_version(db: Session, key: str):
    row = db.query(SystemConfig).filter_by(config_key=key).first()
    if row:
        # KV-1 → KV-2
        try:
            num = int(row.config_value.split("-")[-1]) + 1
            row.config_value = f"{row.config_value.split('-')[0]}-{num}"
        except Exception:
            row.config_value = f"{row.config_value}-1"
        db.commit()
