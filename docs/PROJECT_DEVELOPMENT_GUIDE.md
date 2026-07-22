# 项目开发指导书

## 1. 目标

本项目实现通用预问诊与随访信息链路。患者可以用中文记录常见不适，服务端对有限且有现有依据的事实组合执行确定性规则，其余内容明确进入人工复核。AI/RAG 只整理信息和证据。

本指导书不再把前端写死为三步表单、四症状目录或统一数字评分。原始任务书 `期末任务.docx` 不属于本指导书的修改范围。

## 2. 架构边界

```text
Vue 患者/医务/随访/管理端
        │ /api/v2（问诊）+ /api/v1（审核与治理）
Spring Boot 3 / Java 21
        ├─ PostgreSQL + pgvector
        ├─ Redis/Celery 任务状态
        ├─ MinIO 知识原文与产物
        └─ FastAPI / LangGraph 信息整理与 RAG
```

PostgreSQL 是唯一业务数据库。包名 `com.example.medsim` 和制品名是技术标识，不进行无收益改名。

## 3. v2 数据契约

### 3.1 目录

`GET /api/v2/intake-catalog` 返回版本号、中文名称、分类、支持级别、问题和选项。前端不得自行决定支持级别。

- `RULE_SUPPORTED`：仅胸痛、呼吸困难、晕厥、意识异常。
- `RECORD_ONLY`：其他常见目录症状。
- `CUSTOM`：其他不适，必须填写简短名称。

### 3.2 问诊

`VisitIntakeV2` 由主症状、主诉、补充描述和 `SymptomReport[]` 构成。`SymptomReport` 保存：

- `symptomCode`、`source`、`catalogVersion`、`supportLevel`
- `onsetRange`：刚刚、今天、1–3 天、3 天以上、不清楚
- `course`：持续、间歇、已经缓解、不清楚
- `currentStatus`：存在、不存在、不清楚
- `activityImpact`：不影响、需要停下休息、无法正常活动、不清楚
- `answers`：问题 ID、所选选项和可选补充文字

活动影响只进入摘要和人工审核，不换算成旧分值。

### 3.3 健康资料

`GET/PUT /api/v2/patient-profile` 维护年龄段、必要生理信息、慢病、过敏和长期用药。提交时把当前资料写入 `visits.profile_snapshot`，以后修改档案不会改变历史问诊。

## 4. 四阶段患者流程

### 4.1 哪里不舒服

目录必须来自服务端。页面提供搜索、中文分类、常用卡片和其他不适。选中项目后生成可编辑主诉。患者端不显示英文代码。

### 4.2 具体情况

所有症状显示四个通用事实问题；四类规则症状追加目录定义的特异问题。关联回答为“是”时，可自动加入对应症状并标记 `RELATED_ANSWER`。

### 4.3 特别注意

命中急症或紧急事实组合时，用文字和颜色共同提示，可跳过非必要信息直接提交。仅记录症状必须显示“会被记录并交由人工复核”，不能显示常规、无风险或筛查通过。

### 4.4 检查提交

汇总症状、发生时间、当前状态、活动影响和说不清的项目。确认文案为“以上信息与我填写的一致”。本机自动保存键为 `patient-intake-v2-draft`；服务端草稿通过 `POST/PUT /api/v2/visits` 保存。

## 5. 规则实现

`RuleEngine.evaluateV2` 只读取规则问题的事实答案：

```text
意识异常存在                           -> EMERGENCY
胸口不适存在 + 呼吸困难存在            -> EMERGENCY
胸口不适存在 + 真正失去意识             -> EMERGENCY
呼吸困难存在                           -> URGENT
其他情况                               -> ruleUrgency = null
```

覆盖状态按报告支持级别计算：全部支持为 `FULL`，混合为 `PARTIAL`，没有支持项为 `NONE`。混合症状不能降低已经命中的紧急度；未命中时为 `REQUIRES_MANUAL_REVIEW`。

## 6. 历史兼容与迁移

Flyway V4 完成：

- 用户角色迁移为 `PATIENT` 并重建约束。
- `visits` 增加 intake 版本、主症状和资料快照。
- 旧数字列改名为可空的 `legacy_severity`，新增事实列与 JSONB 答案。
- `triage_results.rule_urgency` 改为可空，增加覆盖和评估状态。
- 新增 `patient_profiles` 和 `visit_supplements`。
- 随访模板统一为 `GENERAL_FOLLOWUP_V1`。

V1–V3 是已发布迁移，不能修改，否则会破坏 Flyway 校验。旧写接口返回 `410 INTAKE_V1_DEPRECATED`，历史记录可经 v2 读取。

## 7. AI/RAG

Java 发送事实答案、支持级别、覆盖状态和规则结果，不发送旧数字字段。Python schema 的 `ruleUrgency` 和 `proposedUrgency` 均可为空。Fake Provider 与真实 Provider 不执行阈值升级；当规则结果为空时，AI 也必须保持为空。

RAG 语料通过网络采集管道写入 MinIO/PostgreSQL，保留来源 URL、抓取时间、SHA-256、对象键、版本和启用状态。检索到材料不等于获得确定性规则资格。

## 8. 安全要求

- 自由文本在写库前调用 `PrivacySanitizer`。
- AI 输入和输出分别执行注入、身份信息和越界检查。
- 引用必须属于当前启用知识版本。
- 患者端不显示规则代码、覆盖枚举、模型名称和引用治理信息。
- 自动结果不能替代人工审核，不构成诊断或处方。

## 9. 测试

必须覆盖：四条确定性规则、头痛/腹痛/自定义症状转人工、混合症状不降级、旧记录只读、新记录数字列为空、角色迁移、资料快照、补充信息、AI 无数字输入、无证据阻断、390px/桌面流程和键盘焦点。

固定环境命令见仓库根目录 README。本项目不允许为通过测试而安装新依赖或切换到替代数据库。
