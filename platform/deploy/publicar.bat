@echo off
REM ---------------------------------------------------------------------
REM  Publica o Foodie no staging (VPS) - atalho em .bat para o release.ps1
REM
REM  Clique duas vezes para publicar o commit HEAD.
REM  Com parametros:
REM      publicar.bat -PackageOnly    so gera o .tar e mostra os hashes
REM      publicar.bat -Sha bf8a6a3    republica um commit anterior
REM
REM  O arquivo .ps1 nao abre com duplo clique: o Windows nunca executa
REM  script por clique (nao ha associacao para .ps1). Por isso este atalho.
REM ---------------------------------------------------------------------
setlocal
cd /d "%~dp0"

echo.
echo === Foodie: publicar no staging ===

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0release.ps1" %*
set "CODIGO=%ERRORLEVEL%"

echo.
if "%CODIGO%"=="0" (
  echo [OK] Terminou sem erro.
) else (
  echo [ATENCAO] Terminou com codigo %CODIGO%. Leia as mensagens acima.
)
echo.
pause
