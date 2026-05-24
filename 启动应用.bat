@echo off
chcp 65001 >nul
echo ========================================
echo   AI 会话管家 - SessionShelf
echo ========================================
echo.

set JAVA_HOME=C:\Users\LiGuoXian\.jdks\ms-17.0.16
set JAR_FILE=%~dp0session-shelf-1.0.0.jar

if not exist "%JAR_FILE%" (
    echo 错误: 找不到 JAR 文件！
    echo 请确保 session-shelf-1.0.0.jar 与此脚本在同一目录。
    pause
    exit /b 1
)

echo 正在启动应用...
echo.

"%JAVA_HOME%\bin\javaw.exe" ^
    --add-opens javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED ^
    --add-opens javafx.base/com.sun.javafx.binding=ALL-UNNAMED ^
    --add-opens javafx.graphics/com.sun.javafx.stage=ALL-UNNAMED ^
    --add-opens javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED ^
    --add-opens javafx.web/com.sun.javafx.scene.web=ALL-UNNAMED ^
    -Dfile.encoding=UTF-8 ^
    -jar "%JAR_FILE%"

if %errorlevel% neq 0 (
    echo.
    echo 启动失败！请检查 Java 版本是否正确。
    pause
)
