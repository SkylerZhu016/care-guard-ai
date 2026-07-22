# 基层医疗安全型预问诊与随访平台

面向工程实训的有限范围全栈教学演示项目。系统只处理合成病例与公开指南，不提供诊断、处方或真实医疗建议。当前自动规则只覆盖胸痛、呼吸困难、晕厥和意识异常 4 类结构化症状；目录外症状不代表已完成安全评估。确定性规则承担安全底线，AI 只整理信息和提供可核验引用，最终结论必须由模拟医务人员审核。

## 已实现闭环

模拟患者从中文受控目录提交预问诊 → 红旗规则筛查 → 受控 AI 分析与引用校验 → 医务人员人工终审 → 明确“不创建随访”或对适用病例选择高血压教学模板 → 随访人员完成已激活任务 → 管理员查看安全告警、知识来源与审计。

演示账号（密码均为 `Demo123!`）：

| 账号 | 角色 | 入口 |
|---|---|---|
| `patient` | 模拟患者 | 预问诊与我的任务 |
| `clinician` | 医务人员 | 待审核队列与随访计划 |
| `followup` | 随访人员 | 随访任务看板 |
| `admin` | 管理员 | 安全、运行与审计 |

## 本地开发

### 1. AI 服务（必须使用指定环境）

```powershell
& 'D:\Anaconda\envs\ML3.9\python.exe' -m pip install -r .\ai-service\requirements.txt
& 'D:\Anaconda\envs\ML3.9\python.exe' -m uvicorn app.main:app --app-dir .\ai-service --port 8000
```

### 2. Spring Boot 后端

```powershell
$env:JAVA_HOME = 'H:\Java\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location .\backend
mvn '-Dmaven.repo.local=H:\Maven\repository' spring-boot:run
```

后端统一使用 PostgreSQL + Flyway。开发数据库也由 `HermesUbuntu` 中的 Docker Compose 启动，不使用 H2 或其他替代数据库。

### 3. Vue 前端

```powershell
Set-Location .\frontend
pnpm --config.store-dir=H:/pnpm-store/v11 install --frozen-lockfile
pnpm dev
```

打开 `http://localhost:5173`。开发服务器会代理 `/api` 到后端。

## 一键容器启动

复制 `.env.example` 为 `.env`，修改本地演示密码后运行：

```powershell
wsl -d HermesUbuntu -- bash -lc 'cd "/mnt/d/工程实训/final" && docker compose up --build -d --wait'
```

打开 `http://localhost:18088`。无真实模型密钥时默认使用可复现的 Fake Provider，界面会持续标记“FAKE / 教学模拟”。JDK 21 固定使用 `H:\Java\jdk-21`，pnpm 共享仓库位于 `H:\pnpm-store\v11`。

## 更新官方网络知识库

知识库使用固定白名单采集 WHO、CDC、NIH/NHLBI 和 NHS 的公开医疗页面。运行时服务不联网；需要更新快照时显式执行：

```powershell
& 'D:\Anaconda\envs\ML3.9\python.exe' .\data-pipeline\crawl_official_medical_sources.py
```

脚本检查 robots.txt、限速并记录失败，不会用合成正文替代失败来源。生成文件经审查后，重建 Compose 或在管理员页面点击“重建网络语料索引”即可幂等入库。

## 测试

```powershell
$env:JAVA_HOME = 'H:\Java\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -f .\backend\pom.xml '-Dmaven.repo.local=H:\Maven\repository' test
& 'D:\Anaconda\envs\ML3.9\python.exe' -m pytest .\ai-service
Set-Location .\frontend; pnpm --config.store-dir=H:/pnpm-store/v11 test -- --run; pnpm --config.store-dir=H:/pnpm-store/v11 build; Set-Location ..
.\scripts\api-smoke.ps1
.\scripts\performance-smoke.ps1
```

2026-07-22 当前验证基线：采集器 3/3、后端 10/10、AI 9/9（覆盖率 85.53%）、前端 15/15，前端生产构建通过。此前完整 API 闭环、网络快照抓取 21/21、150 个官方来源分块及 3 个明确标注的项目安全基线分块均已验证；Compose 与浏览器回归结果以测试报告的最新记录为准。

详细材料见 [docs/README.md](docs/README.md)、[RAG 实现与边界](docs/ai/RAG_IMPLEMENTATION.md)、[docs/testing/TEST_REPORT.md](docs/testing/TEST_REPORT.md) 和 [docs/demo/DEMO_SCRIPT.md](docs/demo/DEMO_SCRIPT.md)。
