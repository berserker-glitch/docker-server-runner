@echo off
echo Starting Docker Project Manager...
echo.
echo Making sure Maven is available...
call mvn --version
if %errorlevel% neq 0 (
    echo ERROR: Maven is not installed or not in PATH
    echo Please install Maven from https://maven.apache.org/
    pause
    exit /b 1
)

echo.
echo Running application...
call mvn clean javafx:run

pause

