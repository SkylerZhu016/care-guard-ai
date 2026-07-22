[CmdletBinding()]
param([string]$BaseUrl = 'http://localhost:18088')

$ErrorActionPreference = 'Stop'
$apiRoot = $BaseUrl.TrimEnd('/')

function Invoke-Api {
    param([string]$Method, [string]$Path, [string]$Token, $Body, [hashtable]$ExtraHeaders = @{})
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    foreach ($key in $ExtraHeaders.Keys) { $headers[$key] = $ExtraHeaders[$key] }
    $args = @{ Method = $Method; Uri = "$apiRoot$Path"; Headers = $headers; TimeoutSec = 70 }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 10 -Compress
        $args.ContentType = 'application/json; charset=utf-8'
        $args.Body = [Text.Encoding]::UTF8.GetBytes($json)
    }
    try {
        $response = Invoke-WebRequest @args -UseBasicParsing
        $stream = $response.RawContentStream
        $stream.Position = 0
        $bytes = New-Object byte[] $stream.Length
        [void]$stream.Read($bytes, 0, $bytes.Length)
        $text = [Text.Encoding]::UTF8.GetString($bytes)
        $result = if ([string]::IsNullOrWhiteSpace($text)) { $null } else { $text | ConvertFrom-Json }
        if ($result -is [System.Array]) { foreach ($item in $result) { Write-Output $item } }
        else { $result }
    }
    catch {
        $status = 0
        $detail = $_.Exception.Message
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            try {
                $reader = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
                $detail = $reader.ReadToEnd()
            } catch {}
        }
        throw "$Method $Path failed with HTTP $status`: $detail"
    }
}

function Login([string]$Username) {
    Invoke-Api -Method Post -Path '/api/v1/auth/login' -Token '' -Body @{ username = $Username; password = 'Demo123!' }
}

function Assert-That([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Smoke assertion failed: $Message" }
}

$patient = Login 'patient'
$clinician = Login 'clinician'
$followup = Login 'followup'
$admin = Login 'admin'

$catalog = Invoke-Api Get '/api/v2/intake-catalog' $patient.accessToken $null
Assert-That ($catalog.version -eq 'intake-catalog-2026.07') 'v2 catalog version must match'
Assert-That (($catalog.symptoms | Where-Object { $_.code -eq 'HEADACHE' }).supportLevel -eq 'RECORD_ONLY') 'headache must be record-only'

$profile = Invoke-Api Put '/api/v2/patient-profile' $patient.accessToken @{
    ageBand = 'ADULT'; physiologicalInfoStatus = 'UNKNOWN'; physiologicalInfo = ''
    chronicConditionsStatus = 'PROVIDED'; chronicConditions = @('hypertension')
    allergiesStatus = 'NONE'; allergies = @(); longTermMedicationsStatus = 'UNKNOWN'; longTermMedications = @()
}
Assert-That ($profile.data.chronicConditions[0] -eq 'hypertension') 'patient profile must persist'

$visitBody = @{
    primarySymptomCode = 'CHEST_PAIN'
    chiefComplaint = '胸口不适并伴呼吸困难'
    freeText = '不含真实身份信息的自动化测试数据'
    symptomReports = @(
        @{ symptomCode = 'CHEST_PAIN'; source = 'CATALOG'; onsetRange = 'JUST_NOW'; course = 'CONTINUOUS'; currentStatus = 'PRESENT'; activityImpact = 'UNABLE_NORMAL_ACTIVITY'; answers = @(@{ questionId = 'chest.current'; selectedOptions = @('YES') }) },
        @{ symptomCode = 'DYSPNEA'; source = 'CATALOG'; onsetRange = 'JUST_NOW'; course = 'CONTINUOUS'; currentStatus = 'PRESENT'; activityImpact = 'UNABLE_NORMAL_ACTIVITY'; answers = @(@{ questionId = 'dyspnea.current'; selectedOptions = @('YES') }) }
    )
}
$visit = Invoke-Api Post '/api/v2/visits' $patient.accessToken $visitBody
$submitted = Invoke-Api Post "/api/v2/visits/$($visit.id)/submit" $patient.accessToken $null @{ 'Idempotency-Key' = [guid]::NewGuid().ToString() }
Assert-That ($submitted.status -eq 'PENDING_REVIEW') 'visit must enter PENDING_REVIEW'
Assert-That ($submitted.triage.ruleUrgency -eq 'EMERGENCY') 'red-flag rules must return EMERGENCY'
Assert-That ($submitted.triage.coverageStatus -eq 'FULL') 'supported red-flag visit must have full coverage'
Assert-That ($submitted.symptomReports[0].legacySeverity -eq $null) 'v2 symptom must not write legacy severity'
Assert-That ($submitted.profileSnapshot.chronicConditions[0] -eq 'hypertension') 'submitted visit must snapshot profile'
Assert-That ($submitted.runs.Count -ge 1) 'AI run must be recorded even when degraded or blocked'
Assert-That ($submitted.runs[0].status -eq 'SUCCEEDED') 'AI run must succeed in the complete Docker environment'
Assert-That ($submitted.runs[0].citations.Count -ge 1) 'successful AI run must include at least one citation'
Assert-That (-not [string]::IsNullOrWhiteSpace($submitted.runs[0].citations[0].chunkId)) 'citation chunkId must be non-empty'
Assert-That (-not [string]::IsNullOrWhiteSpace($submitted.runs[0].citations[0].quote)) 'citation quote must be non-empty'
Assert-That (-not [string]::IsNullOrWhiteSpace($submitted.runs[0].citations[0].sourceUrl)) 'citation source URL must be non-empty'
Assert-That ($submitted.runs[0].agentTrace.Count -eq 5) 'multi-agent trace must contain five observable roles'

$manualBody = @{
    primarySymptomCode = 'HEADACHE'; chiefComplaint = '今天头痛'; freeText = '需要人工复核'
    symptomReports = @(@{ symptomCode = 'HEADACHE'; source = 'CATALOG'; onsetRange = 'TODAY'; course = 'INTERMITTENT'; currentStatus = 'PRESENT'; activityImpact = 'NEEDS_REST'; answers = @() })
}
$manualVisit = Invoke-Api Post '/api/v2/visits' $patient.accessToken $manualBody
$manualSubmitted = Invoke-Api Post "/api/v2/visits/$($manualVisit.id)/submit" $patient.accessToken $null @{ 'Idempotency-Key' = [guid]::NewGuid().ToString() }
Assert-That ($null -eq $manualSubmitted.triage.ruleUrgency) 'record-only symptom must not receive automatic urgency'
Assert-That ($manualSubmitted.triage.coverageStatus -eq 'NONE') 'record-only symptom coverage must be NONE'
Assert-That ($manualSubmitted.triage.assessmentStatus -eq 'REQUIRES_MANUAL_REVIEW') 'record-only symptom must require manual review'

$supplement = Invoke-Api Post "/api/v2/visits/$($manualVisit.id)/supplements" $patient.accessToken @{ content = '补充测试信息：今天下午有恶心' }
Assert-That ($supplement.content -eq '补充测试信息：今天下午有恶心') 'submitted visit must accept supplement'

$reviewed = Invoke-Api Post "/api/v1/triage-results/$($submitted.triage.id)/review" $clinician.accessToken @{
    decision = 'ACCEPT'; reason = '自动化测试：事实、规则和引用已核对'; finalUrgency = 'EMERGENCY'
}
Assert-That ($reviewed.status -eq 'REVIEWED') 'review must complete'
$plan = Invoke-Api Post '/api/v1/followup-plans' $clinician.accessToken @{ visitId = $visit.id; templateCode = 'GENERAL_FOLLOWUP_V1' }
$activePlan = Invoke-Api Post "/api/v1/followup-plans/$($plan.id)/activate" $clinician.accessToken $null
Assert-That ($activePlan.status -eq 'ACTIVE') 'follow-up plan must be active'
Assert-That ($activePlan.tasks.Count -eq 4) 'follow-up plan must create four tasks'

$staffTasks = @(Invoke-Api Get '/api/v1/followup-tasks/mine' $followup.accessToken $null)
$task = $staffTasks | Where-Object { $_.planId -eq $plan.id } | Select-Object -First 1
Assert-That ($null -ne $task) 'follow-up staff must receive a task'
$null = Invoke-Api Patch "/api/v1/followup-tasks/$($task.id)" $followup.accessToken @{ status = 'IN_PROGRESS'; resultSummary = '自动化测试处理中' }
$completed = Invoke-Api Patch "/api/v1/followup-tasks/$($task.id)" $followup.accessToken @{ status = 'COMPLETED'; resultSummary = '自动化测试已完成' }
Assert-That ($completed.status -eq 'COMPLETED') 'follow-up task must complete'

$patientTasks = @(Invoke-Api Get '/api/v1/followup-tasks/mine' $patient.accessToken $null)
$alerts = @(Invoke-Api Get '/api/v1/admin/safety-alerts' $admin.accessToken $null)
$audits = @(Invoke-Api Get '/api/v1/admin/audit-logs' $admin.accessToken $null)
$runs = @(Invoke-Api Get '/api/v1/admin/agent-runs' $admin.accessToken $null)
$guidelines = @(Invoke-Api Get '/api/v1/admin/guidelines' $admin.accessToken $null)
Assert-That (($patientTasks | Where-Object { $_.planId -eq $plan.id }).Count -eq 4) 'patient must see all plan tasks'
Assert-That ($audits.Count -gt 0) 'admin audit log must not be empty'
Assert-That ($runs.Count -gt 0) 'admin agent run list must not be empty'
Assert-That ($guidelines.Count -ge 2) 'admin must see active guideline metadata'
Assert-That (($guidelines | Measure-Object -Property chunkCount -Sum).Sum -ge 3) 'knowledge base must expose seeded chunks'

[pscustomobject]@{
    status = 'PASS'; visitId = $visit.id; manualReviewVisitId = $manualVisit.id; ruleUrgency = $submitted.triage.ruleUrgency
    aiRunStatus = $submitted.runs[0].status; citationCount = $submitted.runs[0].citations.Count
    planId = $plan.id; completedTaskId = $task.id
    patientTaskCount = $patientTasks.Count; alertCount = $alerts.Count; auditCount = $audits.Count; agentRunCount = $runs.Count
    agentTraceCount = $submitted.runs[0].agentTrace.Count; guidelineCount = $guidelines.Count
} | ConvertTo-Json -Depth 5
