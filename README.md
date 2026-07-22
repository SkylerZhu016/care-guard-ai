# 基层医疗安全型预问诊与随访平台

面向工程实训的全栈教学演示项目。系统只处理合成病例与公开指南，不提供诊断、处方或真实医疗建议。确定性规则承担安全底线，AI 只整理信息和提供可核验引用，最终结论必须由模拟医务人员审核。

## 已实现闭环

模拟患者登录并提交预问诊 → 红旗规则筛查 → 受控 AI 分析与引用校验 → 医务人员审核 → 激活高血压教学随访计划 → 随访人员完成任务 → 管理员查看安全告警与审计。

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
pnpm --config.store-dir=H:/pnpm-store install --frozen-lockfile
pnpm dev
```

打开 `http://localhost:5173`。开发服务器会代理 `/api` 到后端。

## 一键容器启动

复制 `.env.example` 为 `.env`，修改本地演示密码后运行：

```powershell
wsl -d HermesUbuntu -- bash -lc 'cd "/mnt/d/工程实训/final" && docker compose up --build -d --wait'
```

打开 `http://localhost:18088`。无真实模型密钥时默认使用可复现的 Fake Provider，界面会持续标记“FAKE / 教学模拟”。JDK 21 固定使用 `H:\Java\jdk-21`，pnpm 共享仓库位于 `H:\pnpm-store`。

## 测试

```powershell
$env:JAVA_HOME = 'H:\Java\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -f .\backend\pom.xml '-Dmaven.repo.local=H:\Maven\repository' test
& 'D:\Anaconda\envs\ML3.9\python.exe' -m pytest .\ai-service
Set-Location .\frontend; pnpm test -- --run; pnpm build; Set-Location ..
.\scripts\api-smoke.ps1
.\scripts\performance-smoke.ps1
```

2026-07-22 最终验证基线：后端 5/5、AI 6/6（覆盖率 82.82%）、前端 3/3，生产构建和完整 API 闭环均通过；AI 状态为 `SUCCEEDED` 且引用非空。性能冒烟为 50 请求、并发 10、失败 0、P95 25 ms。四角色页面已通过真实浏览器验收，633px 视口无横向页面溢出。

详细材料见 [docs/README.md](docs/README.md)、[docs/testing/TEST_REPORT.md](docs/testing/TEST_REPORT.md) 和 [docs/demo/DEMO_SCRIPT.md](docs/demo/DEMO_SCRIPT.md)。
