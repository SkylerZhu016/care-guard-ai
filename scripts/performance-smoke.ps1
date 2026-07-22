[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:18088',
    [int]$Requests = 50,
    [int]$Concurrency = 10,
    [int]$P95ThresholdMs = 500
)

$ErrorActionPreference = 'Stop'
if ($Requests -lt 1 -or $Concurrency -lt 1) { throw 'Requests and Concurrency must be positive.' }
$login = Invoke-RestMethod -Method Post -Uri ($BaseUrl.TrimEnd('/') + '/api/v1/auth/login') -ContentType 'application/json' -Body '{"username":"patient","password":"Demo123!"}'

if (-not ('MedsimPerfRunner' -as [type])) {
    Add-Type -AssemblyName System.Net.Http
    $httpAssembly = [System.Net.Http.HttpClient].Assembly.Location
    Add-Type -Language CSharp -ReferencedAssemblies $httpAssembly -TypeDefinition @'
using System;
using System.Diagnostics;
using System.Linq;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Threading;
using System.Threading.Tasks;

public sealed class MedsimPerfSample {
    public long DurationMs { get; set; }
    public int StatusCode { get; set; }
    public bool Success { get; set; }
}

public static class MedsimPerfRunner {
    public static async Task<MedsimPerfSample[]> Run(string url, string token, int requests, int concurrency) {
        using (var client = new HttpClient())
        using (var gate = new SemaphoreSlim(concurrency)) {
            client.Timeout = TimeSpan.FromSeconds(10);
            client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token);
            var tasks = Enumerable.Range(0, requests).Select(async _ => {
                await gate.WaitAsync();
                var watch = Stopwatch.StartNew();
                try {
                    using (var response = await client.GetAsync(url)) {
                        watch.Stop();
                        return new MedsimPerfSample { DurationMs = watch.ElapsedMilliseconds, StatusCode = (int)response.StatusCode, Success = response.IsSuccessStatusCode };
                    }
                } catch {
                    watch.Stop();
                    return new MedsimPerfSample { DurationMs = watch.ElapsedMilliseconds, StatusCode = 0, Success = false };
                } finally { gate.Release(); }
            }).ToArray();
            return await Task.WhenAll(tasks);
        }
    }
}
'@
}

$target = $BaseUrl.TrimEnd('/') + '/api/v1/me'
$wall = [Diagnostics.Stopwatch]::StartNew()
$samples = [MedsimPerfRunner]::Run($target, $login.accessToken, $Requests, $Concurrency).GetAwaiter().GetResult()
$wall.Stop()
$durations = @($samples.DurationMs | Sort-Object)
$p50 = $durations[[Math]::Max(0, [Math]::Ceiling($durations.Count * 0.50) - 1)]
$p95 = $durations[[Math]::Max(0, [Math]::Ceiling($durations.Count * 0.95) - 1)]
$failures = @($samples | Where-Object { -not $_.Success }).Count
$result = [pscustomobject]@{
    status = if ($failures -eq 0 -and $p95 -le $P95ThresholdMs) { 'PASS' } else { 'FAIL' }
    requests = $Requests; concurrency = $Concurrency; failures = $failures
    p50Ms = $p50; p95Ms = $p95; maxMs = ($durations | Measure-Object -Maximum).Maximum
    wallTimeMs = $wall.ElapsedMilliseconds; thresholdP95Ms = $P95ThresholdMs
}
$result | ConvertTo-Json
if ($result.status -ne 'PASS') { throw "Performance smoke threshold failed: failures=$failures p95=${p95}ms" }
