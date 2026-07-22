# API 接口文档

## 鉴权方式
所有受保护的接口需要在请求头中携带：
```
Authorization: Bearer <token>
```

## 接口列表

### 1. 认证管理

#### POST /api/auth/login
登录获取 token。
- 请求体：`{ "username": "string", "password": "string" }`
- 响应：`{ "code": 200, "data": { "token": "string", "role": "string", ... } }`

#### POST /api/auth/init
初始化默认测试用户（首次部署调用）。

### 2. 预问诊管理

#### POST /api/consult/submit
提交预问诊信息。
- 请求体：
```json
{
  "patientId": 1,
  "chiefComplaint": "头痛3天",
  "symptoms": [
    {
      "symptomName": "头痛",
      "bodyPart": "头部",
      "severity": 6,
      "duration": "3天",
      "description": "持续性钝痛"
    }
  ]
}
```
- 响应：`{ "code": 200, "data": { "id": 1, "status": "TRIAGING", ... } }`

#### GET /api/consult/pending
获取待处理就诊列表。

#### GET /api/consult/{visitId}/triage
获取分诊结果。

#### GET /api/consult/{visitId}/symptoms
获取症状列表。

#### PUT /api/consult/{visitId}/approve?notes=xxx
医生通过审核。

### 3. 随访管理

#### POST /api/followup/plan
创建随访计划。
- 参数：visitId, name, desc, intervalDays, totalTimes

#### GET /api/followup/plan/{visitId}
获取随访计划列表。

#### GET /api/followup/tasks/{planId}
获取随访任务列表。

#### PUT /api/followup/tasks/{taskId}/complete?response=xxx
完成随访任务。

### 4. 安全监控

#### GET /api/safety/alerts?unreviewedOnly=false
获取安全告警列表。

#### GET /api/safety/alerts/count
获取未审告警数量。

#### PUT /api/safety/alerts/{id}/review
审核告警。

### 5. 仪表盘

#### GET /api/dashboard/stats
获取仪表盘统计数据。

### 6. 指南检索

#### GET /api/guidelines/search?keyword=xxx&category=xxx
检索医疗指南。

### 7. 患者管理

#### GET /api/patients/list
获取模拟患者列表。

## AI 服务接口

### POST /ai/consult/triage
AI 分诊分析。

### POST /ai/consult/submit
完整预问诊流程（分诊 + 安全审核）。

### POST /ai/safety/check
文本安全审核。

### POST /ai/safety/agent-review
多 Agent 安全复核。

### POST /ai/rag/search
RAG 指南检索。

## 错误码

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 401 | 未授权 / token 无效 |
| 404 | 资源未找到 |
| 500 | 服务器内部错误 |
