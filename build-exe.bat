@echo off
chcp 65001 >nul
setlocal

set JAVA_HOME=C:\Users\LiGuoXian\.jdks\ms-17.0.16
set PATH=%JAVA_HOME%\bin;%PATH%

rem WiX 路径
set PATH=D:\SoftwareCoding\exe\wix\wix314-binaries;%PATH%

echo ========================================
echo   Sessionshelf - jpackage 打包 EXE
echo ========================================
echo.

where jpackage >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 找不到 jpackage，请检查 JDK 版本（需要 JDK 14+）
    pause
    exit /b 1
)

where light.exe >nul 2>&1
if %errorlevel% neq 0 (
    rem WiX 不在 PATH，尝试常见安装路径
    for /d %%i in ("C:\Program Files (x86)\WiX Toolset*" "C:\Program Files\WiX Toolset*") do (
        if exist "%%i\bin\light.exe" set "PATH=%%i\bin;%PATH%"
    )
    where light.exe >nul 2>&1
    if %errorlevel% neq 0 (
        echo [错误] 找不到 WiX Toolset（light.exe）
        echo 请先安装: https://wixtoolset.org/releases/
        echo 或运行: winget install --id WiXToolset.WiXToolset -e  （需管理员权限）
        pause
        exit /b 1
    )
    echo [WiX] 已自动检测到安装路径
)

echo [1/3] 编译项目...
call mvn package -DskipTests -q
if %errorlevel% neq 0 (
    echo [错误] 编译失败
    pause
    exit /b 1
)

echo [2/3] 清理旧输出...
if exist "target\dist" rmdir /s /q "target\dist"

echo [3/3] 打包 EXE（需要几分钟）...
echo.

jpackage ^
    --type exe ^
    --name "Sessionshelf" ^
    --input target ^
    --main-jar session-shelf-1.0.0.jar ^
    --main-class com.sessionshelf.Launcher ^
    --dest target\dist ^
    --icon src\main\resources\images\logo.png ^
    --java-options "--add-opens=javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED" ^
    --java-options "--add-opens=javafx.base/com.sun.javafx.binding=ALL-UNNAMED" ^
    --java-options "--add-opens=javafx.graphics/com.sun.javafx.stage=ALL-UNNAMED" ^
    --java-options "--add-opens=javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED" ^
    --java-options "--add-opens=javafx.web/com.sun.javafx.scene.web=ALL-UNNAMED" ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --app-version 1.0.0 ^
    --vendor "Sessionshelf" ^
    --description "本地离线多源 AI 会话统一管理工具" ^
    --win-dir-chooser ^
    --win-shortcut ^
    --win-menu

if %errorlevel% neq 0 (
    echo.
    echo ========================================
    echo 打包失败！
    echo 常见原因:
    echo   1. 未安装 WiX Toolset 3.x
    echo   2. logo.png 不是有效 PNG
    echo   3. JDK 版本不兼容
    echo ========================================
    pause
    exit /b 1
)

echo.
echo ========================================
echo   打包成功！
echo   输出: target\dist\Sessionshelf-1.0.0.exe
echo ========================================
pause
