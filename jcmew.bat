@echo off
rem Wrapper for the jcme CLI using the uber-jar produced by 'mvn package'.
rem Run from the project root: jcmew.bat pages https://company.atlassian.net/...

setlocal

set "JCMEW_DIR=%~dp0"
set "JAR="

rem Pick the first jcme-*.jar under target\ — `mvn package` only produces one
rem (the shaded uber-jar) since shadedArtifactAttached is false.
for %%f in ("%JCMEW_DIR%target\jcme-*.jar") do (
    if not defined JAR set "JAR=%%~ff"
)

if not defined JAR (
    echo Error: jcme jar not found under %JCMEW_DIR%target\. 1>&2
    echo Run 'mvn package' first to build it. 1>&2
    exit /b 1
)

java --enable-native-access=ALL-UNNAMED -jar "%JAR%" %*
exit /b %ERRORLEVEL%
