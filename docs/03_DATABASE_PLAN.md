# 03 数据库规划

> DDL 唯一来源：`backend/src/main/resources/db/migration/V1__init.sql`（Flyway）。
> 本文档说明设计意图；变更流程：先改迁移脚本（新版本号）再同步本文档。

## 1. 表清单与职责（34 表）

### 认证权限
| 表 | 职责 | 关键约束/索引 |
|---|---|---|
| users | 账号 | username UK；failed_attempts+locked_until 锁定 |
| roles / permissions | 角色/权限 | code UK |
| user_roles / role_permissions | 关联 | 联合 PK |
| refresh_tokens | 刷新令牌 | token_hash UK；revoked 轮换 |

### 患者档案
| simulated_patients | 模拟档案 | patient_no UK；owner_user_id 本人绑定；chronic_tags jsonb |
| patient_histories / allergy_records / medication_records | 既往/过敏/用药 | patient_id FK+索引 |

### 预问诊
| visits | 问诊单 | visit_no UK；status 状态机；form_data jsonb；version 乐观锁 |
| visit_status_logs | 流转日志 | 每次流转必写（from/to/操作人/理由） |
| symptoms | 归一化症状行 | visit FK |
| symptom_extractions | AI 结构化产物 | extracted_json+missing_fields+置信度+版本 |
| triage_results | 分诊结论 | visit UK（一诊一结论）；风险点/摘要/安全状态/四版本 |

### 规则引擎
| rule_definitions | 规则主表 | code UK；condition_expr jsonb；current_version |
| rule_versions | 规则版本 | (rule_id,version) UK，改动即新版本 |
| rule_hits | 命中记录 | visit FK；evidence 快照 |

### 知识库
| knowledge_documents | 文档元数据 | status(PENDING/ENABLED/DISABLED/INGEST_FAILED)；version |
| knowledge_chunks | 分块+向量 | (document_id,chunk_no,version) UK；embedding vector(256) |

### AI 治理
| model_configs | 模型配置 | 不存 Key（仅环境变量）；is_default |
| prompt_templates / prompt_versions | Prompt 版本 | (template_id,version) UK |
| agent_runs | 流水线运行 | status/current_step/四版本/token |
| agent_run_steps | 步骤明细 | step 枚举；输入输出快照 |
| citations | 引用 | 文档/章节/页码/片段/得分/知识库版本 |
| safety_alerts | 安全告警 | type 枚举；OPEN/ACK/CLOSED |

### 审核与随访
| review_records | 审核记录 | ai_snapshot 快照支持版本对比 |
| followup_plans | 随访计划 | 状态机；items jsonb；version |
| followup_tasks | 随访任务 | (status,due_date) 复合索引；version 乐观锁 |
| followup_records | 随访记录 | symptom_change 枚举驱动趋势图 |

### 系统
| uploaded_files | 文件 | sha256 去重；object_key→MinIO |
| audit_logs | 审计 | 时间+用户索引；只增不改 |
| system_configs | 配置 KV | knowledge_version/rule_version 计数器 |

## 2. ER 关系摘要
users 1─n simulated_patients 1─n visits 1─n {symptoms, visit_status_logs, symptom_extractions, rule_hits, citations, agent_runs 1─n agent_run_steps}，visits 1─1 triage_results、1─n review_records；knowledge_documents 1─n knowledge_chunks；followup_plans 1─n followup_tasks 1─n followup_records。

## 3. 设计要点
- **状态字段**全部 VARCHAR 枚举（CHECK 由应用层状态机保证），流转必写日志表。
- **并发**：visits/followup_tasks 乐观锁 version；状态流转在事务内 `SELECT … FOR UPDATE`。
- **事务边界**：提交问诊（visit+run+日志+审计）一个事务；审核（review+visit状态+日志）一个事务；任务完成（task+record）一个事务。
- **软删除**：knowledge_documents/rule_definitions 用 deleted；users 用 enabled。
- **敏感字段**：phone/id_card 仅详情接口对授权角色返回明文，列表接口服务端掩码；审计/日志只存掩码摘要。
- **向量**：embedding 由 ai-service 统一写入；版本升级经 `EMBEDDING_DIM` 变更 + 重建索引（新增 migration）。
- **迁移**：Flyway；V1 建表、V2 种子；演示重置脚本 `data-pipeline/reset_demo.*` 重建 V2 数据集。
