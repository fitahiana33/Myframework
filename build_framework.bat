@echo off
setlocal enabledelayedexpansion

:: === Définir le chemin du dossier MyFramework ===
set "PROJECT_DIR=%~dp0"
cd /d "%PROJECT_DIR%"

:: === Dossiers du framework ===
set "SRC_DIR=%PROJECT_DIR%src"
set "BUILD_DIR=%PROJECT_DIR%MyFramework\build"
set "DIST_DIR=%PROJECT_DIR%MyFramework\dist"
set "JAR_NAME=FrontServlet.jar"

:: === JAR de Jakarta Servlet API (depuis Tomcat) ===
set "SERVLET_JAR=D:\BOSSY\cours\L3\S5\Mr_Naina\apache-tomcat-10.1.28\lib\servlet-api.jar"

:: === Dossier logs ===
set "LOG_DIR=%~dp0logs"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
set "DATE_STR=%date:~-4%-%date:~3,2%-%date:~0,2%"
set "TIME_STR=%time:~0,2%-%time:~3,2%"
set "LOG_FILE=%LOG_DIR%\build_%DATE_STR%_%TIME_STR%.log"

echo 🚀 Compilation du framework...
if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"
if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"

:: Compilation dans le bon ordre avec encodage UTF-8
echo Compilation des annotations... >> "%LOG_FILE%"
javac -encoding UTF-8 -d "%BUILD_DIR%" "%SRC_DIR%\com\nandrianina\framework\annotation\*.java" >> "%LOG_FILE%" 2>&1
if errorlevel 1 goto :error

echo Compilation des classes utilitaires... >> "%LOG_FILE%"
javac -encoding UTF-8 -d "%BUILD_DIR%" -cp "%BUILD_DIR%" "%SRC_DIR%\com\nandrianina\framework\mapping\*.java" >> "%LOG_FILE%" 2>&1
if errorlevel 1 goto :error

javac -encoding UTF-8 -d "%BUILD_DIR%" -cp "%BUILD_DIR%" "%SRC_DIR%\com\nandrianina\framework\util\*.java" >> "%LOG_FILE%" 2>&1
if errorlevel 1 goto :error

echo Compilation du FrontServlet... >> "%LOG_FILE%"
javac -encoding UTF-8 -d "%BUILD_DIR%" -cp "%BUILD_DIR%;%SERVLET_JAR%" "%SRC_DIR%\com\nandrianina\framework\FrontServlet.java" >> "%LOG_FILE%" 2>&1
if errorlevel 1 goto :error

echo 📦 Création du JAR...
jar cf "%DIST_DIR%\%JAR_NAME%" -C "%BUILD_DIR%" . >> "%LOG_FILE%" 2>&1
if errorlevel 1 goto :error

echo ✅ Framework compilé avec succès : "%DIST_DIR%\%JAR_NAME%"
echo 📜 Log : "%LOG_FILE%"
endlocal
pause
exit /b 0

:error
echo ❌ Erreur de compilation. Voir "%LOG_FILE%"
endlocal
pause
exit /b 1
