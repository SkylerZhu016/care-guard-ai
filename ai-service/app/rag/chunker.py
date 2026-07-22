"""文本分块器"""
import re


def chunk_text(text: str, target_size: int = 400, overlap: int = 60) -> list[dict]:
    """按段落聚合分块，保留章节信息。
    返回 [{content, section, chunk_no}]
    """
    if not text or not text.strip():
        return []

    # 按双换行或单换行分段
    paragraphs = [p.strip() for p in re.split(r"\n\s*\n|\n", text) if p.strip()]
    if not paragraphs:
        return []

    chunks = []
    current = ""
    current_section = ""
    chunk_no = 0

    for para in paragraphs:
        # 识别章节标题（# 开头或短行+冒号）
        if para.startswith("#") or (len(para) < 30 and ("：" in para or ":" in para)):
            current_section = para.lstrip("#").strip()

        if len(current) + len(para) > target_size and current:
            chunks.append({"content": current.strip(), "section": current_section, "chunk_no": chunk_no})
            chunk_no += 1
            # overlap
            current = current[-overlap:] + "\n" + para if overlap else para
        else:
            current = (current + "\n" + para) if current else para

    if current.strip():
        chunks.append({"content": current.strip(), "section": current_section, "chunk_no": chunk_no})

    return chunks
