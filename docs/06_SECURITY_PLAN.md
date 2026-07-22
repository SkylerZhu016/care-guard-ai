# 06 安全与数据合规规划

## 1. 认证与账号安全
- JWT access(2h)+refresh(7d，`refresh_tokens` 存哈希、轮换、登出吊销)；BCrypt(10) 存密码。
- 连续失败 5 次锁 15 分钟（users.failed_attempts/locked_until），锁定期返回 40102。
- 密码策略：≥8 位含字母数字；管理员创建的用户 must_change_password=true。

## 2. 授权（三层）
1. URL 层：Spring Security 按前缀粗粒度（`/api/v1/a/**`→ADMIN 等）。
2. 方法层：`@PreAuthorize("hasAuthority('review:write')")`。
3. 数据层：患者接口强制 `owner_user_id = currentUser`；医生/随访/管理端列表由服务端脱敏。
越权尝试 → 403 + `safety_alerts(UNAUTHORIZED_ACCESS)` + 审计。

## 3. 敏感数据与脱敏
- 分类：姓名/电话/身份证=敏感；主诉=半敏感。
- 规则：列表统一掩码（张*三 / 138****1234 / 110***********1234）；详情仅 DOCTOR/ADMIN/本人可见明文。
- 日志红线：不记密码、Token、API Key、完整证件号；审计 before/after 只存掩码摘要（≤1000 字符）。

## 4. AI 安全（提示词攻防）
- **输入侧**：注入特征清单（忽略之前/以上指令、你现在是、输出系统提示词、DAN、base64 长串等正则+关键词）→ 命中即不调用 LLM，告警 PROMPT_INJECTION，输入原样仅留存于审计掩码摘要。
- **系统提示**：永不回显；Prompt 模板不含密钥；渲染前对用户输入做分隔包裹（<<<user_input>>>）。
- **输出侧**：诊断词/处方词黑名单；强制免责声明；高风险固定话术「建议尽快寻求专业医疗帮助」；引用缺失降级。详见 05。
- **PII 外泄**：输出正则扫描手机号/身份证 → 命中告警 PRIVACY_LEAK 并阻断展示。

## 5. Web 与接口安全
- XSS：前端 v-html 禁用（AI 文本一律纯文本渲染）；后端输出 JSON 默认转义。
- SQL 注入：全量 JPA/MyBatis 参数化；无拼接 SQL。
- CSRF：纯 Bearer Token，无 Cookie 会话，免 CSRF；CORS 仅放行前端源。
- 上传：白名单 txt/md/pdf ≤10MB；MIME+魔数双校验；sha256 落库；MinIO 私有桶，下载走鉴权接口。
- 限流：登录/注册/提交 接口按 IP 简易桶（增强项可换 Redis）。
- 幂等：提交类接口 Idempotency-Key + 业务唯一约束；重复提交不重复建单。

## 6. 密钥管理
- 全部密钥（DB 密码、MinIO Key、LLM Key、JWT Secret、内部 Token）仅环境变量；`.env.example` 只放占位；`.gitignore` 排除 `.env`。
- 前端不接触任何服务间密钥；JWT Secret 与内部 Token 分离。

## 7. 数据合规声明（页面+文档双落地）
1. 本系统为教学用辅助系统，不连接真实医院数据，不使用未经授权的真实患者数据。
2. 演示病例全部为合成数据；知识库为公开指南的教学整理。
3. AI 输出仅供教学参考，不能替代医生诊断；高风险一律建议尽快寻求专业医疗帮助。
4. 数据仅用于课程实训，课后可按脚本一键清除（reset-demo）。
- 落点：登录页/问诊页/AI 结果页固定免责声明；`docs/submission/安全与数据合规说明.md` 汇总。

## 8. 审计与可追溯
- 记录维度：操作人/角色/动作/对象/前后掩码摘要/IP/traceId；AI 维度：模型名、Prompt 版本、知识库版本、规则版本、步骤快照、引用、原始输出、审查结果、人工修改 diff。
- audit_logs 只增不改；查询仅 ADMIN。
