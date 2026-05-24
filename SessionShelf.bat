@echo off
chcp 65001 >nul
echo ========================================
echo   AI 会话管家 - SessionShelf (无控制台)
echo ========================================

set JAVA_HOME=C:\Users\LiGuoXian\.jdks\ms-17.0.16

start "" "%JAVA_HOME%\bin\javaw.exe" ^
    --add-opens javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED ^
    --add-opens javafx.base/com.sun.javafx.binding=ALL-UNNAMED ^
    --add-opens javafx.graphics/com.sun.javafx.stage=ALL-UNNAMED ^
    --add-opens javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED ^
    --add-opens javafx.web/com.sun.javafx.scene.web=ALL-UNNAMED ^
    -Dfile.encoding=UTF-8 ^
    -jar "%~dp0session-shelf-1.0.0.jar"

exit
