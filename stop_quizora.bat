@echo off
echo Stopping Quizora services...

for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8080" ^| findstr "LISTENING"') do (
    taskkill /F /PID %%a 2>nul
)

for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":5173" ^| findstr "LISTENING"') do (
    taskkill /F /PID %%a 2>nul
)

if exist "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqladmin.exe" (
    "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqladmin.exe" -u root --port=3306 shutdown 2>nul
    timeout /t 2 /nobreak >nul
)
taskkill /IM mysqld.exe 2>nul

echo All Quizora services stopped.
pause
