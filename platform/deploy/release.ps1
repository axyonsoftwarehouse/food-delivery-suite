<#
.SYNOPSIS
    Gera, envia e aplica um release do Foodie na VPS.

.DESCRIPTION
    Um comando para publicar: empacota o conteúdo da pasta platform/ do commit
    escolhido com 'git archive', envia para a VPS junto com o deploy.sh e
    executa o deploy lá (com backup do banco, snapshot do código, verificação
    de /ready e rollback automático em caso de falha).

    O pacote é feito a partir do COMMIT, não da pasta de trabalho. Alterações
    não commitadas NÃO vão para a VPS — o script aborta se houver, para você
    não publicar achando que publicou.

    Observação: 'git archive <sha>:platform' grava nos arquivos o horário da
    execução, não o do commit. O conteúdo é sempre o mesmo para um dado commit,
    mas os bytes do .tar (e portanto o sha256) podem variar entre gerações. O
    sha256 conferido na VPS é sempre o do arquivo efetivamente enviado.

.PARAMETER Ambiente
    staging (padrão) ou production. Na produção, pede para digitar PUBLICAR PRODUCAO, e o
    deploy.sh na VPS só aceita o commit que já está publicado no staging.

.PARAMETER Sha
    Commit a publicar. Qualquer referência do git: HEAD (padrão), main, um sha
    curto, uma tag.

.PARAMETER AllowDirtyTree
    Permite publicar mesmo com arquivos versionados modificados. O que estiver
    modificado e não commitado continua fora do pacote.

.PARAMETER PackageOnly
    Só gera o .tar e mostra os hashes; não envia nem aplica nada.

.PARAMETER SshHost
    Destino SSH. Padrão: deploy@2.29.42.104

.PARAMETER KeyPath
    Chave SSH. Padrão: %USERPROFILE%\.ssh\foodie_vps

.EXAMPLE
    .\release.ps1
    Publica o commit HEAD.

.EXAMPLE
    .\release.ps1 -PackageOnly
    Gera o pacote e confere os hashes, sem tocar na VPS.

.EXAMPLE
    .\release.ps1 -Sha bf8a6a3
    Republica um commit anterior.

.EXAMPLE
    .\release.ps1 -Ambiente production -Sha bf8a6a3
    Promove para a produção o commit que está no staging.
#>
[CmdletBinding()]
param(
    [ValidateSet('staging', 'production')]
    [string] $Ambiente = 'staging',
    [string] $Sha = 'HEAD',
    [string] $SshHost = 'deploy@2.29.42.104',
    [string] $KeyPath = (Join-Path $env:USERPROFILE '.ssh\foodie_vps'),
    [string] $RemoteDir = '/home/deploy',
    [switch] $AllowDirtyTree,
    [switch] $PackageOnly
)

$ErrorActionPreference = 'Stop'

function Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Info($msg) { Write-Host "    $msg" }
function Warn($msg) { Write-Host "    [aviso] $msg" -ForegroundColor Yellow }
function Fail($msg) { Write-Host "`n[ERRO] $msg" -ForegroundColor Red; exit 1 }

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$deploySh = Join-Path $PSScriptRoot 'deploy.sh'

if (-not (Get-Command git -ErrorAction SilentlyContinue)) { Fail 'git não está no PATH.' }
if (-not (Test-Path $deploySh)) { Fail "deploy.sh não encontrado em $deploySh" }
if (-not $PackageOnly -and -not (Test-Path $KeyPath)) { Fail "chave SSH não encontrada: $KeyPath" }

Push-Location $repoRoot
try {
    # ---------------------------------------------------------- commit
    # O sha vai COMPLETO para a VPS: a produção compara com o .deployed do staging, que o
    # botão do GitHub grava com 40 caracteres. O curto é só para mostrar e nomear o pacote.
    $resolved = & git rev-parse --verify --quiet "$Sha^{commit}" 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $resolved) { Fail "commit '$Sha' não encontrado neste repositório." }
    $sha = "$resolved".Trim()
    $shaCurto = $sha.Substring(0, 7)

    $subject = (& git log -1 --format='%s' $sha).Trim()

    Step "Release $shaCurto no ambiente $Ambiente"
    Info "commit  : $sha"
    Info "assunto : $subject"
    Info "repo    : $repoRoot"

    # ------------------------------------------------- árvore limpa?
    $dirtyRaw = & git status --porcelain --untracked-files=no
    $dirty = @($dirtyRaw | Where-Object { $_ -and "$_".Trim() -ne '' })
    if ($dirty.Count -gt 0) {
        if ($AllowDirtyTree) {
            Warn 'há alterações não commitadas; elas NÃO entram no pacote (-AllowDirtyTree)'
            $dirty | ForEach-Object { Warn "  $_" }
        }
        else {
            Write-Host "`n[ERRO] Existem alterações não commitadas em arquivos versionados:" -ForegroundColor Red
            $dirty | ForEach-Object { Write-Host "    $_" -ForegroundColor Red }
            Fail @'
O pacote é feito com 'git archive', a partir do commit — o que está modificado
e não commitado NÃO iria para a VPS. Commite antes de publicar, ou rode com
-AllowDirtyTree se você tem certeza de que quer publicar o commit como está.
'@
        }
    }
    else {
        Info 'árvore limpa (nada versionado pendente)'
    }

    # ------------------------------------------------------- empacotar
    $tarPath = Join-Path $repoRoot "platform-release-$shaCurto.tar"
    if (Test-Path $tarPath) { Remove-Item $tarPath -Force }

    Step 'Empacotando (git archive <sha>:platform)'
    # core.autocrlf=true (padrão no Windows) faz o 'git archive' gravar CRLF nos
    # arquivos de texto. Em Linux isso quebra os .sh: 'set -euo pipefail\r' morre
    # com "invalid option name". Forçamos LF no pacote, sem depender da
    # configuração da máquina.
    & git -c core.autocrlf=false -c core.eol=lf archive --format=tar -o $tarPath "${sha}:platform"
    if ($LASTEXITCODE -ne 0) { Fail 'git archive falhou.' }
    if (-not (Test-Path $tarPath)) { Fail 'o pacote não foi gerado.' }

    $item = Get-Item $tarPath
    $sha256 = (Get-FileHash $tarPath -Algorithm SHA256).Hash.ToLower()
    $md5 = (Get-FileHash $tarPath -Algorithm MD5).Hash.ToLower()
    Info ("arquivo : {0} ({1:N1} MB)" -f $item.Name, ($item.Length / 1MB))
    Info "sha256  : $sha256"
    Info "md5     : $md5"

    if ($PackageOnly) {
        Step 'Somente pacote (-PackageOnly): nada foi enviado'
        Info "para aplicar manualmente na VPS:"
        Info "  scp -i `"$KeyPath`" `"$tarPath`" ${SshHost}:$RemoteDir/"
        Info "  ssh -i `"$KeyPath`" $SshHost `"FOODIE_ENV=$Ambiente bash $RemoteDir/deploy.sh $RemoteDir/$($item.Name) $sha $sha256`""
        return
    }

    if ($Ambiente -eq 'production') {
        Step 'Publicar na PRODUÇÃO'
        Info "commit: $sha ($subject)"
        $confirmacao = Read-Host 'Digite PUBLICAR PRODUCAO para confirmar'
        if ($confirmacao -cne 'PUBLICAR PRODUCAO') { Fail 'confirmação não confere; nada foi enviado.' }
    }

    # --------------------------------------------------------- enviar
    Step 'Enviando para a VPS'
    & scp -q -i $KeyPath $tarPath "${SshHost}:${RemoteDir}/"
    if ($LASTEXITCODE -ne 0) { Fail 'scp do pacote falhou.' }
    Info "pacote : $RemoteDir/$($item.Name)"

    & scp -q -i $KeyPath $deploySh "${SshHost}:${RemoteDir}/deploy.sh"
    if ($LASTEXITCODE -ne 0) { Fail 'scp do deploy.sh falhou.' }
    Info "runner : $RemoteDir/deploy.sh"

    # -------------------------------------------------------- aplicar
    Step 'Aplicando na VPS (backup, build e verificação)'
    $remotePackage = "$RemoteDir/$($item.Name)"
    $arvore = if ($Ambiente -eq 'production') { '/home/deploy/foodie-production' } else { '/home/deploy/foodie-platform' }
    & ssh -i $KeyPath $SshHost "FOODIE_ENV=$Ambiente bash $RemoteDir/deploy.sh '$remotePackage' '$sha' '$sha256'"
    $code = $LASTEXITCODE

    if ($code -ne 0) {
        Write-Host "`n[FALHA] o deploy retornou código $code." -ForegroundColor Red
        Write-Host 'O deploy.sh reverte o código sozinho quando a verificação falha.' -ForegroundColor Red
        Write-Host 'Confira o estado atual com:' -ForegroundColor Red
        Write-Host "  ssh -i `"$KeyPath`" $SshHost `"cat $arvore/.deployed`"" -ForegroundColor Red
        exit $code
    }

    Step "Concluído ($Ambiente)"
    Info "release : $sha"
    if ($Ambiente -eq 'staging') {
        Info "web     : https://staging.2.29.42.104.sslip.io"
        Info "api     : https://api.staging.2.29.42.104.sslip.io/ready"
    }
    elseif ($env:FOODIE_PRODUCTION_URL) {
        # O domínio de produção não fica no repositório (público).
        Info "web     : $env:FOODIE_PRODUCTION_URL"
    }
    Info "registro: $arvore/.deployed"
}
finally {
    Pop-Location
}
