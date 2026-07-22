[CmdletBinding()]
param([string]$BaseUrl = 'http://localhost:18088')

$ErrorActionPreference = 'Stop'
$apiRoot = $BaseUrl.TrimEnd('/') + '/api/v1'

function Invoke-Api {
    param([string]$Method, [string]$Path, [string]$Token, $Body, [hashtable]$ExtraHeaders = @{})
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    foreach ($key in $ExtraHeaders.Keys) { $headers[$key] = $ExtraHeaders[$key] }
    $args = @{ Method = $Method; Uri = "$apiRoot$Path"; Headers = $headers; TimeoutSec = 70 }
    if ($null -ne $Body) { $args.ContentType = 'application/json'; $args.Body = ($Body | ConvertTo-Json -Depth 10 -Compress) }
    try {
        $result = Invoke-RestMethod @args
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
    Invoke-Api -Method Post -Path '/auth/login' -Token '' -Body @{ username = $Username; password = 'Demo123!' }
}

function Assert-That([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Smoke assertion failed: $Message" }
}

$patient = Login 'patient'
$clinician = Login 'clinician'
$followup = Login 'followup'
$admin = Login 'admin'

$visitBody = @{
    chiefComplaint = 'Synthetic case: chest pain with dyspnea'
    freeText = 'Automated engineering smoke test using synthetic data only.'
    symptoms = @(
        @{ code = 'CHEST_PAIN'; name = 'Chest pain'; severity = 8; onset = '2 hours' },
        @{ code = 'DYSPNEA'; name = 'Dyspnea'; severity = 7; onset = '2 hours' }
    )
}
$visit = Invoke-Api Post '/visits' $patient.accessToken $visitBody
$submitted = Invoke-Api Post "/visits/$($visit.id)/submit" $patient.accessToken $null @{ 'Idempotency-Key' = [guid]::NewGuid().ToString() }
Assert-That ($submitted.status -eq 'PENDING_REVIEW') 'visit must enter PENDING_REVIEW'
Assert-That ($submitted.triage.ruleUrgency -eq 'EMERGENCY') 'red-flag rules must return EMERGENCY'
Assert-That ($submitted.runs.Count -ge 1) 'AI run must be recorded even when degraded or blocked'
Assert-That ($submitted.runs[0].status -eq 'SUCCEEDED') 'AI run must succeed in the complete Docker environment'
Assert-That ($submitted.runs[0].citations.Count -ge 1) 'successful AI run must include at least one citation'
Assert-That (-not [string]::IsNullOrWhiteSpace($submitted.runs[0].citations[0].chunkId)) 'citation chunkId must be non-empty'
Assert-That (-not [string]::IsNullOrWhiteSpace($submitted.runs[0].citations[0].quote)) 'citation quote must be non-empty'
Assert-That (-not [string]::IsNullOrWhiteSpace($submitted.runs[0].citations[0].sourceUrl)) 'citation source URL must be non-empty'
Assert-That ($submitted.runs[0].agentTrace.Count -eq 5) 'multi-agent trace must contain five observable roles'

$reviewed = Invoke-Api Post "/triage-results/$($submitted.triage.id)/review" $clinician.accessToken @{
    decision = 'ACCEPT'; reason = 'Automated test: deterministic rules and teaching citations checked'; finalUrgency = 'EMERGENCY'
}
Assert-That ($reviewed.status -eq 'REVIEWED') 'review must complete'
$plan = Invoke-Api Post '/followup-plans' $clinician.accessToken @{ visitId = $visit.id; templateCode = 'HYPERTENSION_TEACHING_V1' }
$activePlan = Invoke-Api Post "/followup-plans/$($plan.id)/activate" $clinician.accessToken $null
Assert-That ($activePlan.status -eq 'ACTIVE') 'follow-up plan must be active'
Assert-That ($activePlan.tasks.Count -eq 4) 'teaching plan must create four tasks'

$staffTasks = @(Invoke-Api Get '/followup-tasks/mine' $followup.accessToken $null)
$task = $staffTasks | Where-Object { $_.planId -eq $plan.id } | Select-Object -First 1
Assert-That ($null -ne $task) 'follow-up staff must receive a task'
$null = Invoke-Api Patch "/followup-tasks/$($task.id)" $followup.accessToken @{ status = 'IN_PROGRESS'; resultSummary = 'Automated test in progress' }
$completed = Invoke-Api Patch "/followup-tasks/$($task.id)" $followup.accessToken @{ status = 'COMPLETED'; resultSummary = 'Automated test completed' }
Assert-That ($completed.status -eq 'COMPLETED') 'follow-up task must complete'

$patientTasks = @(Invoke-Api Get '/followup-tasks/mine' $patient.accessToken $null)
$alerts = @(Invoke-Api Get '/admin/safety-alerts' $admin.accessToken $null)
$audits = @(Invoke-Api Get '/admin/audit-logs' $admin.accessToken $null)
$runs = @(Invoke-Api Get '/admin/agent-runs' $admin.accessToken $null)
$guidelines = @(Invoke-Api Get '/admin/guidelines' $admin.accessToken $null)
Assert-That (($patientTasks | Where-Object { $_.planId -eq $plan.id }).Count -eq 4) 'patient must see all plan tasks'
Assert-That ($audits.Count -gt 0) 'admin audit log must not be empty'
Assert-That ($runs.Count -gt 0) 'admin agent run list must not be empty'
Assert-That ($guidelines.Count -ge 2) 'admin must see active guideline metadata'
Assert-That (($guidelines | Measure-Object -Property chunkCount -Sum).Sum -ge 3) 'knowledge base must expose seeded chunks'

[pscustomobject]@{
    status = 'PASS'; visitId = $visit.id; ruleUrgency = $submitted.triage.ruleUrgency
    aiRunStatus = $submitted.runs[0].status; citationCount = $submitted.runs[0].citations.Count
    planId = $plan.id; completedTaskId = $task.id
    patientTaskCount = $patientTasks.Count; alertCount = $alerts.Count; auditCount = $audits.Count; agentRunCount = $runs.Count
    agentTraceCount = $submitted.runs[0].agentTrace.Count; guidelineCount = $guidelines.Count
} | ConvertTo-Json -Depth 5
