import importlib.util
from pathlib import Path
import sys
import unittest


MODULE_PATH = Path(__file__).resolve().parents[1] / "crawl_official_medical_sources.py"
SPEC = importlib.util.spec_from_file_location("crawler", MODULE_PATH)
crawler = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = crawler
SPEC.loader.exec_module(crawler)


class CrawlerTest(unittest.TestCase):
    def test_extracts_main_content_and_drops_navigation(self):
        paragraphs = "".join(f"<p>Clinical paragraph {i} contains sufficiently detailed official health information for extraction.</p>" for i in range(15))
        html = f"<html><body><nav><p>Navigation should never appear in the corpus despite being rather long.</p></nav><main><h1>Official title</h1><h2>Symptoms</h2>{paragraphs}</main></body></html>"
        title, sections = crawler.extract_sections(html, "Fallback")
        content = " ".join(p for section in sections for p in section["paragraphs"])
        self.assertEqual("Official title", title)
        self.assertIn("Clinical paragraph 4", content)
        self.assertNotIn("Navigation", content)

    def test_chunk_ids_are_stable_and_have_overlap(self):
        sections = [{"heading": "Symptoms", "paragraphs": ["A" * 700, "B" * 700, "C" * 700]}]
        first = crawler.build_chunks("official-source", ("CHEST_PAIN",), sections, target_chars=1000)
        second = crawler.build_chunks("official-source", ("CHEST_PAIN",), sections, target_chars=1000)
        self.assertEqual(first, second)
        self.assertEqual("chunk-official-source-001", first[0]["chunkId"])
        self.assertIn("B" * 700, first[1]["content"])

    def test_allowlist_contains_only_expected_official_hosts(self):
        allowed = {"www.who.int", "www.cdc.gov", "www.nhlbi.nih.gov", "www.nhs.uk"}
        self.assertGreaterEqual(len(crawler.SOURCES), 20)
        self.assertEqual(allowed, {crawler.urlparse(source.url).netloc for source in crawler.SOURCES})


if __name__ == "__main__":
    unittest.main()
