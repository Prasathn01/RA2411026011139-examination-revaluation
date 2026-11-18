@echo off
rem ============================================================
rem  Starts the whole system WITHOUT Docker (Camunda 8 Run mode).
rem  Double-click it, or in PowerShell:  .\start-demo.bat
rem  Four windows open (Mailpit, mock services, backend, frontend): leave them open.
rem
rem  Needs: Camunda 8 Run 8.10.6 in %C8RUN%, Java 21, Maven, Node.js, and Mailpit
rem  (install once with:  winget install -e --id axllent.mailpit)
rem ============================================================

if "%C8RUN%"=="" set C8RUN=C:\camunda\c8run-8.10.6
cd /d "%~dp0"

if not exist secrets\secrets.env (
  echo Creating secrets\secrets.env from the example file
  copy secrets\secrets.example.env secrets\secrets.env >nul
)

echo [1/5] Camunda 8 Run
curl.exe -s -o nul http://localhost:8080/v2/topology
if %errorlevel%==0 (
  echo       already running
) else (
  pushd "%C8RUN%"
  "%C8RUN%\c8run.exe" start --no-browser
  popd
)
rem Connector secrets (RESULTS_API_URL, SMTP_HOST, ...) used by the REST and Email connectors
"%C8RUN%\c8run.exe" secrets import "%~dp0secrets\secrets.env"

echo [2/5] Window: Mailpit test inbox (http://localhost:8025)
rem winget does not always put mailpit on PATH, so fall back to its install folder
set MAILPIT=mailpit
where mailpit >nul 2>&1 || for /d %%d in ("%LOCALAPPDATA%\Microsoft\WinGet\Packages\axllent.mailpit_*") do set MAILPIT="%%d\mailpit.exe"
start "Mailpit (leave open)" cmd /k %MAILPIT% --smtp-auth-accept-any --smtp-auth-allow-insecure

echo [3/5] Window: mock external systems (http://localhost:3000)
start "Mock services (leave open)" cmd /k node mock-services\mock-services.js

echo [4/5] Window: Spring Boot backend (http://localhost:8081) - first start downloads dependencies
start "Backend (leave open)" cmd /k "cd backend-springboot && mvn -B -q spring-boot:run"

echo [5/5] Window: React portal (http://localhost:5173)
if not exist frontend-react\node_modules (
  start "Frontend (leave open)" cmd /k "cd frontend-react && npm install && npm run dev"
) else (
  start "Frontend (leave open)" cmd /k "cd frontend-react && npm run dev"
)

echo.
echo ============================================================
echo  Starting. The backend needs about 30 seconds.
echo    Portal     http://localhost:5173
echo    Operate    http://localhost:8080/operate   (demo / demo)
echo    Mailpit    http://localhost:8025
echo    Mock       http://localhost:3000
echo  Stop everything with:  .\stop-demo.bat
echo ============================================================
