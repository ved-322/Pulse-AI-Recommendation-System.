@echo off
rem Runs the JUnit test-suite
cd /d "%~dp0"
if not defined JAVA_HOME (
  for /d %%d in ("C:\Program Files\Java\jdk*") do set "JAVA_HOME=%%~d"
)
set "MVN=%~dp0tools\apache-maven-3.9.9\bin\mvn.cmd"
if not exist "%MVN%" set "MVN=mvn"
call "%MVN%" -f backend\pom.xml test
