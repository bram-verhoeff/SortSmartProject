@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Maven Start Up Batch script
@REM
@REM Required ENV vars:
@REM JAVA_HOME - location of a JDK home dir
@REM
@REM Optional ENV vars
@REM M2_HOME - location of maven2's installed home dir
@REM MAVEN_BATCH_ECHO - set to 'on' to enable batch command echoing
@REM MAVEN_BATCH_PAUSE - set to 'on' to pause the batch file before exit
@REM MAVEN_OPTS - parameters passed to the Java VM when running Maven
@REM     e.g. to debug Maven itself, use
@REM       set MAVEN_OPTS=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000
@REM MAVEN_SKIP_RC - flag to disable loading of mavenrc files
@REM ----------------------------------------------------------------------------

@IF "%__MAVEN_CMD_ECHO__%" == "on" echo on

@REM Enable extensions
@SETLOCAL

@IF "%MAVEN_BATCH_ECHO%" == "on"  echo on

@REM set %HOME% to equivalent of $HOME
@IF "%HOME%" == "" (SET "HOME=%HOMEDRIVE%%HOMEPATH%")

@SET MAVEN_PROJECTBASEDIR=%MAVEN_BASEDIR%
@IF NOT "%MAVEN_PROJECTBASEDIR%"=="" goto endDetectBaseDir

@SET EXEC_DIR=%CD%
@SET WDIR=%EXEC_DIR%
:findBaseDir
@IF EXIST "%WDIR%"\.mvn goto baseDirFound
@cd ..
@IF "%WDIR%"=="%CD%" goto baseDirNotFound
@SET WDIR=%CD%
@goto findBaseDir

:baseDirFound
@SET MAVEN_PROJECTBASEDIR=%WDIR%
@cd "%EXEC_DIR%"
@goto endDetectBaseDir

:baseDirNotFound
@SET MAVEN_PROJECTBASEDIR=%EXEC_DIR%
@cd "%EXEC_DIR%"

:endDetectBaseDir

@IF NOT EXIST "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar" (
  @IF NOT EXIST "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\MavenWrapperDownloader.java" (
    @ECHO Downloading https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar...
    @powershell -Command "&{"^
		"$webclient = new-object System.Net.WebClient;"^
		"$webclient.DownloadFile('https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar', '%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar')"^
		"}"
  )
)

@IF EXIST "C:\Users\bramv\AppData\Local\Programs\jdk-21-x64\jdk-21.0.13.1+2\bin\java.exe" (
  @SET "JAVA_HOME=C:\Users\bramv\AppData\Local\Programs\jdk-21-x64\jdk-21.0.13.1+2"
) ELSE (
  @IF "%JAVA_HOME%"=="" (
    @IF EXIST "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr\bin\java.exe" (
      @SET "JAVA_HOME=C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr"
    )
  ) ELSE (
    @ECHO %JAVA_HOME% | FINDSTR /I "jdk-17" >nul
    @IF NOT ERRORLEVEL 1 (
      @IF EXIST "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr\bin\java.exe" (
        @SET "JAVA_HOME=C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr"
      )
    )
  )
)

@SET MAVEN_JAVA_EXE="%JAVA_HOME%\bin\java.exe"
@set WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

@set DOWNLOAD_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"

@FOR /F "usebackq tokens=1,2 delims==" %%A IN ("%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties") DO (
    @IF "%%A"=="wrapperUrl" SET DOWNLOAD_URL=%%B
)

@REM Extension to allow automatically downloading the maven-wrapper.jar from Maven-central
@REM This allows using the maven wrapper in environments where the maven-wrapper.jar is not available
@REM but Maven central is accessible
@IF NOT EXIST "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar" (
    @IF NOT "%MVNW_REPOURL%" == "" SET DOWNLOAD_URL="%MVNW_REPOURL%/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"
    @ECHO Downloading %DOWNLOAD_URL%
    @powershell -Command "&{"^
		"$webclient = new-object System.Net.WebClient;"^
		"if (-not (Test-Path '%MAVEN_PROJECTBASEDIR%\.mvn\wrapper')) { New-Item -ItemType Directory -Path '%MAVEN_PROJECTBASEDIR%\.mvn\wrapper' | Out-Null; }"^
		"$webclient.DownloadFile(%DOWNLOAD_URL%, '%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar')"^
		"}"
    @IF "%ERRORLEVEL%"=="0" ( echo Downloaded maven-wrapper.jar ) else ( echo ERROR: Unable to download maven-wrapper.jar; exit 1 )
)

@SET WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

@%MAVEN_JAVA_EXE% %JVM_CONFIG_MAVEN_PROPS% %MAVEN_OPTS% %MAVEN_DEBUG_OPTS% ^
  -classpath "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar" ^
  "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" ^
  %WRAPPER_LAUNCHER% %MAVEN_CONFIG% %*

@IF "%ERRORLEVEL%"=="0" GOTO mainEnd
:error
@SET ERROR_CODE=%ERRORLEVEL%

:mainEnd
@IF "%MAVEN_BATCH_PAUSE%"=="on" pause

@IF "%MAVEN_TERMINATE_CMD%"=="on" EXIT %ERROR_CODE%

@ENDLOCAL & EXIT /B %ERROR_CODE%
