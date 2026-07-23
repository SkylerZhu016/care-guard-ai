# 守望基层医疗：预问诊与随访

这是一个通用预问诊与随访案例。患者可记录常见不适、维护轻量健康资料、保存和恢复草稿，并在提交后查看人工审核与随访状态。系统只整理信息，不提供诊断或处方；自动结果必须人工审核，敏感字段在写库前脱敏。

## 业务边界

- 症状目录由 `GET /api/v2/intake-catalog` 提供，当前目录版本为 `intake-catalog-2026.07.2`，覆盖 34 项分类不适。
- 胸痛、呼吸困难、晕厥、意识异常具备有限的确定性规则；其他目录症状为 `RECORD_ONLY`，“其他不适”为 `CUSTOM`。
- 新问诊不采集数字严重度，只保存开始时间、变化过程、当前状态、活动影响和症状特异答案。
- 未支持症状不会得到 `ROUTINE`：仅包含此类症状时，规则紧急度为空并进入人工复核。
- 患者填写主诉后按 Enter 即自动调用 AI；模型只能从服务端完整症状目录选择标签，返回内容经 JSON 提取、结构校验和白名单过滤后以 `AI_EXTRACTED` 分源写入，患者可直接删除或手动补充。原始主诉永久保留，AI 失败时可改为手选且不影响草稿保存。
- 医务端 AI/RAG 按结构化摘要、关键发现、异常信号、追问、排查方向、补充信息、风险、不确定性与引用分层展示，不能把知识检索结果升级为确定性规则。
- 历史 `INTAKE_V1` 记录只读保留；新写入统一为 `INTAKE_V2`。

## 测试账号

| 账号 | 角色 | 功能 |
| --- | --- | --- |
| `patient` | 患者 | 预问诊、记录、随访任务、健康资料 |
| `clinician` | 医务人员 | 人工审核、证据核对、随访决策 |
| `followup` | 随访人员 | 执行已激活任务 |
| `admin` | 管理员 | 安全告警、知识来源与审计 |

测试密码统一为 `Demo123!`。测试夹具均不含真实身份信息。

## 本地运行

```powershell
wsl -d HermesUbuntu -- bash -lc "cd /mnt/d/工程实训/final && docker compose up -d --build"
```

打开 `http://localhost:18088`。项目使用 PostgreSQL/pgvector，不使用 H2。

## 指定工具链

- Python：`D:\Anaconda\envs\ML3.9\python.exe`
- Java 21：`H:\Java\jdk-21`
- Maven 本地仓库：`H:\Maven\repository`
- pnpm 共享仓库：`H:\pnpm-store\v11`
- Docker：WSL2 发行版 `HermesUbuntu`

```powershell
pnpm --dir frontend exec vitest run
pnpm --dir frontend build
$env:JAVA_HOME='H:\Java\jdk-21'
$env:MAVEN_OPTS='-Dmaven.repo.local=H:\Maven\repository'
& 'H:\Maven\apache-maven-3.9.9\bin\mvn.cmd' -f backend\pom.xml test
& 'D:\Anaconda\envs\ML3.9\python.exe' -m pytest ai-service\tests -q
```

真实大模型使用 OpenAI 兼容接口，通过运行环境设置 `AI_PROVIDER=openai-compatible`、`AI_MODEL`、`AI_BASE_URL`、`AI_API_KEY`，以及可选的 `AI_MAX_OUTPUT_TOKENS` 和 `AI_REQUEST_TIMEOUT_SECONDS`。密钥不得写入仓库；未配置或调用失败时系统保留原始主诉并转人工复核。默认 `fake` Provider 仅用于离线开发和确定性测试。

详细契约见 [OpenAPI](docs/api/openapi.yaml)、[SRS](docs/requirements/SRS.md)、[开发指导书](docs/PROJECT_DEVELOPMENT_GUIDE.md) 和 [RAG 实现说明](docs/ai/RAG_IMPLEMENTATION.md)。`期末任务.docx` 是原始任务书，禁止修改、转换或重导出。
