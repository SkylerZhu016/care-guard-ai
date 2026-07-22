# 基层医疗安全型预问诊与随访平台

> 教学用辅助系统 · 不提供真实医疗诊断 · AI 内容仅供教学参考，不能替代医生

课程实训期末项目（选题二）。Vue 3 + Spring Boot + FastAPI 多 Agent + RAG（pgvector）+ 确定性规则引擎 + 全链路审计，Docker Compose 一键启动。

## 快速开始
```bash
cd deploy
cp .env.example .env
docker compose up -d --build
# 前端 http://localhost  后端 http://localhost:8080/swagger-ui.html
```
默认 `MOCK_LLM=true`（离线演示，无需任何 API Key）；配置 `LLM_API_BASE/LLM_API_KEY` 即切换真实模型。

## 演示账号
| 账号 | 密码 | 角色 |
|---|---|---|
| admin | Admin@123456 | 管理员 |
| doctor1 | Doctor@123456 | 医务人员 |
| follow1 | Follow@123456 | 随访人员 |
| patient1 | Patient@123456 | 患者 |

## 目录结构
```
├── frontend/       Vue 3 + TS + Vite + Pinia + Element Plus + ECharts（30 页面）
├── backend/        Spring Boot 3：认证/RBAC/状态机/规则引擎/审核/随访/审计/SSE
├── ai-service/     FastAPI：五段 Agent 流水线 + RAG + 安全审查 + Mock LLM 降级
├── data-pipeline/  种子与演示数据重置脚本
├── deploy/         docker-compose.yml / nginx / .env.example
├── tests/          端到端与 AI 评测（合成测试集）
└── docs/           规划文档（01-10）+ submission/ 提交材料（需求/设计/测试/部署/手册等）
```

## 核心特性
- **安全四层防线**：规则引擎红旗兜底 → 安全审查 Agent → 医务人工审核 → 免责表达约束；AI 永不绕过人工直接对患者输出。
- **全链路可追溯**：每次 AI 运行留模型/Prompt/知识库/规则四版本 + 逐步快照；引用可定位到文档/章节/页码/得分。
- **业务闭环**：填报→脱敏→结构化→规则预筛→RAG→多 Agent 复核→人工审核→随访计划→任务→记录归档。
- **可离线演示**：Mock LLM + HASH 嵌入，零外网依赖；演示数据一键重置。

## 文档导航
- 总指导书：`docs/PROJECT_IMPLEMENTATION_GUIDE.md`
- 提交材料：`docs/submission/`（需求规格、系统设计、数据库、接口、测试报告、AI评测、安全合规、部署、用户手册、答辩演示）

## 测试
```bash
cd ai-service && pytest -q        # AI 服务
cd backend && ./mvnw test         # 后端
cd frontend && npm run test       # 前端
```

## 合规声明
本系统仅用于教学演示：全部病例为合成数据；知识库为公开来源资料；不构成任何医疗建议。
