# 医疗 RAG 实现、网络语料与边界

## 当前状态

知识库已由“2 个手写来源、3 个教学分块”升级为可复现的官方网络语料快照。截至 2026-07-22，本地快照包含 21 个成功来源、150 个分块，来源机构为 WHO、CDC、NIH/NHLBI 和 NHS，覆盖高血压、胸痛、呼吸困难、晕厥、头痛、卒中、心肌梗死、心力衰竭、心律失常及心血管风险预防。

完整工程链路为：固定官方来源白名单 → 检查 robots.txt → 限速、重试并抓取正文 → 清理页面模板和重复段落 → 稳定分块 → 生成带 SHA-256 的 JSON 快照 → 后端校验哈希 → Markdown 原文上传 MinIO → 来源、版本和抓取证据写入 PostgreSQL → 64 维确定性 hashing embedding 写入 pgvector → HNSW cosine 检索 → Agent 引用白名单校验。

运行中的后端和 AI 服务不访问外网。联网只发生在显式执行采集脚本时，因此每次入库内容都可以先审查、归档和复现。采集命令必须使用项目指定虚拟环境：

```powershell
D:\Anaconda\envs\ML3.9\python.exe data-pipeline\crawl_official_medical_sources.py
```

采集器当前只使用虚拟环境中已有的 `requests`、`beautifulsoup4` 和 `lxml`，没有新增 Python 依赖。`requests` 连续失败时会调用系统现有 `curl.exe` 完成同一白名单 URL 的抓取，并在 `retrievalMethod` 中明确记录 `curl-fallback`，不会静默换来源或生成合成正文。

## 语料与审计文件

- 采集器：`data-pipeline/crawl_official_medical_sources.py`
- 固定来源与治理策略：`data-pipeline/sources/manifest.yaml`
- 可入库快照：`backend/src/main/resources/knowledge/official-corpus.json`
- 最近一次成功/失败清单：`data-pipeline/corpus/crawl-report.json`
- 数据库来源字段迁移：`backend/src/main/resources/db/migration/V3__web_corpus_provenance.sql`

每个网络文档记录原始 URL、重定向后的 URL、抓取时间、HTTP 状态、ETag、Last-Modified、采集方式、许可说明、正文 SHA-256、版本 ID 和全部分块。内容哈希决定版本 ID；相同内容重复采集和重建是幂等的，内容变化会生成新版本，旧版本保留但不再激活。管理员页面可查看来源状态、采集方式、时间、哈希和 MinIO object key。

项目仍保留 3 个明确标注为 `CONTROLLED_BASELINE` 的自制安全分块，用于红旗规则及离线测试兜底。它们不冒充网络指南，也不再是知识库主体。

## 安全约束

- 只抓固定白名单中的官方公共卫生/医疗机构页面，不跟随站内链接扩张爬取范围。
- 遵守 robots.txt；禁止或无法取得合规结果时写入失败清单，不绕过限制。
- 不抓论坛、社交媒体、病例分享、患者数据或来源不明内容。
- 页面许可说明是工程审计元数据，不构成法律意见；展示和再利用仍以原站条款及页面中的第三方版权标记为准。
- `EvidenceRetrieverAgent` 只检索数据库中当前激活版本；`CitationVerifierAgent` 只接受本次检索返回的 chunk ID，后端落库前再次确认 chunk 仍激活。
- RAG 结果不能降低确定性红旗规则给出的紧急度，不能绕过有资质人员终审。
- 内部知识检索接口要求 `X-Internal-Token`，不经 Nginx 对外暴露。

## 验证与诚实边界

采集器单测覆盖正文清洗、导航剔除、稳定 chunk ID、段落重叠和域名白名单；后端测试校验快照文档数、分块数、URL、许可、抓取时间、SHA-256 与 chunk ID 唯一性；集成测试继续连接真实 PostgreSQL/pgvector 并验证 HNSW、鉴权与检索返回。

当前向量仍是 `fake-embedding-v1`（64 维确定性 hashing embedding）。它足以验证 pgvector/HNSW、版本治理、引用和部署链路，但不是医学语义向量模型。网络语料的增加改善了知识覆盖范围，不代表临床准确率已经得到证明。本系统只处理合成教学病例，不提供诊断、处方或真实医疗建议。
