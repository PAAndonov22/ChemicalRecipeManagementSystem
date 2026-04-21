param(
    [string]$BaseUrl = "http://localhost:8080"
)

$loginBody = @{
    email = "admin@crms.local"
    password = "Admin123!"
} | ConvertTo-Json

Write-Host "Logging in as admin..."
$login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" -ContentType "application/json" -Body $loginBody

if (-not $login.token) {
    throw "Login did not return a token."
}

$headers = @{
    Authorization = "Bearer $($login.token)"
}

Write-Host "Checking current user..."
$me = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/auth/me" -Headers $headers
if ($me.user.roleName -ne "Admin") {
    throw "Expected Admin role from /api/auth/me."
}

Write-Host "Checking recipes..."
$recipes = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/recipes" -Headers $headers
if ($null -eq $recipes.items) {
    throw "Recipe listing failed."
}

Write-Host "Checking reports..."
$reports = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/reports/summary" -Headers $headers
if ($null -eq $reports.overview) {
    throw "Reports endpoint failed."
}

Write-Host "Checking audit logs..."
$audit = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/audit-logs?limit=5" -Headers $headers
if ($null -eq $audit.items) {
    throw "Audit log endpoint failed."
}

Write-Host "Smoke test completed successfully."
