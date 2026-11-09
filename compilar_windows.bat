@echo off
setlocal
cd /d "%~dp0"
echo =============================================
echo    Waybridge - Compilar en Windows
 echo =============================================
where javac >nul 2>nul
if errorlevel 1 (
  echo [ERROR] No se encuentra javac. Instala el JDK 17 y reinicia VS Code.
  exit /b 1
)
echo [INFO] JDK detectado:
javac -version
if not exist ".herramientas\gradle-8.9\bin\gradle.bat" (
  echo [INFO] Descargando Gradle 8.9... Solo la primera vez.
  if not exist ".herramientas" mkdir ".herramientas"
  powershell -NoProfile -Command "$ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.9-bin.zip' -OutFile '.herramientas/gradle-8.9-bin.zip'; Expand-Archive -Path '.herramientas/gradle-8.9-bin.zip' -DestinationPath '.herramientas' -Force"
  if errorlevel 1 (
    echo [ERROR] No se pudo descargar/extraer Gradle. Comprueba Internet.
    exit /b 1
  )
)
if not exist "gradlew.bat" (
  echo [INFO] Generando Gradle Wrapper para VS Code...
  call ".herramientas\gradle-8.9\bin\gradle.bat" --no-daemon wrapper --gradle-version 8.9
  if errorlevel 1 (
    echo [ERROR] No se pudo generar Gradle Wrapper.
    exit /b 1
  )
)
echo [INFO] Compilando el mod...
call "gradlew.bat" --no-daemon clean build
if errorlevel 1 (
  echo [ERROR] Fallo la compilacion. Copia el primer error desde la terminal.
  exit /b 1
)
echo.
echo [OK] Jar para Minecraft: build\libs\waybridge-1.0.0.jar
echo [AVISO] No usar el -sources.jar ni el -dev.jar
endlocal
