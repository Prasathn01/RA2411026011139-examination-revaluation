@echo off
rem ============================================================
rem  Stops the Camunda 8 Run demo started by start-demo.bat.
rem  Note: this closes ALL node.exe, java.exe-on-port-8081 and mailpit.exe processes.
rem ============================================================

if "%C8RUN%"=="" set C8RUN=C:\camunda\c8run-8.10.6

rem Backend: the java process listening on port 8081
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8081" ^| findstr "LISTENING"') do taskkill /PID %%p /F >nul 2>&1
echo Backend stopped.

taskkill /IM node.exe /F >nul 2>&1
echo Mock services and frontend stopped.

taskkill /IM mailpit.exe /F >nul 2>&1
echo Mailpit stopped.

pushd "%C8RUN%"
"%C8RUN%\c8run.exe" stop
popd
echo Camunda stopped. (The four windows stay open showing a prompt: close them.)
