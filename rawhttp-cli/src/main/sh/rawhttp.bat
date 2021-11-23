@echo off

set DIR=%~dp0

"%DIR%java" -Dpolyglot.engine.WarnInterpreterOnly=false -m rawhttp.cli/rawhttp.cli.Main %*
