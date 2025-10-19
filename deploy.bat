@echo off
setlocal enabledelayedexpansion

:: Log pour tracer
set LOG_FILE=%~dp0deploy-log.txt
echo ETAPE Debut > "%LOG_FILE%"

:: ==========================
:: CONFIGURATION - ADAPTE ICI
:: ==========================
set PROJECT_NAME=MyFramework
set PROJECT_DIR=D:\BOSSY\cours\L3\S5\Mr_Naina\MyFramework
set SRC_DIR=%PROJECT_DIR%\src
set TEST_DIR=%PROJECT_DIR%\test
set BUILD_DIR=%PROJECT_DIR%\build
set WAR_NAME=%PROJECT_NAME%.war
set TOMCAT_DIR=D:\BOSSY\cours\L3\S5\Mr_Naina\apache-tomcat-10.1.28
set TOMCAT_WEBAPPS=%TOMCAT_DIR%\webapps
set SERVLET_API_JAR=%TOMCAT_DIR%\lib\servlet-api.jar
set FRAMEWORK_JAR_NAME=framework.jar

:: Écrit config dans log
echo ETAPE Config OK >> "%LOG_FILE%"
echo PROJECT_DIR: %PROJECT_DIR% >> "%LOG_FILE%"
echo SRC_DIR: %SRC_DIR% >> "%LOG_FILE%"
echo TEST_DIR: %TEST_DIR% >> "%LOG_FILE%"
echo BUILD_DIR: %BUILD_DIR% >> "%LOG_FILE%"
echo TOMCAT_DIR: %TOMCAT_DIR% >> "%LOG_FILE%"
echo SERVLET_API_JAR: %SERVLET_API_JAR% >> "%LOG_FILE%"

pause  REM Pause pour voir config

:: Vérifications avec goto
if not exist "%SRC_DIR%" goto SRC_ERROR
echo ETAPE SRC OK >> "%LOG_FILE%"
pause
goto NEXT1

:SRC_ERROR
echo ETAPE ERREUR SRC >> "%LOG_FILE%"
echo ERREUR: SRC_DIR (%SRC_DIR%) non trouve.
pause
goto END

:NEXT1
if not exist "%TEST_DIR%" goto TEST_ERROR
echo ETAPE TEST OK >> "%LOG_FILE%"
pause
goto NEXT2

:TEST_ERROR
echo ETAPE ERREUR TEST >> "%LOG_FILE%"
echo ERREUR: TEST_DIR (%TEST_DIR%) non trouve.
pause
goto END

:NEXT2
if not exist "%TOMCAT_DIR%" goto TOMCAT_ERROR
echo ETAPE TOMCAT OK >> "%LOG_FILE%"
pause
goto NEXT3

:TOMCAT_ERROR
echo ETAPE ERREUR TOMCAT >> "%LOG_FILE%"
echo ERREUR: TOMCAT_DIR (%TOMCAT_DIR%) non trouve.
pause
goto END

:NEXT3
if not exist "%SERVLET_API_JAR%" goto JAR_ERROR
echo ETAPE JAR OK >> "%LOG_FILE%"
pause
goto BUILD_START

:JAR_ERROR
echo ETAPE ERREUR JAR >> "%LOG_FILE%"
echo ERREUR: SERVLET_API_JAR (%SERVLET_API_JAR%) non trouve.
pause
goto END

:BUILD_START
echo ==========================================
echo Tous OK ! Debut build, compilation, JAR, deploiement.
echo ========================================== >> "%LOG_FILE%"

:: [1] Nettoyage global (build, WAR, Tomcat app, cache)
echo [1] Nettoyage...
echo ETAPE Nettoyage >> "%LOG_FILE%"
if exist "%BUILD_DIR%" rd /s /q "%BUILD_DIR%"
mkdir "%BUILD_DIR%\WEB-INF\classes"
mkdir "%BUILD_DIR%\WEB-INF\lib"

cd /d "%TOMCAT_DIR%\bin"
call shutdown.bat
timeout /t 2 >nul

if exist "%TOMCAT_WEBAPPS%\%WAR_NAME%" del /f /q "%TOMCAT_WEBAPPS%\%WAR_NAME%"
if exist "%TOMCAT_WEBAPPS%\%PROJECT_NAME%" rd /s /q "%TOMCAT_WEBAPPS%\%PROJECT_NAME%"
if exist "%TOMCAT_DIR%\work\Catalina\localhost\%PROJECT_NAME%" rd /s /q "%TOMCAT_DIR%\work\Catalina\localhost\%PROJECT_NAME%"
echo ETAPE Nettoyage OK >> "%LOG_FILE%"
pause

:: [2] Compilation du code source
echo [2] Compilation...
echo ETAPE Compilation >> "%LOG_FILE%"
dir /s /b "%SRC_DIR%\*.java" > "%BUILD_DIR%\sources.txt"
if errorlevel 1 goto COMP_ERROR
javac -cp "%SERVLET_API_JAR%" -d "%BUILD_DIR%\WEB-INF\classes" @"%BUILD_DIR%\sources.txt"
if errorlevel 1 goto COMP_ERROR
del "%BUILD_DIR%\sources.txt"
echo ETAPE Compilation OK >> "%LOG_FILE%"
pause
goto JAR_START

:COMP_ERROR
echo ETAPE ERREUR COMP >> "%LOG_FILE%"
echo ERREUR: Compilation echouee. Verifiez imports (javax.servlet pour Tomcat 9).
pause
goto END

:JAR_START
:: [3] Creation du .jar
echo [3] Creation JAR...
echo ETAPE JAR Creation >> "%LOG_FILE%"
cd /d "%BUILD_DIR%\WEB-INF\classes"
jar cvf "%BUILD_DIR%\WEB-INF\lib\%FRAMEWORK_JAR_NAME%" *
cd /d "%PROJECT_DIR%"
echo ETAPE JAR OK >> "%LOG_FILE%"
pause
goto COPY_START

:COPY_START
:: [4] Copie des fichiers test vers build (webapp pour WAR)
echo [4] Copie test vers build...
echo ETAPE Copie >> "%LOG_FILE%"
xcopy "%TEST_DIR%\*" "%BUILD_DIR%" /E /I /Y /Q
if errorlevel 1 goto COPY_ERROR
:: Copie le JAR dans lib si pas deja
copy "%BUILD_DIR%\WEB-INF\lib\%FRAMEWORK_JAR_NAME%" "%BUILD_DIR%\WEB-INF\lib\%FRAMEWORK_JAR_NAME%" >nul
:: Force timestamp JSP
for /r "%BUILD_DIR%" %%f in (*.jsp) do copy /b "%%f"+,, "%%f"
echo ETAPE Copie OK >> "%LOG_FILE%"
pause
goto WAR_START

:COPY_ERROR
echo ETAPE ERREUR COPY >> "%LOG_FILE%"
echo ERREUR: Copie echouee.
pause
goto END

:WAR_START
:: [5] Creation WAR
echo [5] Creation WAR...
echo ETAPE WAR >> "%LOG_FILE%"
cd /d "%BUILD_DIR%"
jar -cvf "%WAR_NAME%" *
cd /d "%PROJECT_DIR%"
echo ETAPE WAR OK >> "%LOG_FILE%"
pause
goto DEPLOY_START

:DEPLOY_START
:: [6] Deploiement dans Tomcat
echo [6] Deploiement...
echo ETAPE Deploiement >> "%LOG_FILE%"
move "%BUILD_DIR%\%WAR_NAME%" "%TOMCAT_WEBAPPS%\%WAR_NAME%"
if errorlevel 1 goto DEPLOY_ERROR
echo ETAPE Deploiement OK >> "%LOG_FILE%"

cd /d "%TOMCAT_DIR%\bin"
call startup.bat
timeout /t 5 >nul

echo ==========================================
echo Succes ! Compilation, JAR, deploiement faits.
echo URL: http://localhost:8080/%PROJECT_NAME%/
echo Log: %LOG_FILE%
echo ========================================== >> "%LOG_FILE%"
pause
goto END

:DEPLOY_ERROR
echo ETAPE ERREUR DEPLOY >> "%LOG_FILE%"
echo ERREUR: Deploiement echoue.
pause
goto END

:END