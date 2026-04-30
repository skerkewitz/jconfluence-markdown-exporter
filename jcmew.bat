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

rem Force UTF-8 in the JVM. cmd.exe console codepage may still need 'chcp 65001'
rem for umlauts to render correctly in the terminal — see README.
java --enable-native-access=ALL-UNNAMED ^
    -Dfile.encoding=UTF-8 ^
    -Dstdout.encoding=UTF-8 ^
    -Dstderr.encoding=UTF-8 ^
    -jar "%JAR%" %*
exit /b %ERRORLEVEL%
