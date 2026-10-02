@echo off
REM ---------------------------------------------------------------------
REM  Configura as credenciais do Mercado Pago no .env do Foodie
REM
REM  Clique duas vezes e cole o token quando o script pedir: o valor nao
REM  aparece na tela.
REM
REM  Com parametros:
REM      configurar-mercadopago.bat -Ensaio
REM      configurar-mercadopago.bat -LigarCobrancaOnline
REM      configurar-mercadopago.bat -NotificationUrl "https://api.staging.../webhooks/mercadopago"
REM      configurar-mercadopago.bat -WebhookSecret
REM      configurar-mercadopago.bat -EnvFile "C:\outro\.env"
REM      configurar-mercadopago.bat -NaoConferir
REM
REM  Para o ambiente no ar (staging), em vez do .env local:
REM      configurar-mercadopago.bat -Remoto -LigarCobrancaOnline -NotificationUrl "https://api.staging.2.29.42.104.sslip.io/webhooks/mercadopago"
REM  (o -Remoto grava na VPS por SSH e recria a API; -NaoReiniciar so grava)
REM ---------------------------------------------------------------------
setlocal
cd /d "%~dp0"

echo.
echo === Foodie: configurar pagamento online (Mercado Pago) ===

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0configurar-mercadopago.ps1" %*
set "CODIGO=%ERRORLEVEL%"

echo.
if "%CODIGO%"=="0" (
  echo [OK] Terminou sem erro.
) else (
  echo [ATENCAO] Terminou com codigo %CODIGO%. Leia as mensagens acima.
)
echo.
pause
