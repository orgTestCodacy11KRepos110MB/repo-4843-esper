@echo off

if "%JAVA_HOME%" == "" (
  echo.
  echo JAVA_HOME not set
  goto EOF
)

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo.
  echo Cannot find java executable, check JAVA_HOME
  goto EOF
)

set LIB_COMPILER=..\..\..\dependencies\compiler

set CLASSPATH=.
set CLASSPATH=%CLASSPATH%;..\target\classes
set CLASSPATH=%CLASSPATH%;..\..\..\esper-common-8.8.0.jar
set CLASSPATH=%CLASSPATH%;..\..\..\esper-compiler-8.8.0.jar
set CLASSPATH=%CLASSPATH%;..\..\..\esper-runtime-8.8.0.jar
set CLASSPATH=%CLASSPATH%;%LIB_COMPILER%\slf4j-api-1.7.30.jar
set CLASSPATH=%CLASSPATH%;%LIB_COMPILER%\slf4j-log4j12-1.7.30.jar
set CLASSPATH=%CLASSPATH%;%LIB_COMPILER%\log4j-1.2.17.jar
set CLASSPATH=%CLASSPATH%;%LIB_COMPILER%\antlr4-runtime-4.9.3.jar
set CLASSPATH=%CLASSPATH%;%LIB_COMPILER%\commons-compiler-3.1.6.jar
set CLASSPATH=%CLASSPATH%;%LIB_COMPILER%\janino-3.1.6.jar

:EOF
