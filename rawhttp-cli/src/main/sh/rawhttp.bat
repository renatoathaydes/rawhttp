@echo off

set DIR=%~dp0

"%DIR%java" -m rawhttp.cli/rawhttp.cli.Main %*
