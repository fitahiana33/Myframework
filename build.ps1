$ErrorActionPreference = 'Stop'

# ==========================
# CONFIGURATION
# ==========================
$PROJECT_NAME = 'MonFrameworkTest'
$PROJECT_DIR  = 'D:\BOSSY\cours\L3\S5\Naina\framework'
$SRC_DIR      = Join-Path $PROJECT_DIR 'src'
$TEST_DIR     = Join-Path $PROJECT_DIR 'test'
$BUILD_DIR    = Join-Path $TEST_DIR 'build'
$WAR_NAME     = "$PROJECT_NAME.war"
$TOMCAT_DIR   = 'D:\BOSSY\cours\L3\S5\Naina\apache-tomcat-10.1.28'
$TOMCAT_WEBAPPS = Join-Path $TOMCAT_DIR 'webapps'
$TMP_CLASSES  = Join-Path $BUILD_DIR 'tmp-classes'
$FRAMEWORK_JAR_NAME = 'framework.jar'
$SOURCES_LIST = Join-Path $BUILD_DIR 'sources.txt'

Write-Host '[1/6] Cleaning previous build...'
if (Test-Path $BUILD_DIR) { Remove-Item -Recurse -Force $BUILD_DIR }
New-Item -ItemType Directory -Force -Path (Join-Path $BUILD_DIR 'WEB-INF') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $BUILD_DIR 'WEB-INF\lib') | Out-Null

Write-Host '[2/6] Copying web test files (excluding build folder)...'
# Use robocopy to avoid copying build into itself
$rc = (Start-Process -FilePath 'robocopy' -ArgumentList @("$TEST_DIR","$BUILD_DIR","/E","/XD","build") -Wait -PassThru).ExitCode
if ($rc -ge 8) { throw "Copy failed (robocopy RC=$rc)" }

Write-Host '[3/6] Preparing sources list...'
if (Test-Path $SOURCES_LIST) { Remove-Item $SOURCES_LIST -Force }
$javaFiles = Get-ChildItem -Recurse -File -Path $SRC_DIR -Filter *.java
if (-not $javaFiles) { throw "No Java sources found under $SRC_DIR" }
$javaFiles | ForEach-Object { $_.FullName } | Set-Content -Encoding ascii $SOURCES_LIST

Write-Host '[3/6] Compiling framework Java code...'
New-Item -ItemType Directory -Force -Path $TMP_CLASSES | Out-Null
$files = Get-Content -Encoding ascii $SOURCES_LIST
& javac -cp (Join-Path $TOMCAT_DIR 'lib\*') -d $TMP_CLASSES $files

Write-Host '[4/6] Building framework JAR...'
# jar -cvf BUILD/WEB-INF/lib/framework.jar -C TMP_CLASSES .
& jar -cvf (Join-Path $BUILD_DIR "WEB-INF\lib\$FRAMEWORK_JAR_NAME") -C $TMP_CLASSES . | Out-Null
Remove-Item -Recurse -Force $TMP_CLASSES

Write-Host '[5/6] Creating WAR...'
Push-Location $BUILD_DIR
& jar -cvf $WAR_NAME * | Out-Null
Pop-Location

Write-Host '[6/6] Deploying to Tomcat...'
Move-Item -Force (Join-Path $BUILD_DIR $WAR_NAME) (Join-Path $TOMCAT_WEBAPPS $WAR_NAME)

Write-Host '=========================================='
Write-Host 'Build and deploy completed successfully.'
Write-Host "Open: http://localhost:8080/$PROJECT_NAME/"
Write-Host '==========================================' 