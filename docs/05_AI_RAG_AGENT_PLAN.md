# 05 AI / RAG / Agent 规划

## 1. 流水线总览（agent_runs + agent_run_steps 全留痕）

`STRUCTURE → RETRIEVE → RISK → SAFETY → SUMMARY`
- 每步：started/finished/tokens/duration/input_json/output_json 落 `agent_run_steps`。
- 任一步失败：run=FAILED（或 PARTIAL），保留已成功步骤与规则结果，回调后端置 visit=FAILED + 告警。
- SAFETY=FAIL：run=REVIEW_FAILED，不执行 SUMMARY，写 safety_alerts(REVIEW_FAILED)。
- 补充信息重跑：只重跑 SAFETY+SUMMARY（复用 STRUCTURE/RETRIEVE/RISK 产物）。

## 2. LLM 客户端（clients/llm.py）
- 配置：`LLM_API_BASE` `LLM_API_KEY` `LLM_MODEL` `LLM_TIMEOUT=30s` `LLM_MAX_RETRIES=2`（指数退避 1s→4s）`LLM_MAX_INPUT_CHARS=12000` `LLM_MAX_TOKENS=2048`。
- `MOCK_LLM=true` → MockLLM：按输入关键词确定性生成完全符合 Schema 的 JSON（离线演示/评测基准）。
- 返回非 JSON → 自动剥离 ```json 围栏 + json_repair 修复重试 1 次 → 仍失败抛 `LLMOutputError`。
- 记录每次调用的 tokens（mock 用估算值）累加进 agent_runs.total_tokens。

## 3. JSON Schemas（schemas/，均含 schema_version）

### 3.1 症状结构化 SymptomExtraction v1
```json
{"schema_version":"1.0","chief_complaint":"string","symptoms":[{"name":"string","body_part":"string|null","severity":"MILD|MODERATE|SEVERE|null","duration":"string|null"}],
 "onset_time":"string|null","duration_text":"string|null","triggers":"string|null","relief_factors":"string|null","aggravating_factors":"string|null",
 "accompanying":["string"],"denied":["string"],"past_history":["string"],"allergies":["string"],"medications":["string"],
 "special_group":"NONE|PREGNANT|ELDERLY|INFANT|CHRONIC","missing_fields":["string"],"confidence":0.0}
```
必填：chief_complaint、symptoms、missing_fields、confidence。校验失败 → 修复重试 → 降级 PARTIAL。

### 3.2 检索请求 RetrievalQuery v1
`{schema_version, queries:[string], top_k:int=5, doc_types?:[string]}`

### 3.3 引用 Citation v1
`{schema_version, chunk_id, document_id, title, section, page_no, snippet, score, knowledge_version}`

### 3.4 风险分析 RiskAnalysis v1
```json
{"schema_version":"1.0","risk_level":"LOW|MEDIUM|HIGH|CRITICAL","risk_summary":"string",
 "risk_points":[{"point":"string","basis":"string","citation_ids":["string"],"severity":"LOW|..."}],
 "questions_for_doctor":["string"],"red_flags_noticed":["string"]}
```
硬约束：risk_point 无 citation_ids 时 severity 不得高于 MEDIUM，且 basis 须标注「证据不足」。**最终 risk_level = max(规则最高级, AI 级)**（在后端合并，规则优先）。

### 3.5 安全审查 SafetyReview v1
```json
{"schema_version":"1.0","verdict":"PASS|FAIL","issues":[{"type":"DIAGNOSIS|PRESCRIPTION|NO_DISCLAIMER|REDFLAG_MISSED|NO_CITATION|INJECTION|PROMPT_LEAK|PII_LEAK|HALLUCINATION","detail":"string","severity":"HIGH|MEDIUM|LOW"}],"sanitized":false}
```
确定性检查项（safety/checker.py，非 LLM 也可执行）：诊断词表（确诊/诊断为/你患了…）、处方词表（每日三次/口服 mg/处方）、免责声明存在性、红旗症状覆盖（对照 rule_hits）、引用存在性、注入特征、PII 正则（手机号/身份证）。FAIL 即阻断。

### 3.6 医生摘要 ReviewSummary v1
```json
{"schema_version":"1.0","chief_complaint":"string","symptom_table":[{"name":"...","severity":"...","duration":"..."}],
 "risk_level":"...","risk_points":[...同3.4],"citations":[...3.3],"suggested_focus":["string"],"disclaimer":"本内容由 AI 生成，仅供教学参考，不能替代医生诊断。"}
```

### 3.7 随访建议草案 FollowupAdvice v1（增强，预留）
`{schema_version, advices:[{topic,content,source_citation_id}], disclaimer}`

## 4. RAG 设计（rag/）
- **分块**：按段落聚合，目标 300~500 字、重叠 60 字；保留章节/页码。
- **嵌入**：默认 `HashEmbedder(256)`：分词（jieba 可选，默认按字 bigram+词）→ 特征哈希 → L2 归一；配置 `EMBEDDING_API_BASE/KEY/MODEL` 时切换 OpenAI 兼容嵌入（维度随 `EMBEDDING_DIM`，需重建）。
- **检索**：score = 0.6·cosine + 0.4·keyword_overlap（查询词在 content 命中数/查询词数）；过滤 enabled 文档；top_k=5；无结果（最高分<0.15）→ citations 为空并在风险摘要标注「指南证据不足」。
- **知识库版本**：system_configs.knowledge_version（KV-n），每次摄取/启停/重建 +1；citations 落版本号。
- **评测**：tests/eval 内置 10 条合成问诊→期望关键词；指标：命中率/引用相关（人工核验表）。

## 5. 五段 Agent 提示词要点（prompts/ + DB prompt_versions 一致）
- STRUCTURE：只输出 Schema JSON；不得给建议。
- RETRIEVE：由症状生成 2~4 个检索词。
- RISK：基于证据列关注点；禁止诊断词；每条挂 citation。
- SAFETY：按检查单复核上一步输出（LLM 复核 + 确定性 checker 双保险，checker 为一票否决）。
- SUMMARY：面向医生的中性摘要，固定结尾免责声明。

## 6. 失败与降级矩阵
| 故障 | 行为 | 落库/告警 |
|---|---|---|
| 模型超时/不可用 | 重试2次→失败 | run=FAILED；告警 MODEL_TIMEOUT/MODEL_ERROR |
| 非 JSON/字段缺失 | 修复+重试1次 | 失败→PARTIAL；告警 JSON_PARSE_ERROR |
| 越权诊断/处方 | SAFETY checker 拦截 | REVIEW_FAILED + 告警 REVIEW_FAILED |
| 无引用结论 | 降级为「需人工关注」 | 告警 CITATION_MISSING |
| RAG 无结果 | 风险降级标注证据不足 | 继续，不阻断 |
| 注入特征 | 输入侧拦截不调用 LLM | 告警 PROMPT_INJECTION |
| SSE 中断 | 前端轮询兜底 | 无 |
| 后端回调失败 | AI 侧重试3次 | run 保持终态，人工 retry |
