r"""Build an auditable medical RAG corpus from a fixed official-source allowlist.

This is intentionally a snapshot builder, not an unrestricted crawler.  The
backend ingests the generated JSON and never reaches out to the internet at
runtime.  Run it with the project-approved interpreter:

    D:\Anaconda\envs\ML3.9\python.exe data-pipeline\crawl_official_medical_sources.py
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import tempfile
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, Iterable, List, Sequence, Tuple
from urllib.parse import urlparse
from urllib.robotparser import RobotFileParser

import requests
from bs4 import BeautifulSoup, Tag


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CORPUS = ROOT / "backend" / "src" / "main" / "resources" / "knowledge" / "official-corpus.json"
DEFAULT_REPORT = ROOT / "data-pipeline" / "corpus" / "crawl-report.json"
USER_AGENT = "MedsimMedicalCorpusBot/1.0 (+local engineering project; respectful snapshot builder)"
SPACE = re.compile(r"\s+")


@dataclass(frozen=True)
class Source:
    source_id: str
    title: str
    publisher: str
    url: str
    topics: Tuple[str, ...]
    license_note: str
    language: str = "en"


WHO_LICENSE = (
    "World Health Organization public web page. Reuse remains subject to the WHO page's "
    "copyright and licensing terms; this corpus stores a dated engineering snapshot with attribution."
)
CDC_LICENSE = (
    "U.S. Centers for Disease Control and Prevention public web page. CDC-authored material is generally "
    "public domain unless the page identifies third-party material; attribution and source verification are retained."
)
NIH_LICENSE = (
    "U.S. National Institutes of Health/NHLBI public web page. Federal material is generally public domain "
    "unless otherwise marked; attribution and the original URL are retained."
)
NHS_LICENSE = (
    "NHS website content, Crown copyright. Reuse is subject to the Open Government Licence v3.0 and any "
    "page-specific exclusions; attribution and the original URL are retained."
)


SOURCES: Tuple[Source, ...] = (
    Source("who-hypertension", "Hypertension", "World Health Organization", "https://www.who.int/news-room/fact-sheets/detail/hypertension", ("HYPERTENSION", "BP_RECORD", "HEADACHE", "CARDIOVASCULAR"), WHO_LICENSE),
    Source("who-cardiovascular-diseases", "Cardiovascular diseases (CVDs)", "World Health Organization", "https://www.who.int/news-room/fact-sheets/detail/cardiovascular-diseases-(cvds)", ("CARDIOVASCULAR", "CHEST_PAIN", "DYSPNEA", "STROKE"), WHO_LICENSE),
    Source("cdc-high-blood-pressure", "About High Blood Pressure", "U.S. Centers for Disease Control and Prevention", "https://www.cdc.gov/high-blood-pressure/about/index.html", ("HYPERTENSION", "BP_RECORD", "HEADACHE"), CDC_LICENSE),
    Source("cdc-stroke", "About Stroke", "U.S. Centers for Disease Control and Prevention", "https://www.cdc.gov/stroke/about/index.html", ("STROKE", "ALTERED_CONSCIOUSNESS", "HEADACHE", "EMERGENCY"), CDC_LICENSE),
    Source("cdc-stroke-signs", "Signs and Symptoms of Stroke", "U.S. Centers for Disease Control and Prevention", "https://www.cdc.gov/stroke/signs-symptoms/index.html", ("STROKE", "ALTERED_CONSCIOUSNESS", "HEADACHE", "EMERGENCY"), CDC_LICENSE),
    Source("cdc-heart-disease", "About Heart Disease", "U.S. Centers for Disease Control and Prevention", "https://www.cdc.gov/heart-disease/about/index.html", ("CARDIOVASCULAR", "CHEST_PAIN", "DYSPNEA"), CDC_LICENSE),
    Source("cdc-heart-attack", "About Heart Attack", "U.S. Centers for Disease Control and Prevention", "https://www.cdc.gov/heart-disease/about/heart-attack.html", ("HEART_ATTACK", "CHEST_PAIN", "DYSPNEA", "SYNCOPE", "EMERGENCY"), CDC_LICENSE),
    Source("cdc-heart-facts", "Heart Disease Facts", "U.S. Centers for Disease Control and Prevention", "https://www.cdc.gov/heart-disease/data-research/facts-stats/index.html", ("CARDIOVASCULAR", "RISK_FACTORS", "PREVENTION"), CDC_LICENSE),
    Source("nhlbi-high-blood-pressure", "High Blood Pressure", "National Heart, Lung, and Blood Institute", "https://www.nhlbi.nih.gov/health/high-blood-pressure", ("HYPERTENSION", "BP_RECORD", "RISK_FACTORS"), NIH_LICENSE),
    Source("nhlbi-heart-attack", "Heart Attack", "National Heart, Lung, and Blood Institute", "https://www.nhlbi.nih.gov/health/heart-attack", ("HEART_ATTACK", "CHEST_PAIN", "DYSPNEA", "EMERGENCY"), NIH_LICENSE),
    Source("nhlbi-stroke", "Stroke", "National Heart, Lung, and Blood Institute", "https://www.nhlbi.nih.gov/health/stroke", ("STROKE", "ALTERED_CONSCIOUSNESS", "EMERGENCY"), NIH_LICENSE),
    Source("nhlbi-arrhythmias", "Arrhythmias", "National Heart, Lung, and Blood Institute", "https://www.nhlbi.nih.gov/health/arrhythmias", ("ARRHYTHMIA", "PALPITATIONS", "SYNCOPE", "CARDIOVASCULAR"), NIH_LICENSE),
    Source("nhlbi-heart-failure", "Heart Failure", "National Heart, Lung, and Blood Institute", "https://www.nhlbi.nih.gov/health/heart-failure", ("HEART_FAILURE", "DYSPNEA", "FATIGUE", "CARDIOVASCULAR"), NIH_LICENSE),
    Source("nhlbi-atherosclerosis", "Atherosclerosis", "National Heart, Lung, and Blood Institute", "https://www.nhlbi.nih.gov/health/atherosclerosis", ("ATHEROSCLEROSIS", "CARDIOVASCULAR", "RISK_FACTORS", "PREVENTION"), NIH_LICENSE),
    Source("nhs-chest-pain", "Chest pain", "National Health Service", "https://www.nhs.uk/symptoms/chest-pain/", ("CHEST_PAIN", "HEART_ATTACK", "EMERGENCY"), NHS_LICENSE),
    Source("nhs-shortness-of-breath", "Shortness of breath", "National Health Service", "https://www.nhs.uk/symptoms/shortness-of-breath/", ("DYSPNEA", "HEART_FAILURE", "EMERGENCY"), NHS_LICENSE),
    Source("nhs-fainting", "Fainting", "National Health Service", "https://www.nhs.uk/conditions/fainting/", ("SYNCOPE", "ALTERED_CONSCIOUSNESS", "EMERGENCY"), NHS_LICENSE),
    Source("nhs-headaches", "Headaches", "National Health Service", "https://www.nhs.uk/conditions/headaches/", ("HEADACHE", "STROKE", "EMERGENCY"), NHS_LICENSE),
    Source("nhs-high-blood-pressure", "High blood pressure (hypertension)", "National Health Service", "https://www.nhs.uk/conditions/high-blood-pressure-hypertension/", ("HYPERTENSION", "BP_RECORD", "HEADACHE"), NHS_LICENSE),
    Source("nhs-stroke", "Symptoms of a stroke", "National Health Service", "https://www.nhs.uk/conditions/stroke/symptoms/", ("STROKE", "ALTERED_CONSCIOUSNESS", "EMERGENCY"), NHS_LICENSE),
    Source("nhs-heart-attack", "Heart attack", "National Health Service", "https://www.nhs.uk/conditions/heart-attack/", ("HEART_ATTACK", "CHEST_PAIN", "DYSPNEA", "EMERGENCY"), NHS_LICENSE),
)
ALLOWED_HOSTS = frozenset(urlparse(source.url).netloc for source in SOURCES)


@dataclass
class FetchResult:
    body: str
    final_url: str
    status: int
    headers: Dict[str, str]
    method: str


def utc_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def normalize_text(value: str) -> str:
    return SPACE.sub(" ", value.replace("\u00a0", " ")).strip()


def _curl_fetch(url: str, timeout: int) -> FetchResult:
    with tempfile.TemporaryDirectory(prefix="medsim-crawl-") as directory:
        body_path = Path(directory) / "body.bin"
        header_path = Path(directory) / "headers.txt"
        command = [
            "curl.exe", "--silent", "--show-error", "--location", "--compressed",
            "--retry", "2", "--retry-delay", "2", "--max-time", str(timeout),
            "--user-agent", USER_AGENT, "--output", str(body_path), "--dump-header", str(header_path),
            "--write-out", "%{http_code}\n%{url_effective}", url,
        ]
        completed = subprocess.run(command, capture_output=True, text=True, timeout=timeout + 15, check=False)
        if completed.returncode != 0:
            raise RuntimeError(f"curl exit {completed.returncode}: {completed.stderr.strip()}")
        lines = completed.stdout.splitlines()
        status = int(lines[-2])
        final_url = lines[-1]
        raw_headers = header_path.read_text(encoding="iso-8859-1", errors="replace")
        headers: Dict[str, str] = {}
        for line in raw_headers.splitlines():
            if ":" in line:
                key, value = line.split(":", 1)
                headers[key.lower().strip()] = value.strip()
        encoding = "utf-8"
        content_type = headers.get("content-type", "")
        match = re.search(r"charset=([^; ]+)", content_type, re.I)
        if match:
            encoding = match.group(1).strip('"')
        body = body_path.read_bytes().decode(encoding, errors="replace")
        return FetchResult(body, final_url, status, headers, "curl-fallback")


def fetch_url(session: requests.Session, url: str, timeout: int = 60) -> FetchResult:
    errors: List[str] = []
    for attempt in range(3):
        try:
            response = session.get(url, timeout=(15, timeout), allow_redirects=True)
            return FetchResult(response.text, response.url, response.status_code,
                               {k.lower(): v for k, v in response.headers.items()}, "requests")
        except requests.RequestException as exc:
            errors.append(f"attempt {attempt + 1}: {type(exc).__name__}: {exc}")
            if attempt < 2:
                time.sleep(1 + attempt)
    try:
        return _curl_fetch(url, timeout)
    except Exception as exc:
        errors.append(f"curl: {type(exc).__name__}: {exc}")
        raise RuntimeError("; ".join(errors)) from exc


def robots_allowed(session: requests.Session, source_url: str, cache: Dict[str, Tuple[bool, str]]) -> Tuple[bool, str]:
    parsed = urlparse(source_url)
    origin = f"{parsed.scheme}://{parsed.netloc}"
    if origin in cache:
        allowed, detail = cache[origin]
        if not allowed:
            return cache[origin]
        parser = RobotFileParser()
        parser.parse(detail.splitlines())
        return parser.can_fetch(USER_AGENT, source_url), detail
    robots_url = origin + "/robots.txt"
    result = fetch_url(session, robots_url, timeout=30)
    if result.status == 404:
        cache[origin] = (True, "")
        return True, ""
    if result.status != 200:
        raise RuntimeError(f"robots.txt returned HTTP {result.status}")
    parser = RobotFileParser()
    parser.set_url(robots_url)
    parser.parse(result.body.splitlines())
    cache[origin] = (True, result.body)
    return parser.can_fetch(USER_AGENT, source_url), result.body


def extract_sections(html: str, fallback_title: str) -> Tuple[str, List[Dict[str, object]]]:
    soup = BeautifulSoup(html, "lxml")
    for selector in ("script", "style", "noscript", "svg", "nav", "footer", "header", "form", "aside", ".cookie", "#cookie-banner", "[aria-label='Breadcrumb']"):
        for node in soup.select(selector):
            node.decompose()
    root = soup.select_one("main") or soup.select_one("article") or soup.select_one("[role='main']") or soup.body
    if root is None:
        raise ValueError("page has no body/main/article content")
    heading = root.find("h1") or soup.find("h1")
    title = normalize_text(heading.get_text(" ", strip=True)) if heading else fallback_title
    sections: List[Dict[str, object]] = []
    current_heading = "Overview"
    paragraphs: List[str] = []
    seen = set()

    def flush() -> None:
        nonlocal paragraphs
        if paragraphs:
            sections.append({"heading": current_heading[:200], "paragraphs": paragraphs})
            paragraphs = []

    for node in root.find_all(["h2", "h3", "p", "li"]):
        if not isinstance(node, Tag):
            continue
        text = normalize_text(node.get_text(" ", strip=True))
        if node.name in ("h2", "h3"):
            if len(text) >= 3:
                flush()
                current_heading = text
            continue
        lowered = text.lower()
        if len(text) < 25 or len(text) > 4000 or text in seen:
            continue
        if any(marker in lowered for marker in ("accept cookies", "cookie settings", "skip to main", "page last reviewed", "sign up for email", "back to top")):
            continue
        seen.add(text)
        paragraphs.append(text)
    flush()
    if not sections or sum(len(p) for s in sections for p in s["paragraphs"]) < 800:
        raise ValueError("extracted main content is too short")
    return title, sections


def build_chunks(source_id: str, topics: Sequence[str], sections: Sequence[Dict[str, object]], target_chars: int = 1200,
                 version_tag: str = "") -> List[Dict[str, str]]:
    chunks: List[Dict[str, str]] = []
    for section in sections:
        heading = str(section["heading"])
        batch: List[str] = []
        length = 0
        for paragraph in section["paragraphs"]:
            paragraph = str(paragraph)
            if batch and length + len(paragraph) + 2 > target_chars:
                chunks.append({"section": heading, "topics": " ".join(topics), "content": "\n\n".join(batch)})
                batch = batch[-1:]  # one-paragraph overlap keeps local context
                length = sum(len(item) + 2 for item in batch)
            batch.append(paragraph)
            length += len(paragraph) + 2
        if batch:
            chunks.append({"section": heading, "topics": " ".join(topics), "content": "\n\n".join(batch)})
    for index, chunk in enumerate(chunks, start=1):
        version_part = f"-{version_tag}" if version_tag else ""
        chunk["chunkId"] = f"chunk-{source_id}{version_part}-{index:03d}"
    return chunks


def canonical_markdown(source: Source, title: str, final_url: str, sections: Sequence[Dict[str, object]]) -> str:
    lines = [f"# {title}", "", f"Publisher: {source.publisher}", f"Source: {final_url}", "", source.license_note, ""]
    for section in sections:
        lines.extend((f"## {section['heading']}", ""))
        for paragraph in section["paragraphs"]:
            lines.extend((str(paragraph), ""))
    return "\n".join(lines).strip() + "\n"


def crawl(sources: Iterable[Source], delay_seconds: float = 0.8) -> Tuple[Dict[str, object], Dict[str, object]]:
    source_list = tuple(sources)
    session = requests.Session()
    session.headers.update({"User-Agent": USER_AGENT, "Accept": "text/html,application/xhtml+xml"})
    robots_cache: Dict[str, Tuple[bool, str]] = {}
    documents: List[Dict[str, object]] = []
    failures: List[Dict[str, object]] = []
    started = utc_now()
    last_domain = ""
    for source in source_list:
        try:
            domain = urlparse(source.url).netloc
            if domain == last_domain and delay_seconds:
                time.sleep(delay_seconds)
            allowed, _ = robots_allowed(session, source.url, robots_cache)
            if not allowed:
                raise PermissionError("robots.txt disallows this URL for the corpus bot")
            fetched = fetch_url(session, source.url)
            if fetched.status != 200:
                raise RuntimeError(f"source returned HTTP {fetched.status}")
            if urlparse(fetched.final_url).netloc not in ALLOWED_HOSTS:
                raise PermissionError(f"redirect left the official host allowlist: {fetched.final_url}")
            if "html" not in fetched.headers.get("content-type", "text/html").lower():
                raise ValueError(f"unexpected content type: {fetched.headers.get('content-type', '')}")
            title, sections = extract_sections(fetched.body, source.title)
            markdown = canonical_markdown(source, title, fetched.final_url, sections)
            digest = hashlib.sha256(markdown.encode("utf-8")).hexdigest()
            chunks = build_chunks(source.source_id, source.topics, sections, version_tag=digest[:10])
            documents.append({
                "guidelineId": source.source_id,
                "title": title,
                "publisher": source.publisher,
                "sourceUrl": source.url,
                "finalUrl": fetched.final_url,
                "licenseNote": source.license_note,
                "language": source.language,
                "versionId": f"{source.source_id}-{digest[:12]}",
                "versionLabel": f"web-snapshot-{digest[:12]}",
                "fetchedAt": utc_now(),
                "contentSha256": digest,
                "retrievalMethod": fetched.method,
                "sourceStatus": "FETCHED",
                "httpStatus": fetched.status,
                "etag": fetched.headers.get("etag", ""),
                "lastModified": fetched.headers.get("last-modified", ""),
                "topics": list(source.topics),
                "markdown": markdown,
                "chunks": chunks,
            })
            last_domain = domain
            print(f"OK   {source.source_id}: {len(chunks)} chunks, {len(markdown)} chars")
        except Exception as exc:
            failure = {"sourceId": source.source_id, "sourceUrl": source.url, "error": f"{type(exc).__name__}: {exc}"}
            failures.append(failure)
            print(f"FAIL {source.source_id}: {failure['error']}")
    completed = utc_now()
    corpus = {
        "schemaVersion": 1,
        "generatedAt": completed,
        "generator": "data-pipeline/crawl_official_medical_sources.py",
        "documentCount": len(documents),
        "chunkCount": sum(len(document["chunks"]) for document in documents),
        "documents": documents,
    }
    report = {
        "schemaVersion": 1,
        "startedAt": started,
        "completedAt": completed,
        "allowlistedSourceCount": len(source_list),
        "successfulSourceCount": len(documents),
        "failedSourceCount": len(failures),
        "failures": failures,
    }
    return corpus, report


def write_json(path: Path, value: Dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--corpus", type=Path, default=DEFAULT_CORPUS)
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    parser.add_argument("--delay", type=float, default=0.8, help="delay between requests to the same domain")
    parser.add_argument("--minimum-success", type=int, default=15)
    args = parser.parse_args()
    corpus, report = crawl(SOURCES, args.delay)
    write_json(args.corpus, corpus)
    write_json(args.report, report)
    print(json.dumps({"corpus": str(args.corpus), "report": str(args.report), **report}, ensure_ascii=False, indent=2))
    return 0 if report["successfulSourceCount"] >= args.minimum_success else 1


if __name__ == "__main__":
    raise SystemExit(main())
