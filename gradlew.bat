@ECHO OFF
@REM ##########################################################################
@REM
@REM  Gradle startup script for Windows
@REM
@REM ##########################################################################

SETLOCAL

SET DIRNAME=%~dp0
IF "%DIRNAME%" == "" SET DIRNAME=.
SET APP_BASE_NAME=%~n0
SET APP_HOME=%DIRNAME%

@REM Add default JVM options here if needed
set DEFAULT_JVM_OPTS=

IF NOT "%JAVA_HOME%" == "" (
  SET JAVA_EXE=%JAVA_HOME%/bin/java.exe
  IF NOT EXIST "%JAVA_EXE%" (
    ECHO ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
    EXIT /B 1
  )
) ELSE (
  SET JAVA_EXE=java.exe
  %JAVA_EXE% -version >NUL 2>&1
  IF NOT %ERRORLEVEL% == 0 (
    ECHO ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
    EXIT /B 1
  )
)

SET CLASSPATH=%APP_HOME%\\gradle\\wrapper\\gradle-wrapper.jar

"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

ENDLOCAL
