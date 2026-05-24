@echo off
chcp 65001 >nul
echo ========================================
echo   AI 会话管家 - 创建 EXE 安装包
echo ========================================
echo.
echo 说明: jpackage 需要 WiX Toolset 才能创建 EXE 安装程序。
echo.
echo 步骤:
echo 1. 下载并安装 WiX Toolset 3.11 或更高版本
echo    下载地址: https://wixtoolset.org/
echo.
echo 2. 安装完成后，将 WiX 的 bin 目录添加到 PATH 环境变量
echo    默认路径: C:\Program Files (x86)\WiX Toolset v3.11\bin
echo.
echo 3. 重新运行此脚本
echo.
echo ============================================================
echo.
echo 按任意键开始打包（已安装 WiX Toolset）...
pause >nul

set JAVA_HOME=C:\Users\LiGuoXian\.jdks\ms-17.0.16
set PATH=%JAVA_HOME%\bin;%PATH%

echo.
echo [1/2] 清理旧文件...
if exist "target\dist" rmdir /s /q "target\dist"
mkdir "target\dist"

echo [2/2] 打包 EXE...
echo.

jpackage ^
    --type exe ^
    --name "SessionShelf" ^
    --input target ^
    --main-jar session-shelf-1.0.0.jar ^
    --main-class com.sessionshelf.Launcher ^
    --dest target\dist ^
    --java-options "--add-opens=javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED" ^
    --java-options "--add-opens=javafx.base/com.sun.javafx.binding=ALL-UNNAMED" ^
    --java-options "--add-opens=javafx.graphics/com.sun.javafx.stage=ALL-UNNAMED" ^
    --java-options "--add-opens=javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED" ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --app-version 1.0.0 ^
    --vendor "SessionShelf" ^
    --copyright "Copyright 2024" ^
    --description "AI 会话管家 - 本地离线多源 AI 会话统一管理工具"

if %errorlevel% neq 0 (
    echo.
    echo ========================================
    echo 打包失败！请确保已安装 WiX Toolset。
    echo ========================================
    pause
    exit /b 1
)

echo.
echo ========================================
echo   打包成功！
echo ========================================
echo.
echo EXE 安装程序: target\dist\SessionShelf-1.0.0.exe
echo.
echo 您可以分发此 EXE 文件，用户双击即可安装运行。
echo.
pause
