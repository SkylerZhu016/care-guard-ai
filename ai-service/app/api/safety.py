from fastapi import APIRouter, HTTPException
from app.core.models import SafetyCheckInput, SafetyCheckOutput
from app.core.engine import engine
import uuid

router = APIRouter()

@router.post("/check", response_model=dict)
async def safety_check(input_data: SafetyCheckInput):
    """Check text content for safety violations."""
    try:
        result = engine.safety_check_text(input_data)
        return {
            "code": 200,
            "message": "安全审核完成",
            "data": result.model_dump(),
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/agent-review", response_model=dict)
async def agent_review(input_data: SafetyCheckInput):
    """Multi-agent safety review simulation."""
    run_id = str(uuid.uuid4())

    # Agent 1: Content safety
    safety_result = engine.safety_check_text(input_data)

    # Agent 2: Medical accuracy check (simulated)
    medical_check = {
        "has_hallucination": False,
        "confidence": 0.92,
        "review_notes": "内容与基层诊疗指南一致",
    }

    # Agent 3: Privacy compliance (simulated)
    privacy_check = {
        "contains_phi": False,
        "data_category": "synthetic",
        "compliance": "compliant",
    }

    return {
        "code": 200,
        "message": "多Agent审核完成",
        "data": {
            "run_id": run_id,
            "overall_safe": safety_result.is_safe,
            "agent_reports": {
                "safety_agent": safety_result.model_dump(),
                "medical_accuracy_agent": medical_check,
                "privacy_agent": privacy_check,
            },
        },
    }
