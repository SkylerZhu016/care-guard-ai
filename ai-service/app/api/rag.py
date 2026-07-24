from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import Optional, List
from app.core.config import API_KEY, BASE_URL, LLM_MODEL
from openai import AsyncOpenAI
import uuid, json, re

router = APIRouter()

# DeepSeek client (same config as engine)
client = AsyncOpenAI(api_key=API_KEY, base_url=BASE_URL) if API_KEY else None

# Local guideline knowledge base
GUIDELINES = [
    {"id": 1, "title": "高血压基层诊疗指南", "category": "心血管", "content": "诊断标准：非同日3次测量，收缩压\u2265140mmHg和/或舒张压\u226590mmHg。危险分层：根据血压水平、心血管危险因素、靶器官损害进行分层。治疗目标：一般患者<140/90mmHg，合并糖尿病或肾病<130/80mmHg。药物选择：ACEI/ARB、CCB、利尿剂、\u03b2受体阻滞剂。"},
    {"id": 2, "title": "2型糖尿病基层诊疗指南", "category": "内分泌", "content": "诊断标准：空腹血糖\u22657.0mmol/L或OGTT 2h血糖\u226511.1mmol/L。治疗路径：生活方式干预\u2192单药治疗(二甲双胍)\u2192联合治疗。血糖控制目标：HbA1c<7.0%（个体化调整）。并发症筛查：每年筛查眼底、肾功能、足部。"},
    {"id": 3, "title": "急性上呼吸道感染基层诊疗指南", "category": "呼吸", "content": "大多数为病毒感染，无需抗生素治疗。对症治疗为主：退热、止咳、缓解鼻塞。抗生素使用指征：明确细菌感染证据。红/黄旗症状：持续高热>3天、呼吸困难、胸痛需转诊。"},
    {"id": 4, "title": "冠心病基层诊疗指南", "category": "心血管", "content": "稳定型心绞痛：硝酸酯类、\u03b2受体阻滞剂、抗血小板治疗。不稳定型心绞痛/心肌梗死：紧急转诊。二级预防：阿司匹林、他汀类药物、ACEI/ARB。生活方式：低盐低脂饮食、戒烟、适度运动。"},
    {"id": 5, "title": "脑卒中基层诊疗指南", "category": "神经内科", "content": "FAST快速评估：面部下垂、手臂无力、言语障碍、及时就医。缺血性卒中：4.5小时内溶栓治疗。二级预防：抗血小板、他汀、控制血压血糖。康复治疗：早期康复训练。"},
    {"id": 6, "title": "发热待查基层诊疗流程", "category": "感染", "content": "急性发热(<7天)：常见感染性疾病为主。长期发热(>7天)：需考虑感染、自身免疫、肿瘤。红旗征：高热伴意识改变、颈强直、皮疹、呼吸困难。经验性抗生素使用需谨慎。"},
]

class RAGQuery(BaseModel):
    query: str
    category: Optional[str] = None
    top_k: int = 5

@router.post("/search", response_model=dict)
async def rag_search(query: RAGQuery):
    try:
        run_id = str(uuid.uuid4())

        # Step 1: Filter by category if specified
        candidates = [g for g in GUIDELINES if not query.category or g["category"] == query.category]

        if not query.query.strip():
            # Empty query: return all (or filtered by category)
            results = candidates[:query.top_k]
            return {
                "code": 200, "message": "检索完成", "data": {
                    "run_id": run_id, "results": results, "total": len(results),
                    "ai_answer": None, "kb_version": "1.0.0",
                }
            }

        # Step 2: Use DeepSeek for semantic search + answer generation
        if client:
            ai_result = await _ai_search(query.query, candidates, query.top_k)
            return {
                "code": 200, "message": "AI检索完成", "data": {
                    "run_id": run_id, "results": ai_result["guidelines"],
                    "total": len(ai_result["guidelines"]),
                    "ai_answer": ai_result["answer"],
                    "kb_version": "2.0.0-ai",
                }
            }
        else:
            # Fallback to keyword matching
            return _keyword_search(query, candidates, run_id)

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


async def _ai_search(query: str, candidates: list, top_k: int) -> dict:
    """Use DeepSeek to find relevant guidelines and generate an answer."""
    # Build context from candidate guidelines
    context_parts = []
    for g in candidates:
        context_parts.append(f"[{g['id']}] {g['title']} ({g['category']})\n{g['content']}")
    context_str = "\n\n".join(context_parts)

    system_prompt = """你是一个医疗知识库AI助手。你的任务：
1. 根据用户的问题，从提供的指南列表中找出最相关的指南
2. 返回匹配的指南ID列表（json数组）
3. 基于指南内容生成专业的回答

请严格按照以下JSON格式返回，不要包含markdown标记：
{
  "matched_ids": [1, 3],
  "answer": "基于指南内容的专业回答..."
}"""

    user_prompt = f"""用户问题：{query}

可参考的指南库：
{context_str}"""

    try:
        response = await client.chat.completions.create(
            model=LLM_MODEL,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt}
            ],
            temperature=0.1,
            max_tokens=2048,
            response_format={"type": "json_object"}
        )

        content = response.choices[0].message.content.strip()
        if content.startswith("```"):
            content = re.sub(r"```(?:json)?\n*", "", content).strip()
            content = content.rstrip("`").strip()
        result = json.loads(content)

        matched_ids = result.get("matched_ids", [])
        answer = result.get("answer", "")

        # Build matched guidelines list
        matched = []
        for g in candidates:
            if g["id"] in matched_ids:
                matched.append(g)

        # Sort by matched order
        ordered = sorted(matched, key=lambda x: matched_ids.index(x["id"]) if x["id"] in matched_ids else 999)
        ordered = ordered[:top_k]

        return {"guidelines": ordered, "answer": answer}

    except Exception as e:
        print(f"[DeepSeek RAG Error] {e}, falling back to keyword search")
        # Fallback
        query_lower = query.lower()
        results = []
        for g in candidates:
            score = 0
            for kw in query_lower.split():
                if kw in g["title"].lower(): score += 5
                if kw in g["content"].lower(): score += 2
            if score > 0:
                results.append(g)
        results.sort(key=lambda x: sum(2 if kw in x["content"].lower() else 0 for kw in query_lower.split()) + 5 * sum(1 for kw in query_lower.split() if kw in x["title"].lower()), reverse=True)
        return {"guidelines": results[:top_k], "answer": None}


def _keyword_search(query_obj, candidates, run_id):
    """Fallback keyword search."""
    results = []
    query = query_obj.query.lower()
    for g in candidates:
        score = 0
        for kw in query.split():
            if kw in g["title"].lower(): score += 5
            if kw in g["content"].lower(): score += 2
        if score > 0:
            g_copy = dict(g)
            g_copy["relevance_score"] = score
            results.append(g_copy)
    results.sort(key=lambda x: x["relevance_score"], reverse=True)
    results = results[:query_obj.top_k]
    return {
        "code": 200, "message": "检索完成（关键词模式）", "data": {
            "run_id": run_id, "results": results, "total": len(results),
            "ai_answer": None, "kb_version": "1.0.0",
        }
    }


@router.get("/guidelines/{guideline_id}", response_model=dict)
async def get_guideline(guideline_id: int):
    for guide in GUIDELINES:
        if guide["id"] == guideline_id:
            return {"code": 200, "message": "success", "data": guide}
    raise HTTPException(status_code=404, detail="指南未找到")
