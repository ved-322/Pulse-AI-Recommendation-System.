@echo off
rem Builds the backend (if needed) and starts the app on http://localhost:8080
cd /d "%~dp0"

rem --- locate a JDK (JAVA_HOME, else newest under Program Files\Java) ---
if not defined JAVA_HOME (
  for /d %%d in ("C:\Program Files\Java\jdk*") do set "JAVA_HOME=%%~d"
)
if not defined JAVA_HOME (
  echo JDK 17+ not found. Install it and set JAVA_HOME, then run this again.
  exit /b 1
)

rem --- use the bundled Maven (tools\) or fall back to one on PATH ---
set "MVN=%~dp0tools\apache-maven-3.9.9\bin\mvn.cmd"
if not exist "%MVN%" set "MVN=mvn"

if not exist backend\target\recsys.jar (
  echo Building...
  call "%MVN%" -q -f backend\pom.xml package -DskipTests
  if errorlevel 1 exit /b 1
)
"%JAVA_HOME%\bin\java.exe" -jar backend\target\recsys.jar
