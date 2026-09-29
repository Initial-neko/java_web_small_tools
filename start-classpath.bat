@echo off
chcp 65001 >nul
title Toolbox (classpath mode)
echo ========================================
echo   Toolbox 本地工具集 - lib classpath
echo ========================================
echo.
java -cp "lib\*" com.toolbox.ToolboxApplication
pause
