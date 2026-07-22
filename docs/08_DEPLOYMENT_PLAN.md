# 08 部署规划（Docker Compose 一键启动）

## 1. 服务清单（deploy/docker-compose.yml）

| 服务 | 镜像/构建 | 端口 | 依赖 | 健康检查 |
|---|---|---|---|---|
| db | pgvector/pgvector:pg16 | 5432 | - | pg_isready |
| minio | minio/minio:latest | 9000/9001 | - | /minio/health/live |
| backend | build ../backend (多阶段 maven→jre17) | 8080 | db, minio | /actuator/health |
| ai-service | build ../ai-service (python:3.11-slim) | 8000 | db | /health |
| frontend | build ../frontend (node build→nginx) | 80 | backend | nginx 静态 |

- 网络：`mednet`（bridge）；卷：`pgdata`、`miniodata`。
- 启动：`docker compose --env-file .env up -d --build`；初始化由 backend 的 Flyway 自动完成（V1+V2）。
- ai-service 启动时回填 NULL embedding（种子 chunk 自动向量化）并确保 MinIO bucket 存在（由 backend 首次写入时创建亦可）。

## 2. 环境变量（.env.example 占位，禁止提交真实值）
```
POSTGRES_DB=medplatform
POSTGRES_USER=meduser
POSTGRES_PASSWORD=change-me
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=change-me
MINIO_BUCKET=med-files
JWT_SECRET=change-me-32bytes-min
INTERNAL_TOKEN=change-me-internal
AI_SERVICE_URL=http://ai-service:8000
MOCK_LLM=true
LLM_API_BASE=
LLM_API_KEY=
LLM_MODEL=gpt-4o-mini
EMBEDDING_DIM=256
EMBEDDING_API_BASE=
EMBEDDING_API_KEY=
CORS_ORIGINS=http://localhost
```

## 3. Nginx（frontend 容器内）
- `/` → 静态资源（history 路由 fallback index.html）；`/api/` → backend:8080；SSE 路径关闭缓冲（`proxy_buffering off; X-Accel-Buffering: no`）；gzip on；上传限制 10m。

## 4. 初始化与重置
- 首启：Flyway 建表+种子（账号/规则/Prompt/指南/演示患者）。
- 重置演示：`data-pipeline/reset_demo.sh`（docker compose exec db psql 清业务表并重放种子）或 `docker compose down -v && up -d`。

## 5. 开发 vs 演示
| 项 | 开发 | 演示 |
|---|---|---|
| 前端 | vite dev :5173 代理 /api | nginx 容器 :80 |
| 后端 | IDE 启动，连 compose 的 db/minio | 容器 |
| MOCK_LLM | true | true（答辩兜底）；真实 Key 仅演示前配置 |
| 数据 | 随意 | reset 后的标准演示集 |

## 6. 运维要点
- 日志：`docker compose logs -f <svc>`；后端 JSON 日志含 traceId。
- 备份：演示前 `docker compose exec db pg_dump -U meduser medplatform > backup.sql`。
- 端口冲突：改 compose ports 映射即可，无需改代码。
- 资源建议：≥4C8G；db shm_size 128m。
