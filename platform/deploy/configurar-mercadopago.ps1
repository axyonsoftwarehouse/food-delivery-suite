<#
.SYNOPSIS
    Grava as credenciais do Mercado Pago no .env do Foodie sem mostrar o valor.

.DESCRIPTION
    Pede o token com entrada mascarada (Read-Host -AsSecureString), confere contra a API do
    Mercado Pago (/users/me) e atualiza as chaves no arquivo .env indicado, preservando o resto
    do arquivo e fazendo uma copia de seguranca antes.

    O valor NUNCA aparece na tela nem no chat: o script informa apenas a CLASSE do token
    (TEST- = credencial de teste, APP_USR- = producao) e o tamanho.

    Por padrao recusa token de producao. Use -PermitirProducao se for mesmo a intencao, sabendo
    que a cobranca cai na conta dona daquele token.

.PARAMETER EnvFile
    Arquivo .env a atualizar. Padrao: platform/.env (ao lado desta pasta).

.PARAMETER DeArquivo
    Le o token de um arquivo em vez de pedir na tela (uso automatizado/ensaio).

.PARAMETER WebhookSecret
    Se informado, pede tambem o segredo de assinatura do webhook e grava em
    MERCADOPAGO_WEBHOOK_SECRET.

.PARAMETER WebhookSecretDeArquivo
    Le o segredo do webhook de um arquivo.

.PARAMETER NotificationUrl
    URL publica que o Mercado Pago chama de volta (grava MERCADOPAGO_NOTIFICATION_URL).

.PARAMETER LigarCobrancaOnline
    Liga o caminho de teste que permite CRIAR cobranca online nova
    (PAYMENTS_ALLOW_DIRECT_ONLINE_CHARGES=true). Sem isso, o produto continua recusando
    cobranca nova, como em producao.

.PARAMETER NaoConferir
    Grava o token sem conferir contra a API do Mercado Pago (uso sem rede / ensaio).

.EXAMPLE
    .\configurar-mercadopago.ps1
    Pede o token de teste, confere na API e grava em platform/.env.

.EXAMPLE
    .\configurar-mercadopago.ps1 -LigarCobrancaOnline -NotificationUrl "https://api.staging.2.29.42.104.sslip.io/webhooks/mercadopago"
    Configura e liga a cobranca online (uso no ambiente de teste/staging).
#>
[CmdletBinding()]
param(
    [string] $EnvFile = (Join-Path (Split-Path $PSScriptRoot -Parent) '.env'),
    [string] $DeArquivo,
    [string] $WebhookSecretDeArquivo,
    [string] $NotificationUrl,
    [switch] $WebhookSecret,
    [switch] $LigarCobrancaOnline,
    [switch] $PermitirProducao,
    [switch] $NaoConferir
)

$ErrorActionPreference = 'Stop'

function Info($msg) { Write-Host "    $msg" }
function Ok($msg) { Write-Host "    $msg" -ForegroundColor Green }
function Warn($msg) { Write-Host "    [aviso] $msg" -ForegroundColor Yellow }
function Falhar($msg) { Write-Host "`n[ERRO] $msg" -ForegroundColor Red; exit 1 }

function Ler-Segredo([string]$rotulo) {
    $seguro = Read-Host -Prompt "    $rotulo" -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($seguro)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

function Ler-DeArquivo([string]$caminho) {
    if (-not (Test-Path $caminho)) { Falhar "arquivo nao encontrado: $caminho" }
    return ((Get-Content -Raw -Path $caminho) -replace '\r?\n$', '')
}

function Classe-Do-Token([string]$valor) {
    if ($valor.StartsWith('TEST-')) { return 'teste' }
    if ($valor.StartsWith('APP_USR-')) { return 'producao' }
    return 'desconhecida'
}

function Definir-Chave([string[]]$linhas, [string]$chave, [string]$valor) {
    $nova = "$chave=$valor"
    $achou = $false
    $saida = foreach ($linha in $linhas) {
        if ($linha -match ('^\s*' + [regex]::Escape($chave) + '\s*=')) { $achou = $true; $nova } else { $linha }
    }
    if (-not $achou) { $saida += $nova }
    return , @($saida)
}

Write-Host "`n==> Foodie: configurar pagamento online (Mercado Pago)" -ForegroundColor Cyan

if (-not (Test-Path $EnvFile)) { Falhar "arquivo .env nao encontrado: $EnvFile" }
Info "arquivo alvo: $EnvFile"

# ---------------------------------------------------------------- token
if ($DeArquivo) {
    Info "lendo o token de: $DeArquivo"
    $token = Ler-DeArquivo $DeArquivo
} else {
    $token = Ler-Segredo 'Cole o ACCESS TOKEN do Mercado Pago e tecle Enter (nao aparece na tela):'
}
if ([string]::IsNullOrWhiteSpace($token)) { Falhar 'nenhum token informado.' }
$token = $token.Trim()

$classe = Classe-Do-Token $token
Info ("token: {0} caracteres, classe {1}" -f $token.Length, $classe)
switch ($classe) {
    'teste' { Ok 'credencial de TESTE - o dinheiro dessas cobrancas e ficticio.' }
    'producao' {
        if (-not $PermitirProducao) {
            Falhar 'isto e uma credencial de PRODUCAO (APP_USR-). Este script nao grava producao por padrao: a cobranca cai na conta dona do token e a decisao comercial do Foodie ainda esta aberta. Se for mesmo a intencao, rode de novo com -PermitirProducao.'
        }
        Warn 'credencial de PRODUCAO autorizada por parametro - cobranca real na conta dona do token.'
    }
    default { Warn 'o token nao comeca com TEST- nem APP_USR-; confira se copiou o access token (e nao a public key).' }
}

# ------------------------------------------------------- conferir na API
Write-Host "`n==> Conferindo o token no Mercado Pago" -ForegroundColor Cyan
if ($NaoConferir) {
    Warn 'conferencia com a API pulada (-NaoConferir): o token sera gravado sem validacao.'
} else {
    try {
        $resposta = Invoke-WebRequest -Uri 'https://api.mercadopago.com/users/me' -Method Get `
            -Headers @{ Authorization = "Bearer $token" } -UseBasicParsing -TimeoutSec 25
        $conta = $resposta.Content | ConvertFrom-Json
        Ok ("token aceito: conta '{0}' (id {1}, site {2})" -f $conta.nickname, $conta.id, $conta.site_id)
    } catch {
        $codigo = $null
        if ($_.Exception.Response) { $codigo = [int]$_.Exception.Response.StatusCode.value__ }
        if ($codigo -eq 401) { Falhar 'o Mercado Pago recusou o token (401). Confira se copiou o access token inteiro e se ele e do app certo.' }
        Warn ("nao consegui confirmar o token agora ({0}: {1}). O valor sera gravado mesmo assim." -f $codigo, $_.Exception.Message)
    }
}

# --------------------------------------------------------- segredo webhook
if ($WebhookSecret -and -not $WebhookSecretDeArquivo) {
    $segredo = Ler-Segredo 'Cole o SEGREDO do webhook (painel do app > Webhooks) e tecle Enter:'
    if (-not [string]::IsNullOrWhiteSpace($segredo)) { $segredo = $segredo.Trim() }
} elseif ($WebhookSecretDeArquivo) {
    $segredo = Ler-DeArquivo $WebhookSecretDeArquivo
} else {
    $segredo = $null
}

# ------------------------------------------------------------ gravar
Write-Host "`n==> Gravando no .env" -ForegroundColor Cyan
$backup = "$EnvFile.bak-" + (Get-Date -Format 'yyyyMMddHHmmss')
Copy-Item -Path $EnvFile -Destination $backup -Force
Info "copia de seguranca: $backup"

$linhas = @(Get-Content -Path $EnvFile)
$linhas = Definir-Chave $linhas 'MERCADOPAGO_ACCESS_TOKEN' $token
if ($segredo) { $linhas = Definir-Chave $linhas 'MERCADOPAGO_WEBHOOK_SECRET' $segredo }
if ($NotificationUrl) { $linhas = Definir-Chave $linhas 'MERCADOPAGO_NOTIFICATION_URL' $NotificationUrl }
if ($LigarCobrancaOnline) { $linhas = Definir-Chave $linhas 'PAYMENTS_ALLOW_DIRECT_ONLINE_CHARGES' 'true' }

[IO.File]::WriteAllLines($EnvFile, $linhas, (New-Object System.Text.UTF8Encoding($false)))
Ok 'gravado.'

# ------------------------------------------------------------- resumo
Write-Host "`n==> O que ficou no arquivo (sem valores)" -ForegroundColor Cyan
foreach ($chave in @('MERCADOPAGO_ACCESS_TOKEN', 'MERCADOPAGO_WEBHOOK_SECRET', 'MERCADOPAGO_NOTIFICATION_URL', 'PAYMENTS_ALLOW_DIRECT_ONLINE_CHARGES')) {
    $linha = (Get-Content -Path $EnvFile | Where-Object { $_ -match ('^\s*' + [regex]::Escape($chave) + '\s*=') } | Select-Object -First 1)
    if (-not $linha) { Info ('{0,-38} ausente' -f $chave); continue }
    $valor = ($linha -split '=', 2)[1]
    if ([string]::IsNullOrWhiteSpace($valor)) { Info ('{0,-38} (vazio)' -f $chave) }
    elseif ($chave -like '*TOKEN*' -or $chave -like '*SECRET*') { Info ('{0,-38} definido ({1} caracteres)' -f $chave, $valor.Length) }
    else { Info ('{0,-38} {1}' -f $chave, $valor) }
}

Write-Host "`n==> Proximo passo" -ForegroundColor Cyan
Info 'reconstruir a API para ela ler o .env:'
Info '  cd platform ; docker compose --profile java up -d --build api-java'
Info 'conferir se o provedor esta de pe:'
Info '  curl -s http://127.0.0.1:4001/payments/providers'
Info 'com a cobranca online ligada, o cliente consegue criar Pix/cartao e o retorno vem no webhook.'
Write-Host ''
