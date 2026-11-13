@echo off
setlocal
set GRADLE_VERSION=9.6.0
set GRADLE_HOME=%USERPROFILE%\.gradle\focus-ai-bootstrap\gradle-%GRADLE_VERSION%
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  echo [MyAITutor] Gradle %GRADLE_VERSION% is not installed. Downloading it now...
  set ZIP_FILE=%TEMP%\gradle-%GRADLE_VERSION%-bin.zip
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%TEMP%\gradle-%GRADLE_VERSION%-bin.zip'; New-Item -ItemType Directory -Force -Path '%USERPROFILE%\.gradle\focus-ai-bootstrap' | Out-Null; Expand-Archive -Path '%TEMP%\gradle-%GRADLE_VERSION%-bin.zip' -DestinationPath '%USERPROFILE%\.gradle\focus-ai-bootstrap' -Force"
  if errorlevel 1 exit /b 1
)
call "%GRADLE_HOME%\bin\gradle.bat" -p "%~dp0." %*
exit /b %ERRORLEVEL%
