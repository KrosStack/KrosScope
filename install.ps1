# Installs the kscope command-line tool from a KrosScope release on Windows, from PowerShell:
#
#     irm https://raw.githubusercontent.com/KrosStack/KrosScope/main/install.ps1 | iex
#
# It downloads the release archive, checks it against the release's SHA-256 sums, unpacks it into your user
# folder and adds its bin folder to your user PATH. With JDK 17 or newer present, it also runs `kscope skill
# install`, which teaches the coding agents it finds to use kscope. Running it again replaces the installed version.
# kscope needs JDK 17 or newer; the installer warns when it finds none.
#
# Settings, all optional, as environment variables set before running it:
#   KSCOPE_VERSION          version to install, such as 0.1.0 (default: the latest release)
#   KSCOPE_INSTALL_DIR      where the tool is unpacked (default: %LOCALAPPDATA%\Programs\kscope)
#   KSCOPE_NO_MODIFY_PATH   set to 1 to leave your user PATH alone
#   KSCOPE_NO_AGENT_SKILL   set to 1 to skip installing the skill for coding agents
#   KSCOPE_DOWNLOAD_URL     where the release files are (default: the GitHub release of the version)

& {
    $ErrorActionPreference = 'Stop'
    $ProgressPreference = 'SilentlyContinue'
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

    $repository = 'KrosStack/KrosScope'
    $marker = '.kscope-installation'
    $installDir = if ($env:KSCOPE_INSTALL_DIR) { $env:KSCOPE_INSTALL_DIR } else { Join-Path $env:LOCALAPPDATA 'Programs\kscope' }
    $installDir = [IO.Path]::GetFullPath($installDir)

    if ((Test-Path -LiteralPath $installDir) -and -not (Test-Path -LiteralPath (Join-Path $installDir $marker)) -and
        (Get-ChildItem -LiteralPath $installDir -Force | Select-Object -First 1)) {
        throw "kscope install: $installDir exists and was not made by this installer; set KSCOPE_INSTALL_DIR to another folder."
    }

    $parent = Split-Path -Parent $installDir
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
    $work = Join-Path $parent ('.kscope-install.' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $work | Out-Null
    try {
        $version = $env:KSCOPE_VERSION
        if (-not $version) {
            try {
                $latest = Invoke-RestMethod -UseBasicParsing -Uri "https://api.github.com/repos/$repository/releases/latest"
            } catch {
                throw "kscope install: cannot find the latest release of $repository ($($_.Exception.Message))"
            }
            $version = $latest.tag_name
            if (-not $version) { throw "kscope install: cannot read the latest release of $repository." }
        }
        $version = $version -replace '^v', ''

        $base = if ($env:KSCOPE_DOWNLOAD_URL) { $env:KSCOPE_DOWNLOAD_URL.TrimEnd('/') } else { "https://github.com/$repository/releases/download/v$version" }
        $archive = "kscope-$version.zip"
        $archivePath = Join-Path $work $archive
        $sumsPath = Join-Path $work 'SHA256SUMS'
        Write-Host "Downloading kscope $version"
        Invoke-WebRequest -UseBasicParsing -Uri "$base/$archive" -OutFile $archivePath
        Invoke-WebRequest -UseBasicParsing -Uri "$base/SHA256SUMS" -OutFile $sumsPath

        $expected = Get-Content -LiteralPath $sumsPath | ForEach-Object {
            $fields = $_.Trim() -split '\s+', 2
            if ($fields.Count -eq 2 -and $fields[1].TrimStart('*') -eq $archive) { $fields[0].ToLowerInvariant() }
        } | Select-Object -First 1
        if (-not $expected) { throw "kscope install: SHA256SUMS lists no sum for $archive." }
        $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $archivePath).Hash.ToLowerInvariant()
        if ($expected -ne $actual) { throw "kscope install: the SHA-256 sum of $archive does not match SHA256SUMS; nothing was installed." }

        Expand-Archive -LiteralPath $archivePath -DestinationPath $work
        $unpacked = Join-Path $work "kscope-$version"
        if (-not (Test-Path -LiteralPath (Join-Path $unpacked 'bin\kscope.bat'))) {
            throw "kscope install: $archive does not contain kscope-$version\bin\kscope.bat."
        }
        Set-Content -LiteralPath (Join-Path $unpacked $marker) -Value $version

        if (Test-Path -LiteralPath $installDir) { Remove-Item -LiteralPath $installDir -Recurse -Force }
        Move-Item -LiteralPath $unpacked -Destination $installDir
    } finally {
        Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
    }
    Write-Host "Installed kscope $version in $installDir"

    $bin = Join-Path $installDir 'bin'
    if ($env:KSCOPE_NO_MODIFY_PATH -eq '1') {
        Write-Host "Add $bin to your PATH to use kscope."
    } else {
        # The registry keeps unexpanded entries such as %USERPROFILE%\bin as they are.
        $key = [Microsoft.Win32.Registry]::CurrentUser.OpenSubKey('Environment', $true)
        try {
            $userPath = [string]$key.GetValue('Path', '', [Microsoft.Win32.RegistryValueOptions]::DoNotExpandEnvironmentNames)
            $entries = @($userPath -split ';' | Where-Object { $_ })
            if ($entries -notcontains $bin) {
                $key.SetValue('Path', (($entries + $bin) -join ';'), [Microsoft.Win32.RegistryValueKind]::ExpandString)
                # Setting and clearing a variable tells running programs, such as Explorer, that the environment changed.
                [Environment]::SetEnvironmentVariable('KSCOPE_INSTALL_REFRESH', '1', 'User')
                [Environment]::SetEnvironmentVariable('KSCOPE_INSTALL_REFRESH', $null, 'User')
                Write-Host "Added $bin to your user PATH. New terminals find kscope; this one does too."
            }
        } finally {
            $key.Close()
        }
        if (($env:Path -split ';') -notcontains $bin) { $env:Path = "$env:Path;$bin" }
    }

    $java = if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        Join-Path $env:JAVA_HOME 'bin\java.exe'
    } else {
        $command = Get-Command java -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($command) { $command.Source }
    }
    $major = $null
    if ($java) {
        $line = & {
            $ErrorActionPreference = 'Continue'
            & $java -version 2>&1 | ForEach-Object { "$_" } | Select-Object -First 1
        }
        if ($line -match 'version "(\d+)(?:\.(\d+))?') {
            $major = if ($Matches[1] -eq '1' -and $Matches[2]) { [int]$Matches[2] } else { [int]$Matches[1] }
        }
    }
    if ($null -eq $major) {
        Write-Warning 'kscope needs JDK 17 or newer, and no Java was found. Install one, for example from https://adoptium.net.'
    } elseif ($major -lt 17) {
        Write-Warning "kscope needs JDK 17 or newer, and the Java found is version $major."
    }

    if ($null -ne $major -and $major -ge 17) {
        if ($env:KSCOPE_NO_AGENT_SKILL -ne '1') {
            & (Join-Path $bin 'kscope.bat') skill install
            if ($LASTEXITCODE -ne 0) {
                Write-Warning "The kscope skill for coding agents was not installed; run 'kscope skill install' to try again."
            }
        }
        Write-Host "Run 'kscope doctor' in a project to check the setup."
    } elseif ($env:KSCOPE_NO_AGENT_SKILL -ne '1') {
        Write-Host "Then run 'kscope skill install' to teach your coding agents to use kscope."
    }
}
