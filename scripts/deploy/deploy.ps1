# Deploy remoto via SSH: prepara a pasta no servidor, envia o compose de deploy (front na
# porta 18050) com as imagens escolhidas e o .env-prod, baixa o ambiente antigo, remove as
# imagens antigas e sobe o ambiente novamente.
# Uso: .\scripts\deploy\deploy.ps1   (configuração em scripts\deploy\deploy.conf)

$ErrorActionPreference = 'Stop'

$Raiz = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$Conf = Join-Path $PSScriptRoot 'deploy.conf'
$RegexImagem = '^([a-z0-9.-]+(:[0-9]+)?/)?[a-z0-9]([a-z0-9._/-]*[a-z0-9])?(:[A-Za-z0-9._-]+)?$'
$Utf8SemBom = New-Object System.Text.UTF8Encoding($false)

function Erro([string]$Mensagem) {
    throw "ERRO: $Mensagem"
}

function Info([string]$Mensagem) {
    Write-Host "==> $Mensagem"
}

function Assert-Sucesso([string]$Mensagem) {
    if ($LASTEXITCODE -ne 0) { Erro "$Mensagem (código de saída $LASTEXITCODE)." }
}

# Lê o conf linha a linha (CHAVE=valor), sem executar nada do arquivo.
function Carregar-Conf {
    if (-not (Test-Path $Conf)) {
        Erro "arquivo $Conf não encontrado. Copie deploy.conf.example para deploy.conf e preencha."
    }
    $v = @{}
    foreach ($linha in [System.IO.File]::ReadAllLines($Conf, $Utf8SemBom)) {
        $l = $linha.Trim()
        if ($l -eq '' -or $l.StartsWith('#') -or -not $l.Contains('=')) { continue }
        $i = $l.IndexOf('=')
        $v[$l.Substring(0, $i).Trim()] = $l.Substring($i + 1).Trim().Trim('"')
    }
    function Valor($chave, $padrao) { if ($v[$chave]) { $v[$chave] } else { $padrao } }

    $c = [ordered]@{
        Host      = Valor 'SSH_HOST' ''
        Porta     = Valor 'SSH_PORT' '22'
        Usuario   = Valor 'SSH_USER' ''
        Chave     = Valor 'SSH_KEY' ''
        Senha     = Valor 'SSH_PASSWORD' ''
        Pasta     = (Valor 'REMOTE_PATH' '').TrimEnd('/')
        EnvFile   = Valor 'ENV_FILE' '.env-prod'
        Namespace = Valor 'DOCKERHUB_NAMESPACE' ''
        Backend   = Valor 'BACKEND_IMAGE' 'financas-backend'
        Frontend  = Valor 'FRONTEND_IMAGE' 'financas-frontend'
    }
    if (-not $c.Host) { Erro "SSH_HOST vazio em $Conf." }
    if (-not $c.Usuario) { Erro "SSH_USER vazio em $Conf." }
    if ($c.Porta -notmatch '^\d+$') { Erro "SSH_PORT inválida em $Conf." }
    if (-not ($c.Chave -or $c.Senha)) { Erro "preencha SSH_KEY ou SSH_PASSWORD em $Conf." }
    if ($c.Pasta -cnotmatch '^/[A-Za-z0-9._/-]+$') { Erro 'REMOTE_PATH deve ser um caminho absoluto sem espaços (ex.: /opt/financas).' }
    if (-not $c.Namespace) { Erro "DOCKERHUB_NAMESPACE vazio em $Conf." }
    $c.EnvLocal = Join-Path $Raiz $c.EnvFile
    if (-not (Test-Path $c.EnvLocal)) { Erro "arquivo de ambiente $($c.EnvLocal) não encontrado (ENV_FILE em $Conf)." }
    return $c
}

# Define como executar ssh e scp: chave (OpenSSH), senha com plink/pscp, ou senha digitada no OpenSSH.
function Configurar-Ssh($c) {
    $script:Destino = "$($c.Usuario)@$($c.Host)"
    $opcoes = @('-o', 'StrictHostKeyChecking=accept-new')
    if ($c.Chave) {
        $chave = $c.Chave
        if ($chave.StartsWith('~')) { $chave = $HOME + $chave.Substring(1) }
        if (-not (Test-Path $chave)) { Erro "chave SSH $chave não encontrada." }
        $script:SshExe = 'ssh'; $script:SshArgs = @('-i', $chave, '-p', $c.Porta) + $opcoes
        $script:ScpExe = 'scp'; $script:ScpArgs = @('-i', $chave, '-P', $c.Porta) + $opcoes
    } elseif ((Get-Command plink -ErrorAction SilentlyContinue) -and (Get-Command pscp -ErrorAction SilentlyContinue)) {
        # Sem -batch o plink trava perguntando se aceita a chave do servidor (o stdin já foi lido).
        # Aceita as chaves que o OpenSSH já conhece para o host, em known_hosts.
        $alvo = if ($c.Porta -eq '22') { $c.Host } else { "[$($c.Host)]:$($c.Porta)" }
        $chaves = @(& ssh-keygen -l -F $alvo 2>$null | Where-Object { $_ -match '\sSHA256:' } | ForEach-Object { ($_ -split '\s+')[2] })
        if ($chaves.Count -eq 0) {
            Erro "chave do servidor $alvo não está em known_hosts. Conecte uma vez com: ssh -p $($c.Porta) $($script:Destino)"
        }
        $hostkeys = @($chaves | ForEach-Object { '-hostkey', $_ })
        $script:SshExe = 'plink'; $script:SshArgs = @('-ssh', '-batch', '-P', $c.Porta, '-pw', $c.Senha) + $hostkeys
        $script:ScpExe = 'pscp'; $script:ScpArgs = @('-scp', '-batch', '-P', $c.Porta, '-pw', $c.Senha) + $hostkeys
    } else {
        Write-Warning 'SSH_PASSWORD definido, mas plink/pscp (PuTTY) não estão no PATH: o ssh vai pedir a senha a cada conexão (3 vezes).'
        Write-Warning 'Para não digitar: instale o PuTTY (winget install PuTTY.PuTTY) ou use SSH_KEY.'
        $opcoes += @('-o', 'PreferredAuthentications=password,keyboard-interactive', '-o', 'PubkeyAuthentication=no')
        $script:SshExe = 'ssh'; $script:SshArgs = @('-p', $c.Porta) + $opcoes
        $script:ScpExe = 'scp'; $script:ScpArgs = @('-P', $c.Porta) + $opcoes
    }
    if (-not (Get-Command $script:SshExe -ErrorAction SilentlyContinue)) { Erro "comando '$($script:SshExe)' não encontrado no PATH." }
}

# No PS 5.1, stderr de comando nativo vira erro quando a saída é redirecionada; o sucesso
# é checado pelo código de saída.
function Invoke-Remoto([string]$Comando) {
    $ErrorActionPreference = 'Continue'
    & $script:SshExe @script:SshArgs $script:Destino $Comando
}

function Enviar-Arquivos([string[]]$Arquivos, [string]$PastaRemota) {
    $ErrorActionPreference = 'Continue'
    & $script:ScpExe @script:ScpArgs @Arquivos "$($script:Destino):$PastaRemota/"
}

# Enter aceita :latest; só uma tag (sem / nem :) completa o repositório padrão.
function Perguntar-Imagem([string]$Rotulo, [string]$Repo) {
    $padrao = "${Repo}:latest"
    $resposta = (Read-Host "Imagem do $Rotulo [$padrao]").Trim()
    if (-not $resposta) {
        $resposta = $padrao
    } elseif (-not $resposta.Contains('/') -and -not $resposta.Contains(':')) {
        $resposta = "${Repo}:$resposta"
    }
    if ($resposta -cnotmatch $RegexImagem) { Erro "nome de imagem inválido: $resposta" }
    return $resposta
}

# Copia o arquivo com quebras de linha LF e UTF-8 sem BOM (o servidor é Linux).
function Copiar-Lf([string]$Origem, [string]$Destino, [hashtable]$Trocas = @{}) {
    $texto = [System.IO.File]::ReadAllText($Origem, $Utf8SemBom).Replace("`r`n", "`n")
    foreach ($k in $Trocas.Keys) { $texto = $texto.Replace($k, $Trocas[$k]) }
    [System.IO.File]::WriteAllText($Destino, $texto, $Utf8SemBom)
}

$c = Carregar-Conf
Configurar-Ssh $c

$imagemBackend = Perguntar-Imagem 'backend' "$($c.Namespace)/$($c.Backend)"
$imagemFrontend = Perguntar-Imagem 'frontend' "$($c.Namespace)/$($c.Frontend)"

Write-Host ''
Write-Host "Servidor:  $($script:Destino) (porta $($c.Porta))"
Write-Host "Pasta:     $($c.Pasta)"
Write-Host "Backend:   $imagemBackend"
Write-Host "Frontend:  $imagemFrontend"
Write-Host "Ambiente:  $($c.EnvFile)"
$confirma = Read-Host 'Confirma o deploy? [s/N]'
if ($confirma -notmatch '^[sS]$') { Write-Host 'Deploy cancelado.'; exit 0 }

Info "Verificando a pasta $($c.Pasta) no servidor"
$situacao = Invoke-Remoto "command -v docker >/dev/null && docker compose version >/dev/null || { echo SEM_DOCKER; exit 0; }; if [ -d '$($c.Pasta)' ]; then echo EXISTE; else mkdir -p '$($c.Pasta)' && echo CRIADO; fi"
Assert-Sucesso 'falha ao conectar no servidor'
$situacao = ($situacao | Out-String)
if ($situacao -match 'SEM_DOCKER') { Erro "docker ou docker compose não disponível para $($c.Usuario) no servidor." }
elseif ($situacao -match 'EXISTE') { Info 'Pasta existe: o ambiente será atualizado.' }
elseif ($situacao -match 'CRIADO') { Info 'Pasta criada: primeira instalação.' }
else { Erro "resposta inesperada do servidor: $situacao" }

$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("deploy-financas-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tmp | Out-Null
try {
    $compose = Join-Path $tmp 'docker-compose.yml.novo'
    $envNovo = Join-Path $tmp '.env-prod.novo'
    $scriptRemoto = Join-Path $tmp '.deploy-remoto.sh'
    Copiar-Lf (Join-Path $PSScriptRoot 'docker-compose.deploy.yml') $compose @{ '__BACKEND_IMAGE__' = $imagemBackend; '__FRONTEND_IMAGE__' = $imagemFrontend }
    Copiar-Lf $c.EnvLocal $envNovo
    Copiar-Lf (Join-Path $PSScriptRoot 'remoto.sh') $scriptRemoto

    Info "Enviando compose, $($c.EnvFile) e script de deploy"
    Enviar-Arquivos @($compose, $envNovo, $scriptRemoto) $c.Pasta
    Assert-Sucesso 'falha ao enviar os arquivos'

    Info 'Executando o deploy no servidor'
    Invoke-Remoto "bash '$($c.Pasta)/.deploy-remoto.sh' '$($c.Pasta)'"
    Assert-Sucesso 'falha no deploy remoto'
} finally {
    Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
}

Info "Concluído. Aplicação em http://$($c.Host):18050"
