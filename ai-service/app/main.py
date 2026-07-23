from uuid import uuid4
from fastapi import Depends, FastAPI, Header, HTTPException, status
from redis import Redis

from .celery_app import celery_app
from .config import settings
from .jobs import JobStore
from .schemas import AnalysisRequest, ComplaintStructureRequest, ComplaintStructureResult, JobAccepted, JobStatus


app = FastAPI(title="MedSim Controlled AI Service", version="1.0.0", docs_url="/internal/docs")


def internal_auth(x_internal_token: str = Header(default="")) -> None:
    if x_internal_token != settings.internal_token:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="INVALID_INTERNAL_TOKEN")


@app.get("/health/live")
def live() -> dict:
    return {"status": "UP", "service": "ai-api", "provider": settings.provider}


@app.get("/health/ready")
def ready() -> dict:
    try:
        Redis.from_url(settings.redis_url).ping()
        return {"status": "UP", "redis": "UP", "provider": settings.provider}
    except Exception as exc:
        raise HTTPException(status_code=503, detail="REDIS_UNAVAILABLE") from exc


@app.post("/internal/v1/analysis-jobs", response_model=JobAccepted, status_code=202, dependencies=[Depends(internal_auth)])
def create_analysis_job(request: AnalysisRequest) -> JobAccepted:
    job_id = str(uuid4())
    JobStore().save(JobStatus(jobId=job_id, runId=request.runId, status="QUEUED"))
    celery_app.send_task("medsim.run_analysis", args=[job_id, request.model_dump(mode="json")])
    return JobAccepted(jobId=job_id, runId=request.runId, status="QUEUED")


@app.get("/internal/v1/jobs/{job_id}", response_model=JobStatus, dependencies=[Depends(internal_auth)])
def get_job(job_id: str) -> JobStatus:
    job = JobStore().load(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="JOB_NOT_FOUND")
    return job


@app.post("/internal/v1/complaint-structure", response_model=ComplaintStructureResult, dependencies=[Depends(internal_auth)])
def structure_complaint(request: ComplaintStructureRequest) -> ComplaintStructureResult:
    import time
    from .providers import get_provider
    started = time.perf_counter()
    provider = get_provider()
    draft = provider.structure_complaint(request)
    allowed = {tag.code: tag for tag in request.availableTags}
    selected_codes = {tag.code for tag in request.selectedTags}
    filtered_tags = []
    seen_codes = set()
    for candidate in draft.get("extractedTags", []):
        if not isinstance(candidate, dict):
            continue
        code = str(candidate.get("code", "")).strip().upper()
        definition = allowed.get(code)
        if definition is None or code in selected_codes or code in seen_codes:
            continue
        seen_codes.add(code)
        filtered_tags.append({
            **candidate,
            "code": definition.code,
            "displayName": definition.displayName,
            "category": definition.category,
            "source": "ai_extracted",
            "confirmationStatus": "proposed",
        })
    draft["extractedTags"] = filtered_tags
    draft.update({"provider": provider.name, "model": settings.model,
                  "durationMs": int((time.perf_counter() - started) * 1000)})
    return ComplaintStructureResult.model_validate(draft)

