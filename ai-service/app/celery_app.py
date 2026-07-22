from celery import Celery
from .config import settings


celery_app = Celery("medsim", broker=settings.redis_url, backend=settings.redis_url)
celery_app.conf.update(task_serializer="json", result_serializer="json", accept_content=["json"], task_track_started=True)


@celery_app.task(name="medsim.run_analysis", autoretry_for=(), max_retries=0)
def run_analysis(job_id: str, request_data: dict) -> dict:
    from .jobs import execute_job
    return execute_job(job_id, request_data)

