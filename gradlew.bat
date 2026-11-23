@rem AlpAI Gradle wrapper launcher (Windows).
@rem Falls back to a Gradle installation on PATH when the wrapper jar
@rem is not present; see gradlew for the full explanation.
@echo off
set APP_HOME=%~dp0
if exist "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" (
  java -jar "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" %*
  exit /b %ERRORLEVEL%
)
where gradle >nul 2>nul
if %ERRORLEVEL% equ 0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
echo Gradle not found. Install Gradle 8.7 or run: gradle wrapper --gradle-version 8.7
exit /b 1
