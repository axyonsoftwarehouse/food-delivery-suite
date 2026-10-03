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

.PARAMETER LigarCobrancaOnline
    Liga o caminho de teste que permite CRIAR cobranca online nova
    (PAYMENTS_ALLOW_DIRECT_ONLINE_CHARGES=true). Sem isso, o produto continua recusando
    cobranca nova, como em producao.

.PARAMETER NaoConferir
    Grava o token sem conferir contra a API do Mercado Pago (uso sem rede / ensaio).

.PARAMETER Ensaio
    Mostra o que seria gravado (arquivo e chaves) sem alterar nada. Use antes do primeiro uso.

.PARAMETER Remoto
    Em vez do .env local, grava no .env da VPS (staging) por SSH e recria a API para ela ler o
    arquivo novo. Exige a chave SSH do projeto.

.PARAMETER ChaveSsh
    Chave SSH usada no modo -Remoto. Padrao: %USERPROFILE%\.ssh\foodie_vps

.PARAMETER RemotoArquivo
    Arquivo a atualizar dentro do diretorio remoto (padrao: .env).

.PARAMETER NaoReiniciar
    No modo -Remoto, nao recria o container da API depois de gravar (o .env novo so vale na
    proxima recriacao).

.EXAMPLE
    .\configurar-mercadopago.ps1 -Remoto -LigarCobrancaOnline -WebhookSecret
    Configura o ambiente de staging: grava as credenciais na VPS (token e segredo do webhook) e
    recria a API. A URL de callback NAO vai no .env: ela e registrada no painel do Mercado Pago.

.EXAMPLE
    .\configurar-mercadopago.ps1
    Pede o token de teste, confere na API e grava em platform/.env.

.EXAMPLE
    .\configurar-mercadopago.ps1 -LigarCobrancaOnline -WebhookSecret
    Configura e liga a cobranca online (uso no ambiente de teste/staging).
#>
[CmdletBinding()]
param(
    [string] $EnvFile,
    [string] $DeArquivo,
    [string] $WebhookSecretDeArquivo,
    [switch] $WebhookSecret,
    [switch] $LigarCobrancaOnline,
    [switch] $PermitirProducao,
    [switch] $NaoConferir,
    [switch] $Ensaio,
    [switch] $Diagnosticar,
    [switch] $ComoTeste,
    [switch] $Remoto,
    [string] $SshHost = 'deploy@2.29.42.104',
    [string] $ChaveSsh,
    [string] $RemotoDir = '/home/deploy/foodie-platform/deploy',
    [string] $RemotoArquivo = '.env',
    [switch] $NaoReiniciar
)

$ErrorActionPreference = 'Stop'

function Info($msg) { Write-Host "    $msg" }
function Ok($msg) { Write-Host "    $msg" -ForegroundColor Green }
function Warn($msg) { Write-Host "    [aviso] $msg" -ForegroundColor Yellow }
function Falhar($msg) { Write-Host "`n[ERRO] $msg" -ForegroundColor Red; exit 1 }

function Enviar-Para-Vps($Valores) {
    $dir = $RemotoDir.TrimEnd('/')
    $nome = 'foodie-env-' + [Guid]::NewGuid().ToString('N') + '.env'
    $tempLocal = Join-Path ([IO.Path]::GetTempPath()) $nome
    $linhas = foreach ($chave in $Valores.Keys) { "$chave=$($Valores[$chave])" }
    # LF explicito: o .env do Linux nao pode receber CR no fim da linha (o valor iria com 
    # junto e a chave do webhook nunca casaria). WriteAllLines gravaria CRLF no Windows.
    [IO.File]::WriteAllText($tempLocal, (($linhas -join "`n") + "`n"), (New-Object System.Text.UTF8Encoding($false)))
    Info 'arquivo de chaves preparado localmente (e removido no fim)'

    try {
        & scp -q -i $ChaveSsh $tempLocal "$SshHost`:$dir/$nome"
        if ($LASTEXITCODE -ne 0) { Falhar 'scp do arquivo de chaves falhou.' }
        & scp -q -i $ChaveSsh (Join-Path $PSScriptRoot 'atualizar-env-pagamentos.sh') "$SshHost`:$dir/atualizar-env-pagamentos.sh"
        if ($LASTEXITCODE -ne 0) { Falhar 'scp do helper falhou.' }
        Info "enviado para $SshHost`:$dir"

        $merge = @'
chmod 600 "{0}/{1}" && bash "{0}/atualizar-env-pagamentos.sh" "{0}/{1}" "{0}/{2}"; rm -f "{0}/{1}"
'@ -f $dir, $nome, $RemotoArquivo
        & ssh -i $ChaveSsh $SshHost $merge
        if ($LASTEXITCODE -ne 0) { Falhar 'a atualizacao do .env na VPS falhou (o helper guarda copia de seguranca antes de mexer).' }
    } finally {
        if (Test-Path $tempLocal) { Remove-Item $tempLocal -Force }
        Info 'arquivo de chaves local removido.'
    }
}

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
    if ($valor.StartsWith('APP_USR-')) { return 'app_usr' }
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

# O caminho padrao e calculado aqui, e nao no bloco param: $PSScriptRoot nao esta
# disponivel nos valores padrao dos parametros (sai vazio e o Split-Path quebra).
if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = Join-Path (Split-Path $PSScriptRoot -Parent) '.env'
}
if ([string]::IsNullOrWhiteSpace($ChaveSsh)) {
    $ChaveSsh = Join-Path $env:USERPROFILE '.ssh\foodie_vps'
}

if ($Remoto) {
    Info "modo remoto: alvo e $SshHost`:$($RemotoDir.TrimEnd('/'))/$RemotoArquivo (o .env local nao sera alterado)"
} else {
    if (-not (Test-Path $EnvFile)) { Falhar "arquivo .env nao encontrado: $EnvFile" }
    Info "arquivo alvo: $EnvFile"
}

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
Info ("token: {0} caracteres, prefixo {1}" -f $token.Length, $classe)
if ($classe -eq 'teste') { Ok 'credencial de TESTE da aplicacao (TEST-) - o dinheiro dessas cobrancas e ficticio.' }
elseif ($classe -eq 'app_usr') { Info 'prefixo APP_USR-: pode ser a conta real da aplicacao ou um USUARIO DE TESTE - quem responde e o Mercado Pago, nao o prefixo.' }
else { Warn 'o token nao comeca com TEST- nem APP_USR-; confira se copiou o access token (e nao a public key).' }

# ------------------------------------------------------- conferir na API
# O prefixo do token NAO separa conta real de usuario de teste (o access token de usuario de
# teste tambem comeca com APP_USR-). Quem decide e o que o /users/me devolve.
Write-Host "`n==> Conferindo o token no Mercado Pago" -ForegroundColor Cyan
$usuarioDeTeste = $false
$conferido = $false
if ($NaoConferir) {
    Warn 'conferencia com a API pulada (-NaoConferir): sem ela nao da para separar conta real de usuario de teste.'
} else {
    try {
        $resposta = Invoke-WebRequest -Uri 'https://api.mercadopago.com/users/me' -Method Get `
            -Headers @{ Authorization = "Bearer $token" } -UseBasicParsing -TimeoutSec 25
        $conta = $resposta.Content | ConvertFrom-Json
        $conferido = $true
        Ok ("token aceito: conta '{0}' (id {1}, site {2})" -f $conta.nickname, $conta.id, $conta.site_id)
        $rotuloTags = if ($conta.tags) { ($conta.tags -join ', ') } else { '(nenhuma)' }
        $email = if ($conta.email) { [string]$conta.email } else { '(vazio)' }
        $tipo = if ($conta.user_type) { [string]$conta.user_type } else { '(vazio)' }
        Info ("email : {0}" -f $email)
        Info ("tags  : {0}" -f $rotuloTags)
        Info ("tipo  : {0}" -f $tipo)
        $usuarioDeTeste = (($conta.tags -contains 'test_user') -or ($email -match '(?i)test_?user|@testuser\.com'))
        if ($usuarioDeTeste) { Ok 'a conta e de USUARIO DE TESTE - dinheiro ficticio.' }
        else { Warn 'a conta NAO tem marcador de usuario de teste na resposta do Mercado Pago.' }
    } catch {
        $codigo = $null
        if ($_.Exception.Response) { $codigo = [int]$_.Exception.Response.StatusCode.value__ }
        if ($codigo -eq 401) { Falhar 'o Mercado Pago recusou o token (401). Confira se copiou o access token inteiro e se ele e do app certo.' }
        Warn ("nao consegui confirmar o token agora ({0}: {1})." -f $codigo, $_.Exception.Message)
    }
}

if ($Diagnosticar) {
    Write-Host "`n==> Diagnostico (-Diagnosticar): NADA foi gravado" -ForegroundColor Cyan
    Info ("prefixo do token .........: {0}" -f $classe)
    Info ("conferido no Mercado Pago : {0}" -f $conferido)
    Info ("usuario de teste ..........: {0}" -f $usuarioDeTeste)
    $decisao = if ($classe -eq 'teste' -or $usuarioDeTeste -or $ComoTeste) { 'grava como ambiente de TESTE' }
        elseif ($PermitirProducao) { 'grava como PRODUCAO (a pedido)' }
        else { 'recusa (rode com -ComoTeste se e usuario de teste, ou -PermitirProducao se e cobranca real)' }
    Info ("o script faria ...........: {0}" -f $decisao)
    Write-Host ''
    exit 0
}

# ------------------------------------------------------------- o portao
if ($classe -eq 'teste' -or $usuarioDeTeste -or $ComoTeste) {
    if ($ComoTeste) { Warn 'tratando como ambiente de TESTE por -ComoTeste (voce declarou que a conta e de teste).' }
} elseif ($PermitirProducao) {
    Warn 'tratando como PRODUCAO por -PermitirProducao - a cobranca cai na conta dona do token.'
} else {
    Falhar 'esta credencial nao comeca com TEST- e o Mercado Pago nao a marcou como usuario de teste (veja email/tags acima). Se ela e de teste mesmo (um usuario de teste do painel), rode de novo com -ComoTeste; se a intencao e cobrar de verdade nesta conta, rode com -PermitirProducao - sabendo que o dinheiro cai na conta dona do token e que a decisao comercial do Foodie ainda esta aberta.'
}

# --------------------------------------------------------- segredo webhook
if ($WebhookSecret -and -not $WebhookSecretDeArquivo) {
    $segredo = Ler-Segredo 'Cole o SEGREDO do webhook (painel do app > Webhooks) e tecle Enter:'
    if (-not [string]::IsNullOrWhiteSpace($segredo)) { $segredo = $segredo.Trim() }
} elseif ($WebhookSecretDeArquivo) {
    $segredo = Ler-DeArquivo $WebhookSecretDeArquivo
} else {
    $segredo = $null
    Warn 'sem segredo de webhook nesta rodada: a validacao de assinatura fica ACEITANDO QUALQUER ORIGEM. Registre a URL no painel do Mercado Pago (Webhooks > evento "Order") e rode de novo com -WebhookSecret.'
}

# ------------------------------------------------------------ gravar
$plano = [ordered]@{}
$plano['MERCADOPAGO_ACCESS_TOKEN'] = $token
if ($segredo) { $plano['MERCADOPAGO_WEBHOOK_SECRET'] = $segredo }
if ($LigarCobrancaOnline) {
    $plano['PAYMENTS_ALLOW_DIRECT_ONLINE_CHARGES'] = 'true'
} else {
    # Sem o parametro, a chave so entra se ainda nao existir (documenta o padrao) - assim uma
    # cobranca ja ligada nao e desligada sem querer.
    $existente = (Get-Content -Path $EnvFile | Where-Object { $_ -match '^\s*PAYMENTS_ALLOW_DIRECT_ONLINE_CHARGES\s*=' } | Select-Object -First 1)
    if (-not $existente) { $plano['PAYMENTS_ALLOW_DIRECT_ONLINE_CHARGES'] = 'false' }
}

if ($Ensaio) {
    Write-Host "`n==> Ensaio (-Ensaio): NADA sera gravado" -ForegroundColor Cyan
    if ($Remoto) { Info "seria gravado em: $SshHost`:$($RemotoDir.TrimEnd('/'))/$RemotoArquivo" }
    else { Info "arquivo que seria alterado: $EnvFile" }
} elseif ($Remoto) {
    Write-Host "`n==> Gravando na VPS" -ForegroundColor Cyan
    Enviar-Para-Vps $plano
    if ($NaoReiniciar) {
        Warn 'servico nao recriado (-NaoReiniciar): o .env novo so vale quando o container for recriado.'
    } else {
        Write-Host "`n==> Recriando a API na VPS para ela ler o .env novo" -ForegroundColor Cyan
        $recriar = @'
cd "{0}" && docker compose up -d --force-recreate --no-deps api >/dev/null 2>&1 && for i in $(seq 1 20); do curl -fsS http://localhost:4001/ready && break; sleep 3; done; echo "conferencia concluida"
'@ -f $RemotoDir.TrimEnd('/')
        & ssh -i $ChaveSsh $SshHost $recriar
        if ($LASTEXITCODE -ne 0) { Falhar ('a recriacao da API na VPS falhou; confira com: ssh -i <chave> ' + $SshHost + ' "docker logs foodie-staging-api-1"') }
    }
} else {
    Write-Host "`n==> Gravando no .env" -ForegroundColor Cyan
    $backup = "$EnvFile.bak-" + (Get-Date -Format 'yyyyMMddHHmmss')
    Copy-Item -Path $EnvFile -Destination $backup -Force
    Info "copia de seguranca: $backup"

    $linhas = @(Get-Content -Path $EnvFile)
    foreach ($chave in $plano.Keys) { $linhas = Definir-Chave $linhas $chave ([string]$plano[$chave]) }

    # Aqui o arquivo e o local (Windows) e o .env dele ja usa CRLF: mantem a convencao do arquivo.
    [IO.File]::WriteAllLines($EnvFile, $linhas, (New-Object System.Text.UTF8Encoding($false)))
    Ok 'gravado.'
}

# ------------------------------------------------------------- resumo
if ($Ensaio) { Write-Host "`n==> O que seria gravado (sem valores)" -ForegroundColor Cyan }
else { Write-Host "`n==> O que ficou no arquivo (sem valores)" -ForegroundColor Cyan }
foreach ($chave in $plano.Keys) {
    $valor = [string]$plano[$chave]
    if ($chave -like '*TOKEN*' -or $chave -like '*SECRET*') { Info ('{0,-38} definido ({1} caracteres)' -f $chave, $valor.Length) }
    else { Info ('{0,-38} {1}' -f $chave, $valor) }
}
foreach ($chave in @('MERCADOPAGO_WEBHOOK_SECRET', 'PAYMENTS_ALLOW_DIRECT_ONLINE_CHARGES')) {
    if ($Remoto -or $plano.Contains($chave)) { continue }
    $igual = (Get-Content -Path $EnvFile | Where-Object { $_ -match ('^\s*' + [regex]::Escape($chave) + '\s*=') } | Select-Object -First 1)
    if ($igual) { Info ('{0,-38} mantido como estava' -f $chave) } else { Info ('{0,-38} ausente' -f $chave) }
}

Write-Host "`n==> Proximo passo" -ForegroundColor Cyan
if ($Remoto) {
    Info 'no staging, o teste e: pedido de cliente com pagamento online -> Pix (QR) ou cartao (Brick).'
    Info 'a URL de callback do webhook e registrada no painel do Mercado Pago (Webhooks > evento "Order").'
    Info 'conferir a API publica: curl -s https://api.staging.2.29.42.104.sslip.io/ready'
} else {
    Info 'reconstruir a API para ela ler o .env:'
    Info '  cd platform ; docker compose --profile java up -d --build api-java'
    Info 'conferir se o provedor esta de pe:'
    Info '  curl -s http://127.0.0.1:4001/payments/providers'
    Info 'com a cobranca online ligada, o cliente consegue criar Pix/cartao e o retorno vem no webhook.'
}
Write-Host ''
