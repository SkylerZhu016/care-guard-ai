"""RAG 组件测试（离线）"""
import pytest
from app.rag.embedder import HashEmbedder, tokenize
from app.rag.chunker import chunk_text


class TestEmbedder:
    def test_dimension(self):
        emb = HashEmbedder(dim=256)
        vec = emb.embed("胸痛伴呼吸困难")
        assert len(vec) == 256

    def test_normalized(self):
        vec = HashEmbedder(256).embed("发热咳嗽")
        norm = sum(v * v for v in vec) ** 0.5
        assert abs(norm - 1.0) < 0.01  # L2 归一

    def test_deterministic(self):
        e1 = HashEmbedder(256).embed("头痛")
        e2 = HashEmbedder(256).embed("头痛")
        assert e1 == e2

    def test_different_text_different_vec(self):
        e1 = HashEmbedder(256).embed("胸痛")
        e2 = HashEmbedder(256).embed("腹泻")
        assert e1 != e2

    def test_empty(self):
        vec = HashEmbedder(256).embed("")
        assert all(v == 0.0 for v in vec)


class TestTokenize:
    def test_chinese_bigram(self):
        tokens = tokenize("胸痛呼吸困难")
        assert "胸痛" in tokens
        assert "呼吸" in tokens

    def test_english(self):
        tokens = tokenize("chest pain fever")
        assert "chest" in tokens
        assert "fever" in tokens


class TestChunker:
    def test_basic_chunk(self):
        text = "第一章 概述\n急性发热是常见症状。\n\n第二章 处理\n高热不退需就诊。"
        chunks = chunk_text(text, target_size=100)
        assert len(chunks) >= 1
        assert all("content" in c for c in chunks)
        assert all("chunk_no" in c for c in chunks)

    def test_empty(self):
        assert chunk_text("") == []
        assert chunk_text("   ") == []

    def test_section_detection(self):
        text = "# 胸痛指南\n胸痛伴呼吸困难属红旗症状。\n需立即就医评估。"
        chunks = chunk_text(text)
        assert len(chunks) >= 1
        assert chunks[0]["section"] is not None

    def test_chunk_no_sequence(self):
        text = "\n\n".join([f"段落{i}内容内容内容内容内容内容内容" for i in range(10)])
        chunks = chunk_text(text, target_size=50)
        nos = [c["chunk_no"] for c in chunks]
        assert nos == list(range(len(chunks)))
