import json
import time
from typing import Optional
from redis import Redis

from .config import settings
from .schemas import AnalysisRequest, JobStatus


class JobStore:
    def __init__(self, client: Optional[Redis] = None):
        self.client = client or Redis.from_url(settings.redis_url, decode_responses=True)

    def save(self, status: JobStatus) -> None:
        self.client.setex(f"medsim:job:{status.jobId}", 86400, status.model_dump_json())

    def load(self, job_id: str) -> Optional[JobStatus]:
        payload = self.client.get(f"medsim:job:{job_id}")
        return JobStatus.model_validate_json(payload) if payload else None


def execute_job(job_id: str, request_data: dict) -> dict:
    from .workflow import analyze
    store = JobStore()
    request = AnalysisRequest.model_validate(request_data)
    started = time.perf_counter()
    store.save(JobStatus(jobId=job_id, runId=request.runId, status="RUNNING"))
    try:
        result = analyze(request)
        status = "BLOCKED" if result.safety.decision.value == "BLOCK" else "SUCCEEDED"
        job = JobStatus(jobId=job_id, runId=request.runId, status=status, result=result, durationMs=int((time.perf_counter() - started) * 1000))
    except Exception as exc:
        job = JobStatus(jobId=job_id, runId=request.runId, status="FAILED", errorCode=type(exc).__name__, durationMs=int((time.perf_counter() - started) * 1000))
    store.save(job)
    return json.loads(job.model_dump_json())

