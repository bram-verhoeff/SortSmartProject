@echo off
setlocal
set "JAVA_HOME=C:\Users\bramv\AppData\Local\Programs\jdk-21-x64\jdk-21.0.13.1+2"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0sortsmart-javafx"
echo ========================================================
echo  SortSmart - Sorteerstation Beheerpaneel
echo  Java: %JAVA_HOME%
echo ========================================================
echo.
call .\mvnw.cmd javafx:run
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo SortSmart is gestopt met een foutcode (%ERRORLEVEL%).
    pause
)
