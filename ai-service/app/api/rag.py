from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import Optional, List
import uuid

router = APIRouter()

# Simulated guideline database
GUIDELINES = [
    {"id": 1, "title": "高血压基层诊疗指南", "category": "心血管", "content": "诊断标准：非同日3次测量，收缩压≥140mmHg和/或舒张压≥90mmHg。危险分层：根据血压水平、心血管危险因素、靶器官损害进行分层。治疗目标：一般患者<140/90mmHg，合并糖尿病或肾病<130/80mmHg。药物选择：ACEI/ARB、CCB、利尿剂、β受体阻滞剂。"},
    {"id": 2, "title": "2型糖尿病基层诊疗指南", "category": "内分泌", "content": "诊断标准：空腹血糖≥7.0mmol/L或OGTT 2h血糖≥11.1mmol/L。治疗路径：生活方式干预→单药治疗(二甲双胍)→联合治疗。血糖控制目标：HbA1c<7.0%（个体化调整）。并发症筛查：每年筛查眼底、肾功能、足部。"},
    {"id": 3, "title": "急性上呼吸道感染基层诊疗指南", "category": "呼吸", "content": "大多数为病毒感染，无需抗生素治疗。对症治疗为主：退热、止咳、缓解鼻塞。抗生素使用指征：明确细菌感染证据。红/黄旗症状：持续高热>3天、呼吸困难、胸痛需转诊。"},
    {"id": 4, "title": "冠心病基层诊疗指南", "category": "心血管", "content": "稳定型心绞痛：硝酸酯类、β受体阻滞剂、抗血小板治疗。不稳定型心绞痛/心肌梗死：紧急转诊。二级预防：阿司匹林、他汀类药物、ACEI/ARB。生活方式：低盐低脂饮食、戒烟、适度运动。"},
    {"id": 5, "title": "脑卒中基层诊疗指南", "category": "神经内科", "content": "FAST快速评估：面部下垂、手臂无力、言语障碍、及时就医。缺血性卒中：4.5小时内溶栓治疗。二级预防：抗血小板、他汀、控制血压血糖。康复治疗：早期康复训练。"},
    {"id": 6, "title": "发热待查基层诊疗流程", "category": "感染", "content": "急性发热(<7天)：常见感染性疾病为主。长期发热(>7天)：需考虑感染、自身免疫、肿瘤。红旗征：高热伴意识改变、颈强直、皮疹、呼吸困难。经验性抗生素使用需谨慎。"},
]

class RAGQuery(BaseModel):
    query: str
    category: Optional[str] = None
    top_k: int = 5

@router.post("/search", response_model=dict)
async def rag_search(query: RAGQuery):
    """Simulated RAG search - retrieves relevant guidelines."""
    try:
        run_id = str(uuid.uuid4())
        results = []

        for guide in GUIDELINES:
            if query.category and guide["category"] != query.category:
                continue

            # Simple keyword matching simulation
            query_keywords = query.query.lower().split()
            content_lower = guide["content"].lower()
            title_lower = guide["title"].lower()

            score = 0
            for kw in query_keywords:
                if kw in title_lower:
                    score += 5
                if kw in content_lower:
                    score += 2

            if score > 0:
                results.append({
                    "id": guide["id"],
                    "title": guide["title"],
                    "category": guide["category"],
                    "content": guide["content"],
                    "relevance_score": score,
                })

        # Sort by relevance
        results.sort(key=lambda x: x["relevance_score"], reverse=True)
        results = results[:query.top_k]

        return {
            "code": 200,
            "message": "检索完成",
            "data": {
                "run_id": run_id,
                "results": results,
                "total": len(results),
                "kb_version": "1.0.0",
            },
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/guidelines/{guideline_id}", response_model=dict)
async def get_guideline(guideline_id: int):
    """Get a specific guideline by ID."""
    for guide in GUIDELINES:
        if guide["id"] == guideline_id:
            return {"code": 200, "message": "success", "data": guide}
    raise HTTPException(status_code=404, detail="指南未找到")
