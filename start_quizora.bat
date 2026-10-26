@echo off
echo Starting MySQL Database on port 3306...
start "" "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqld.exe" --datadir="%~dp0mysql_data" --port=3306

timeout /t 3 /nobreak >nul

echo Starting Spring Boot Backend on port 8080...
cd /d "%~dp0backend"
start "Quizora Backend" cmd /k "mvn spring-boot:run"

timeout /t 5 /nobreak >nul

echo Starting Frontend on port 5173...
cd /d "%~dp0frontend"
start "Quizora Frontend" cmd /k "npm run dev"

echo.
echo All services launched!
echo - Frontend: http://localhost:5173/
echo - Backend:  http://localhost:8080/
pause
