param(
    [string]$JavaHome = ""
)

if (-not $JavaHome) {
    if ($env:JAVA_HOME) {
        $JavaHome = $env:JAVA_HOME
    } else {
        $detected = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            Select-Object -First 1

        if ($detected) {
            $JavaHome = $detected.FullName
        }
    }
}

if (-not $JavaHome) {
    throw "Java 21 JDK was not found. Install Temurin/OpenJDK 21 first."
}

$javaExe = Join-Path $JavaHome "bin\java.exe"
if (-not (Test-Path $javaExe)) {
    throw "Java executable was not found under '$JavaHome'."
}

$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;$env:Path"

Push-Location (Join-Path $PSScriptRoot "..\frontend")
try {
    if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) {
        throw "npm.cmd was not found. Install Node.js first."
    }

    if (-not (Test-Path ".\node_modules")) {
        & npm.cmd install
    }

    & npm.cmd run build
} finally {
    Pop-Location
}

Push-Location (Join-Path $PSScriptRoot "..\spring-backend")
try {
    .\mvnw.cmd spring-boot:run
} finally {
    Pop-Location
}
