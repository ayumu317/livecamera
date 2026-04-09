@echo off
setlocal enabledelayedexpansion

REM ==========================================
REM LiveCamera-LBS GitHub 上传脚本
REM 用途：
REM 1. 如果当前目录还没初始化 Git，就自动执行 git init
REM 2. 自动添加所有文件并提交
REM 3. 询问 GitHub 远程仓库 URL
REM 4. 最后执行 git push
REM
REM 重要提醒：
REM 请先检查 .gitignore，避免把 build/、.gradle/、.idea/、
REM local.properties、target/ 等构建产物或本地配置上传到 GitHub。
REM ==========================================

cd /d "%~dp0"

echo.
echo ==========================================
echo   GitHub 上传助手
echo ==========================================
echo 当前目录：%CD%
echo.
echo [提醒] 请先检查 .gitignore，避免上传 build/、.gradle/、target/ 等文件夹。
echo.

where git >nul 2>nul
if errorlevel 1 (
    echo [错误] 未检测到 Git，请先安装 Git for Windows。
    goto :end
)

if not exist ".git" (
    echo [信息] 当前目录尚未初始化 Git，正在执行 git init ...
    git init
    if errorlevel 1 (
        echo [错误] git init 执行失败。
        goto :end
    )
) else (
    echo [信息] 检测到现有 Git 仓库，跳过 git init。
)

echo.
git status --short
echo.

echo [信息] 正在执行 git add . ...
git add .
if errorlevel 1 (
    echo [错误] git add 执行失败。
    goto :end
)

set "COMMIT_MSG="
set /p COMMIT_MSG=请输入提交说明（直接回车则使用 "Update project files"）: 
if "%COMMIT_MSG%"=="" set "COMMIT_MSG=Update project files"

echo.
echo [信息] 正在提交代码...
git commit -m "%COMMIT_MSG%"
if errorlevel 1 (
    echo [提示] git commit 可能失败，常见原因是“没有需要提交的更改”。
    echo [提示] 脚本将继续尝试配置远程仓库并 push。
)

echo.
set "REMOTE_URL="
set /p REMOTE_URL=请输入 GitHub 远程仓库 URL（例如 https://github.com/yourname/yourrepo.git）: 
if "%REMOTE_URL%"=="" (
    echo [错误] 远程仓库 URL 不能为空。
    goto :end
)

git remote get-url origin >nul 2>nul
if errorlevel 1 (
    echo [信息] 正在添加 origin 远程仓库...
    git remote add origin "%REMOTE_URL%"
    if errorlevel 1 (
        echo [错误] 添加远程仓库失败。
        goto :end
    )
) else (
    for /f "delims=" %%i in ('git remote get-url origin') do set "CURRENT_REMOTE=%%i"
    echo [信息] 当前 origin：!CURRENT_REMOTE!
    choice /c YN /m "是否将 origin 更新为新输入的 URL"
    if errorlevel 2 (
        echo [信息] 保留现有 origin。
    ) else (
        git remote set-url origin "%REMOTE_URL%"
        if errorlevel 1 (
            echo [错误] 更新 origin 失败。
            goto :end
        )
    )
)

set "CURRENT_BRANCH="
for /f "delims=" %%i in ('git symbolic-ref --short HEAD 2^>nul') do set "CURRENT_BRANCH=%%i"
if "%CURRENT_BRANCH%"=="" set "CURRENT_BRANCH=main"

echo.
echo [信息] 当前分支：%CURRENT_BRANCH%
echo [信息] 正在执行 git push -u origin %CURRENT_BRANCH% ...
git push -u origin "%CURRENT_BRANCH%"
if errorlevel 1 (
    echo.
    echo [错误] git push 失败。
    echo [提示] 请检查：
    echo 1. 远程仓库 URL 是否正确
    echo 2. GitHub 账号是否已完成认证
    echo 3. 当前分支是否存在有效提交
    goto :end
)

echo.
echo [成功] 项目已推送到 GitHub。

:end
echo.
pause
endlocal
