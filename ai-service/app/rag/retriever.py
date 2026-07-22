"""混合检索器：向量余弦 + 关键词重叠"""
import math
from sqlalchemy.orm import Session
from app.models import KnowledgeChunk, KnowledgeDocument
from app.rag.embedder import get_embedder, tokenize
from app.core.config import get_settings

settings = get_settings()


def hybrid_search(db: Session, query: str, top_k: int = None) -> list[dict]:
    """混合检索：cosine(0.6) + keyword_overlap(0.4)
    返回 [{chunk_id, document_id, title, section, page_no, snippet, score, knowledge_version}]
    """
    top_k = top_k or settings.RAG_TOP_K
    embedder = get_embedder()
    query_vec = embedder.embed(query)
    query_tokens = set(tokenize(query))

    # 拉取所有 enabled 文档的 enabled 分块
    rows = (
        db.query(KnowledgeChunk, KnowledgeDocument)
        .join(KnowledgeDocument, KnowledgeChunk.document_id == KnowledgeDocument.id)
        .filter(KnowledgeChunk.enabled == True, KnowledgeDocument.status == "ENABLED", KnowledgeDocument.deleted == False)
        .all()
    )

    scored = []
    for chunk, doc in rows:
        # 向量余弦
        if chunk.embedding is not None:
            vec = list(chunk.embedding)
            cos = sum(a * b for a, b in zip(query_vec, vec)) / (
                math.sqrt(sum(a * a for a in query_vec)) * math.sqrt(sum(b * b for b in vec)) + 1e-9
            )
        else:
            cos = 0.0

        # 关键词重叠
        chunk_tokens = set(tokenize(chunk.content or ""))
        overlap = len(query_tokens & chunk_tokens) / (len(query_tokens) + 1e-9) if query_tokens else 0.0

        score = 0.6 * cos + 0.4 * overlap
        if score >= settings.RAG_MIN_SCORE:
            scored.append({
                "chunk_id": chunk.id,
                "document_id": doc.id,
                "title": doc.title,
                "section": chunk.section,
                "page_no": chunk.page_no,
                "snippet": (chunk.content or "")[:300],
                "score": round(score, 4),
                "knowledge_version": f"KV-{doc.version}",
            })

    scored.sort(key=lambda x: x["score"], reverse=True)
    return scored[:top_k]
