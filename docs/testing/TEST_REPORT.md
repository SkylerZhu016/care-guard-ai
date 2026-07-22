# 测试报告

> 验证日期：2026-07-22  
> 验证对象：基层医疗安全型预问诊与随访平台 1.0.0  
> 结论：通过。以下结论只表示软件工程行为符合本项目要求，不代表临床有效性。

## 1. 验证环境

| 项目 | 实际环境 |
|---|---|
| 宿主系统 | Windows + WSL2 `HermesUbuntu` |
| 容器运行时 | 复用 WSL2 内 Docker，不依赖 Docker Desktop |
| Java | 宿主测试 `H:\Java\jdk-21`，Java 21.0.11；容器 Java 21.0.7 |
| Maven 仓库 | `H:\Maven\repository` |
| Python | `D:\Anaconda\envs\ML3.9\python.exe`，Python 3.9.25 |
| pnpm | 11.9.0，共享仓库 `H:\pnpm-store\v11` |
| 数据库 | 宿主集成测试与 Compose 均为 `pgvector/pgvector:0.8.0-pg16`（PostgreSQL 16.10） |
| 项目入口 | `http://localhost:18088` |

Windows 命令使用 `D:\工程实训\final`，WSL/Docker 命令使用 `/mnt/d/工程实训/final`。未混用 `\` 与 `/` 路径语义。

## 2. 自动化测试结果

| 测试层 | 命令 | 结果 |
|---|---|---:|
| 网络采集器 | `D:\Anaconda\envs\ML3.9\python.exe -m pytest .\data-pipeline\tests` | 3/3 通过 |
| Spring Boot | `mvn -f .\backend\pom.xml '-Dmaven.repo.local=H:\Maven\repository' test` | 10/10 通过 |
| AI 服务 | `D:\Anaconda\envs\ML3.9\python.exe -m pytest .\ai-service` | 9/9 通过，覆盖率 85.53% |
| Vue 单元测试 | `pnpm --dir frontend test -- --run` | 7/7 通过 |
| Vue 生产构建 | `pnpm --dir frontend build` | 通过，无大包警告 |
| API 业务闭环 | `.\scripts\api-smoke.ps1` | PASS |
| 并发性能冒烟 | `.\scripts\performance-smoke.ps1` | PASS |

后端集成测试连接真实 PostgreSQL，并由 Flyway 验证迁移；没有使用 H2。AI 测试严格从指定的 ML3.9 虚拟环境执行。

## 3. API 端到端闭环

最终冒烟结果：

- 红旗规则紧急度：`EMERGENCY`
- AI 运行状态：`SUCCEEDED`
- 有效引用数：4；脚本同时断言 `chunkId`、`quote` 与 `sourceUrl` 非空
- Agent 轨迹：5 个可观察角色完整写入
- 医务人员审核：完成
- 随访计划：创建并激活，生成 4 个任务
- 随访任务：从 `IN_PROGRESS` 更新为 `COMPLETED`
- 患者、随访人员和管理员权限视图：均可访问相应数据
- 审计事件与 Agent 运行记录：均非空

脚本使用四个角色的 JWT 完成完整业务链路，并验证 RBAC、红旗规则、AI 引用、人工复核、计划激活、任务执行和审计查询。

## 4. 性能冒烟

目标为已认证的 `GET /api/v1/me`：

| 指标 | 结果 |
|---|---:|
| 请求数 | 50 |
| 并发数 | 10 |
| 失败数 | 0 |
| P50 | 19 ms |
| P95 | 34 ms |
| 最大值 | 37 ms |
| P95 阈值 | 500 ms |

该结果是本机轻量冒烟数据，不等同于容量规划或生产压测结论。

## 5. AI 工程评测

60 个合成案例（普通 30、红旗 15、对抗 15）的可复现基线：

| 指标 | 结果 |
|---|---:|
| 结构化 JSON 成功率 | 100% |
| 有效引用 ID 比例 | 100% |
| 红旗工程集召回 | 100% |
| 对抗输入阻断召回 | 100% |
| 正常输入误阻断率 | 0% |
| AI 降低规则紧急度次数 | 0 |

详细结果见 [AI 工程评测报告](../ai/AI_EVALUATION_REPORT.md)。

另有 OpenAI-compatible 适配器协议测试，确认摘要生成与独立安全审查使用两次不同请求，并验证超时稳定映射为 `AI_TIMEOUT`。该测试不读取或发送真实密钥，因此只证明适配器契约，不代表真实模型现场效果。

## 6. 浏览器与视觉验收

使用真实部署入口完成四个演示账号的登录和页面检查：

- `patient`：动态症状由 2 项增加到 3 项，已有草稿可重新载入编辑；规则/AI 进度和患者随访任务正常渲染。
- `clinician`：`ACCEPT`、`MODIFY`、`REJECT` 三种审核决定可选，审核与计划激活分步呈现。
- `followup`：每页 6 项；待执行任务只显示“开始”，进行中任务才显示带独立摘要的“完成”。
- `admin`：可查看 21 个官方网络来源、1 个受控安全基线及 1 个停用历史来源，并展示 chunk 数、抓取状态、采集方式、时间、SHA-256、MinIO key 和许可说明。
- 浏览器控制台：无 warning/error。
- 633px 视口：无横向页面溢出；指标为 2×2，管理图表与告警折叠为单栏。

前端已按需注册 Element Plus 组件。最终基础 JS 为 337.55 KB、基础 CSS 为 79.31 KB；管理员懒加载包为 436.05 KB。

## 7. 容器和运行日志

PostgreSQL、Redis、AI API、Celery Worker、Spring Boot、MinIO 和 Nginx 共 7 个常驻服务均达到 healthy。MinIO bucket 与受控知识种子改由后端启动时幂等初始化；旧 `minio-init` 孤儿容器已由 `--remove-orphans` 移除，不再影响 `compose --wait`。

知识链路部署证据：`vector` 扩展存在；21 个官方网络来源和 1 个受控安全基线版本处于激活状态，共 153 个分块（150 + 3）；旧教学种子保留审计记录但已停用；`idx_guideline_chunks_embedding` 为 HNSW cosine 索引；MinIO 卷中存在 24 个当前与历史 Markdown 对象。AI 容器对胸痛/呼吸困难、高血压、晕厥和卒中四组查询均返回相应 WHO、CDC、NHLBI 或 NHS 来源；完整 API 冒烟的最新引用包含 CDC 官方页面。

修复后日志确认后端到 Uvicorn 使用 HTTP/1.1，最终业务请求未出现新的 422、h2c upgrade 或 `Invalid HTTP request received`。管理员页面中的 3 条开放告警是修复前故障验证留下的审计记录，保留它们用于展示可追溯性；本轮成功运行没有新增同类告警。

## 8. 非阻断提示

- Spring 测试提示 `MockBean` 将来会移除，以及 Mockito 动态加载 agent 的未来兼容性警告；当前 Java 21 测试全部通过。
- AI 测试有一条 LangGraph 序列化器默认值即将变化的提示；当前结果和覆盖率不受影响。
- Fake Provider 与 `fake-embedding-v1` 用于可复现工程验证，不代表真实模型或医学语义质量。

