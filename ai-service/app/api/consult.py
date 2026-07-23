from fastapi import APIRouter, HTTPException
from app.core.models import PreConsultInput, TriageOutput, SafetyCheckInput, SafetyCheckOutput
from app.core.engine import engine
import uuid
import time

router = APIRouter()


@router.post("/triage", response_model=dict)
async def triage(input_data: PreConsultInput):
    try:
        run_id = str(uuid.uuid4())
        start = time.time()

        result = await engine.triage(input_data)

        latency = int((time.time() - start) * 1000)

        return {
            "code": 200,
            "message": "\u5206\u8bca\u5b8c\u6210",
            "data": {
                "run_id": run_id,
                "result": result.model_dump(),
                "latency_ms": latency,
                "model_version": "1.0.0",
                "prompt_version": "1.0.0",
            },
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/submit", response_model=dict)
async def submit_consult(input_data: PreConsultInput):
    try:
        run_id = str(uuid.uuid4())
        start = time.time()

        safety_text = f"{input_data.chief_complaint} {' '.join(s.symptom_name for s in input_data.symptoms)}"
        safety_result = engine.safety_check_text(SafetyCheckInput(text=safety_text))

        triage_result = await engine.triage(input_data)

        if not safety_result.is_safe:
            triage_result.safety_checked = True
            triage_result.safety_report = f"\u5b89\u5168\u62e6\u622a\uff1a{safety_result.reason}"

        latency = int((time.time() - start) * 1000)

        return {
            "code": 200,
            "message": "\u9884\u95ee\u8bca\u5b8c\u6210",
            "data": {
                "run_id": run_id,
                "triage": triage_result.model_dump(),
                "safety": safety_result.model_dump(),
                "latency_ms": latency,
                "model_version": "1.0.0",
            },
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))