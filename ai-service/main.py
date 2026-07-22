import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.api import consult, safety, rag

app = FastAPI(
    title="AI 预问诊服务",
    description="教学用基层医疗安全型预问诊 AI 服务",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(consult.router, prefix="/ai/consult", tags=["预问诊"])
app.include_router(safety.router, prefix="/ai/safety", tags=["安全审核"])
app.include_router(rag.router, prefix="/ai/rag", tags=["RAG 检索"])

@app.get("/ai/health")
def health():
    return {"status": "ok", "service": "medical-ai-service"}

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
