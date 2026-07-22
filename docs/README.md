# 基层医疗安全型预问诊与随访平台

> 教学用辅助系统，不能宣称提供真实诊断或替代医生。

## 技术栈

| 层次 | 技术 |
|------|------|
| 前端 | Vue 3 + TypeScript + Vite + Pinia + Element Plus + ECharts |
| 业务后端 | Spring Boot 3.2 + JPA + Security + WebSocket |
| AI 服务 | Python + FastAPI + LangChain |
| 关系数据库 | MySQL 8.0 |
| 缓存 | Redis |
| 部署 | Docker Compose + Nginx |
| 接口文档 | OpenAPI/Swagger |
| 数据库迁移 | Flyway |

## 快速启动

### 方式一：Docker Compose（推荐）

```bash
cd deploy
docker-compose up -d
```

### 方式二：本地开发

1. **启动 MySQL 和 Redis**
2. **启动后端**
   ```bash
   cd backend
   mvn spring-boot:run
   ```
3. **启动 AI 服务**
   ```bash
   cd ai-service
   pip install -r requirements.txt
   python main.py
   ```
4. **启动前端**
   ```bash
   cd frontend
   npm install
   npm run dev
   ```

## 默认测试账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 管理员 |
| doctor1 | doc123 | 医务人员 |
| followup1 | fol123 | 随访人员 |
| patient1 | pat123 | 模拟患者 |

## 核心业务流程

患者填报 → 信息脱敏 → 症状结构化 → 规则预筛 → 医疗 RAG → 多 Agent 风险复核 → 医生模拟审核 → 随访计划 → 定期提醒 → 结果归档

## 安全特性

- JWT 登录 + RBAC 权限控制
- 结构化规则引擎检查红旗症状
- AI 输出先经过安全 Agent 审核
- 提示词攻击拦截
- 越权诊断检测
- 隐私泄露检测
- 模型/提示词/知识库版本追踪
