"""FastAPI 主应用"""
import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI
from app.routers import router
from app.core.db import SessionLocal
from app.services.knowledge import backfill_embeddings

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(name)s] %(levelname)s: %(message)s")
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # 启动时补算缺失 embedding
    try:
        db = SessionLocal()
        count = backfill_embeddings(db)
        if count:
            logger.info(f"启动时补算 {count} 个分块 embedding")
        db.close()
    except Exception as e:
        logger.warning(f"启动 embedding 补算跳过（数据库可能未就绪）: {e}")
    yield


app = FastAPI(title="AI Service - 预问诊平台", version="1.0.0", lifespan=lifespan)
app.include_router(router)
