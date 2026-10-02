@echo off
REM ---------------------------------------------------------------------
REM  Ensaio de publicacao: gera o pacote e confere os hashes.
REM  NAO envia nada para a VPS e NAO altera o que esta no ar.
REM  Use quando quiser ver o que iria ser publicado antes de publicar.
REM ---------------------------------------------------------------------
setlocal
cd /d "%~dp0"
call "%~dp0publicar.bat" -PackageOnly %*
