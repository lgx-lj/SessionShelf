@echo off
chcp 65001 >nul
setlocal

set JAVA_HOME=C:\Users\LiGuoXian\.jdks\ms-17.0.16
set JAR_FILE=%~dp0target\session-shelf-1.0.0.jar
set JAVA_OPTS=--add-opens javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED --add-opens javafx.base/com.sun.javafx.binding=ALL-UNNAMED --add-opens javafx.graphics/com.sun.javafx.stage=ALL-UNNAMED --add-opens javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED --add-opens javafx.web/com.sun.javafx.scene.web=ALL-UNNAMED -Dfile.encoding=UTF-8

echo ========================================
echo   Sessionshelf
echo ========================================

if not exist "%JAR_FILE%" (
    echo [错误] 找不到 JAR: %JAR_FILE%
    echo 请先运行: mvn package -DskipTests
    pause
    exit /b 1
)

echo 启动中...
start "" "%JAVA_HOME%\bin\javaw.exe" %JAVA_OPTS% -jar "%JAR_FILE%"
echo 已启动（后台运行）
exit
