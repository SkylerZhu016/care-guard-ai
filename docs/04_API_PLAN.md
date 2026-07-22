# 04 接口规划（API 合同 v1）

统一约定：
- 前缀 `/api/v1`；除标注「公开」外均需 `Authorization: Bearer <accessToken>`。
- 内部接口前缀 `/api/internal`，仅校验 `X-Internal-Token`（后端⇄AI 服务）。
- 分页请求 `?page=0&size=20&sort=createdAt,desc`；响应 Spring Page：`{content:[],totalElements,totalPages,number,size}`。
- 统一错误体 `{"code":int,"message":string,"traceId":string}`。错误码：40001 参数错误/40002 校验失败/40101 未登录或过期/40102 账号锁定/40301 无权限/40302 越权数据/40401 不存在/40901 状态冲突/40902 重复提交/50001 服务异常/50301 AI服务不可用。
- 所有写操作幂等：提交类接口客户端生成 `Idempotency-Key`（或业务唯一约束兜底），重复提交返回首次结果或 40902。
- 时间格式 ISO-8601；枚举值见 PROJECT_IMPLEMENTATION_GUIDE 第 5 节。

## 1. auth（公开）
| 方法 | 路径 | 说明 | 请求 | 响应 |
|---|---|---|---|---|
| POST | /auth/register | 患者注册(仅PATIENT) | {username,password,realName,phone?,gender?,birthDate?} | {id,username} |
| POST | /auth/login | 登录 | {username,password} | {accessToken,refreshToken,expiresIn,user:{id,username,realName,roles[],mustChangePassword}} |
| POST | /auth/refresh | 刷新 | {refreshToken} | {accessToken,refreshToken,expiresIn} |
| POST | /auth/logout | 登出(吊销refresh) | {refreshToken} | 204 |
| GET | /auth/me | 当前用户 | - | {id,username,realName,roles[],permissions[]} |
| PUT | /auth/password | 改密 | {oldPassword,newPassword} | 204 |

## 2. users / roles（ADMIN）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /users | 分页+按 role/keyword 过滤 |
| POST | /users | 建用户 {username,realName,password,roles[],phone?} |
| PUT | /users/{id} | 改资料/角色 |
| PUT | /users/{id}/status | {enabled:bool} 启停 |
| PUT | /users/{id}/password | 管理员重置 |
| GET | /roles | 角色+权限树 |
| PUT | /roles/{code}/permissions | {permissionIds[]} |

## 3. patients（PATIENT 本人 / DOCTOR,FOLLOWUP,ADMIN 可查）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /patients | 分页（患者仅见本人；列表脱敏） |
| POST | /patients | 建档 {patientNo(可空自动生成),name,gender,birthDate,phone?,idCard?,bloodType?,chronicTags[],address?} |
| GET | /patients/{id} | 详情含既往史/过敏/用药 |
| PUT | /patients/{id} | 改档案（写档案修改记录） |
| POST | /patients/{id}/histories | 加既往史 {diseaseName,diagnosedAt?,note?} |
| POST | /patients/{id}/allergies | 加过敏 {allergen,reaction?,severity?} |
| POST | /patients/{id}/medications | 加用药 {drugName,dosage?,frequency?,startDate?,endDate?} |
| DELETE | /patients/{id}/{histories\|allergies\|medications}/{subId} | 删除子记录 |

## 4. visits 预问诊
| 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|
| POST | /visits/draft | PATIENT | 存草稿 {id?,patientId,formData{...}} → {id,status} |
| GET | /visits/drafts | PATIENT | 我的草稿列表 |
| POST | /visits | PATIENT | 提交 {patientId,formData,idempotencyKey} → {id,status:SUBMITTED}；服务端依次置 STRUCTURING/RULE_SCREENED/AI_ANALYZING 并启动 AI 流水线 |
| GET | /visits | 全部 | 分页（患者仅本人；医生/随访见全部，列表脱敏）；过滤 status/riskLevel/patientId |
| GET | /visits/{id} | 相关方 | 详情：formData+结构化+规则命中+分诊结果（未 REVIEWED 时患者不可见 AI 内容） |
| PUT | /visits/{id}/supplement | PATIENT | 补充信息 {formData}（仅 NEED_INFO）→ 回 PENDING_REVIEW 并重跑 SAFETY/SUMMARY |
| GET | /visits/{id}/status-logs | 相关方 | 状态流转记录 |
| POST | /visits/{id}/retry | DOCTOR/ADMIN | FAILED 重跑流水线 |

formData 结构（分步表单）：
```json
{"basic":{"height":null,"weight":null,"specialGroup":"NONE|PREGNANT|ELDERLY|INFANT|CHRONIC"},
 "chiefComplaint":"主诉文本","onsetTime":"2026-07-01","duration":"3天","severity":"MILD|MODERATE|SEVERE",
 "accompanying":["咳嗽","发热"],"triggers":"","reliefFactors":"","aggravatingFactors":"",
 "pastHistory":"文本","allergyHistory":"文本","medication":"文本","supplement":"文本"}
```

## 5. triage（只读查询，DOCTOR/ADMIN）
| GET | /triage/visits/{visitId} | 结构化结果+风险+引用+安全审查汇总 |
| GET | /triage/visits/{visitId}/extractions | symptom_extractions 列表 |
| GET | /triage/visits/{visitId}/rule-hits | 规则命中（含规则名/版本/依据/等级） |
| GET | /triage/visits/{visitId}/citations | 引用（文档名/章节/页码/原文片段/得分/知识库版本） |

## 6. rules（ADMIN 管理；DOCTOR 只读）
| GET | /rules | 分页 {keyword,enabled} |
| POST | /rules | {code,name,category:RED_FLAG\|SPECIAL_GROUP\|DRUG\|COMBINATION\|MISSING_INFO\|ESCALATION,priority,conditionExpr(JSON),message,riskLevel,enabled} |
| PUT | /rules/{id} | 修改（生成新 rule_versions） |
| PUT | /rules/{id}/toggle | {enabled} |
| GET | /rules/{id}/versions | 版本列表 |
| POST | /rules/test | 规则测试 {visitId? 或 formData} → 命中列表 |

conditionExpr（JSON DSL，确定性求值）：
```json
{"all":[{"field":"symptoms","op":"CONTAINS_ANY","value":["胸痛","胸闷"]},
        {"field":"symptoms","op":"CONTAINS_ANY","value":["呼吸困难","气促"]}],
 "not":[{"field":"specialGroup","op":"EQ","value":"NONE"}]}
```
支持 all/any/not；op：EQ/NE/IN/CONTAINS_ANY/CONTAINS_ALL/GT/LT/IS_EMPTY；field：symptoms/accompanying/specialGroup/severity/durationDays/pastHistory/allergyHistory/medication/chiefComplaint。

## 7. guidelines + knowledge（ADMIN 管理；DOCTOR 只读检索）
| GET | /guidelines | 分页（文档元数据） |
| POST | /guidelines | 上传：multipart file + {title,org,publishDate,docType,scope,sourceNote} → 存 MinIO + knowledge_documents(PENDING) + 触发 AI 摄取 |
| PUT | /guidelines/{id} | 改元数据 |
| PUT | /guidelines/{id}/toggle | 启停（停用不参与检索） |
| DELETE | /guidelines/{id} | 软删+停用 chunk |
| POST | /knowledge/reindex | 重建索引（重嵌全部 chunk） |
| GET | /knowledge/documents/{id}/chunks | 分块列表 |
| POST | /knowledge/search | {query,topK?} → 命中 chunk+得分（内部转 AI 服务） |
| GET | /knowledge/versions | 知识库版本列表（按 document version 聚合） |

## 8. agent-runs（DOCTOR/ADMIN；患者仅见本人 visit 的状态）
| GET | /agent-runs | 分页 {status,visitId} |
| GET | /agent-runs/{id} | 详情+steps+token 用量+版本信息 |
| GET | /agent-runs/{id}/events | **SSE** 进度推送（`?token=` 兼容 EventSource）；事件：step_started/step_completed/run_completed/run_failed |
| GET | /visits/{id}/agent-run | 该 visit 最新 run |

## 9. reviews（DOCTOR）
| GET | /reviews/queue | 待审核队列（status=PENDING_REVIEW/NEED_INFO，按风险/时间过滤排序） |
| POST | /reviews | 提交审核 {visitId,action:APPROVE\|REJECT\|REQUEST_INFO,comment,modifiedSummary?,modifiedRiskLevel?} → APPROVE 后 visit=REVIEWED；REQUEST_INFO 后 visit=NEED_INFO |
| GET | /reviews/visits/{visitId} | 该 visit 审核历史（版本对比） |

## 10. followup-plans / followup-tasks
| POST | /followup-plans | DOCTOR 创建 {visitId,patientId,planName,frequencyCron? 或 intervalDays,startDate,endCondition,items[{title,content}]} → 状态 PENDING_START |
| POST | /followup-plans/{id}/start | 启动 → ACTIVE + 自动生成首批任务 |
| POST | /followup-plans/{id}/pause /resume /terminate | 状态操作 |
| GET | /followup-plans | 分页（患者仅本人） |
| GET | /followup-plans/{id} | 详情+任务列表 |
| GET | /followup-tasks | 分页 {status,riskLevel,dueBefore,assigneeId,patientId}（患者仅本人） |
| POST | /followup-tasks/{id}/claim | FOLLOWUP 认领 → ASSIGNED |
| POST | /followup-tasks/{id}/start | → IN_PROGRESS |
| POST | /followup-tasks/{id}/complete | {record:{contactResult,symptomChange,note,feedback}} → COMPLETED + 写 followup_records |
| POST | /followup-tasks/{id}/delay | {reason,newDueDate} → DELAYED |
| POST | /followup-tasks/{id}/lost | {reason} → LOST |
| POST | /followup-tasks/{id}/escalate | {reason} → ESCALATED + 安全告警 |
| GET | /followup-tasks/trends | {patientId} → 症状变化趋势（ECharts 数据） |

## 11. safety-alerts / audit-logs / files / admin
| GET | /safety-alerts | 分页 {type,status} |
| POST | /safety-alerts/{id}/handle | ADMIN {action:ACK\|CLOSE,note} |
| GET | /audit-logs | 分页 {userId,action,objectType,dateFrom,dateTo}（ADMIN） |
| POST | /files | 通用上传（multipart，≤10MB）→ {fileId,url} |
| GET | /files/{id}/download | 鉴权下载 |
| GET/PUT | /admin/configs | 系统配置 KV |
| GET | /admin/models | model_configs 列表；PUT /admin/models/{id} 启停/改参 |
| GET/POST/PUT | /admin/prompts… | prompt_templates + prompt_versions 管理与启停 |

## 12. 内部接口（/api/internal，X-Internal-Token）
| 方法 | 路径 | 调用方 | 说明 |
|---|---|---|---|
| POST | /internal/agent-runs/{runId}/completed | AI→后端 | 流水线完成回调 {runStatus,summary} → 后端状态机置 PENDING_REVIEW（或 REVIEW_FAILED 置 FAILED+告警） |
| GET | /internal/files/{fileId}/download | AI→后端 | 取知识文档原文（摄取用） |
| POST | /internal/knowledge/documents/{docId}/ingested | AI→后端 | 摄取完成 {chunkCount,status} → 置 ENABLED/INGEST_FAILED |
| GET | /internal/visits/{id}/context | AI→后端 | 取结构化上下文（补充重跑用） |

后端→AI（base `AI_SERVICE_URL`，默认 http://ai-service:8000）：
- POST /internal/pipeline/start {runId, visitId, forceSteps?} → 后台异步执行
- POST /internal/knowledge/ingest {documentId, fileUrl, title, ...}
- POST /internal/search {query, topK, filters} → chunks+scores
- GET /health

## 13. AI 服务内部接口（FastAPI，仅后端可达）
除上述被调接口外：GET /health；所有接口校验 `X-Internal-Token`。
