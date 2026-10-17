# Funções compartilhadas pelos scripts publicar-*.ps1. Não execute diretamente.

$ErrorActionPreference = 'Stop'

$script:Raiz = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$script:Conf = Join-Path $script:Raiz 'scripts\docker\docker-publish.conf'
$script:Changelog = Join-Path $script:Raiz 'CHANGELOG.md'
$script:Utf8SemBom = New-Object System.Text.UTF8Encoding($false)

function Erro([string]$Mensagem) {
    throw "ERRO: $Mensagem"
}

function Info([string]$Mensagem) {
    Write-Host "==> $Mensagem"
}

function Assert-Sucesso([string]$Mensagem) {
    if ($LASTEXITCODE -ne 0) { Erro "$Mensagem (código de saída $LASTEXITCODE)." }
}

function Exigir-Comando([string]$Nome) {
    if (-not (Get-Command $Nome -ErrorAction SilentlyContinue)) { Erro "comando '$Nome' não encontrado no PATH." }
}

function Ler-Texto([string]$Caminho) {
    return [System.IO.File]::ReadAllText($Caminho, $script:Utf8SemBom)
}

function Gravar-Texto([string]$Caminho, [string]$Conteudo) {
    [System.IO.File]::WriteAllText($Caminho, $Conteudo, $script:Utf8SemBom)
}

# Lê o conf linha a linha (CHAVE=valor), sem executar nada do arquivo.
function Carregar-Conf([string]$ImagemChave) {
    if (-not (Test-Path $script:Conf)) {
        Erro "arquivo $($script:Conf) não encontrado. Copie docker-publish.conf.example para docker-publish.conf e preencha."
    }
    $valores = @{}
    foreach ($linha in [System.IO.File]::ReadAllLines($script:Conf, $script:Utf8SemBom)) {
        $l = $linha.Trim()
        if ($l -eq '' -or $l.StartsWith('#') -or -not $l.Contains('=')) { continue }
        $i = $l.IndexOf('=')
        $valores[$l.Substring(0, $i).Trim()] = $l.Substring($i + 1).Trim().Trim('"')
    }

    $conf = [ordered]@{
        Usuario   = $valores['DOCKERHUB_USERNAME']
        Token     = $valores['DOCKERHUB_TOKEN']
        Namespace = $valores['DOCKERHUB_NAMESPACE']
        Imagem    = $valores[$ImagemChave]
    }
    if (-not $conf.Usuario) { Erro "DOCKERHUB_USERNAME vazio em $($script:Conf)." }
    if (-not $conf.Token) { Erro "DOCKERHUB_TOKEN vazio em $($script:Conf)." }
    if (-not $conf.Imagem) { Erro "$ImagemChave vazio em $($script:Conf)." }
    if (-not $conf.Namespace) { $conf.Namespace = $conf.Usuario }
    return $conf
}

# Usa o docker do PATH; sem ele, o docker do WSL.
function Inicializar-Docker {
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        $script:DockerExe = 'docker'
        $script:DockerPrefixo = @()
        $script:DockerViaWsl = $false
    } elseif (Get-Command wsl -ErrorAction SilentlyContinue) {
        $script:DockerExe = 'wsl'
        $script:DockerPrefixo = @('docker')
        $script:DockerViaWsl = $true
    } else {
        Erro "docker não encontrado no PATH nem via WSL."
    }
}

# No PS 5.1, stderr de comando nativo vira erro quando a saída é redirecionada; com 'Stop'
# o progresso do docker build (e avisos do git) abortaria o script. O sucesso é checado
# pelo código de saída (Assert-Sucesso).
function Invoke-Docker {
    $ErrorActionPreference = 'Continue'
    $entrada = @($input)
    if ($entrada.Count -gt 0) {
        # Com o console em UTF-8 (chcp 65001), o PS 5.1 envia um BOM no stdin e o token
        # chegaria errado ao docker login. Força UTF-8 sem BOM enquanto envia.
        $OutputEncoding = $script:Utf8SemBom
        $encodingAnterior = $null
        try { $encodingAnterior = [Console]::InputEncoding; [Console]::InputEncoding = $script:Utf8SemBom } catch { }
        try {
            $entrada | & $script:DockerExe @script:DockerPrefixo @args
        } finally {
            if ($encodingAnterior) { try { [Console]::InputEncoding = $encodingAnterior } catch { } }
        }
    } else {
        & $script:DockerExe @script:DockerPrefixo @args
    }
}

function Invoke-Git {
    $ErrorActionPreference = 'Continue'
    & git @args
}

# Caminho do contexto de build como o docker o enxerga (no WSL, /mnt/c/...).
function Caminho-Docker([string]$Caminho) {
    if (-not $script:DockerViaWsl) { return $Caminho }
    $convertido = & wsl wslpath -a ($Caminho -replace '\\', '/')
    Assert-Sucesso "falha ao converter '$Caminho' para caminho do WSL"
    return $convertido.Trim()
}

function Proxima-Versao([string]$Versao, [string]$Incremento) {
    if ($Versao -notmatch '^(\d+)\.(\d+)\.(\d+)$') { Erro "versão '$Versao' não está no formato X.Y.Z." }
    $major = [int]$Matches[1]; $minor = [int]$Matches[2]; $patch = [int]$Matches[3]
    switch ($Incremento) {
        'major' { return "$($major + 1).0.0" }
        'minor' { return "$major.$($minor + 1).0" }
        'patch' { return "$major.$minor.$($patch + 1)" }
    }
}

# Insere a entrada antes da primeira seção "## " do CHANGELOG.
function Registrar-Changelog([string]$Entrada) {
    if (Test-Path $script:Changelog) {
        $texto = Ler-Texto $script:Changelog
    } else {
        $texto = "# Histórico de versões`n`nImagens publicadas no Docker Hub, da mais recente para a mais antiga. Gerado pelos scripts em ``scripts/docker/``.`n`n"
    }
    $m = [regex]::Match($texto, '(?m)^## ')
    if ($m.Success) {
        $texto = $texto.Insert($m.Index, "$Entrada`n")
    } else {
        $texto = $texto + $Entrada
    }
    Gravar-Texto $script:Changelog $texto
}

function Publicar-Imagem {
    param(
        [Parameter(Mandatory)][string]$Projeto,
        [Parameter(Mandatory)][string]$ImagemChave,
        [Parameter(Mandatory)][string[]]$ArquivosVersao,
        [Parameter(Mandatory)][scriptblock]$LerVersao,
        [Parameter(Mandatory)][scriptblock]$GravarVersao,
        [ValidateSet('major', 'minor', 'patch')][string]$Incremento = 'patch'
    )

    Exigir-Comando git
    Inicializar-Docker
    Push-Location $script:Raiz
    try {
        $conf = Carregar-Conf $ImagemChave

        $status = Invoke-Git status --porcelain
        Assert-Sucesso 'falha no git status'
        if ($status) { Erro 'há alterações não commitadas. Faça commit ou stash antes de publicar.' }

        $versao = & $LerVersao
        if (-not $versao) { Erro "não foi possível ler a versão de $Projeto." }
        $nova = Proxima-Versao $versao $Incremento
        $tag = "$Projeto-v$versao"
        $repo = "$($conf.Namespace)/$($conf.Imagem)"
        $commit = (Invoke-Git rev-parse HEAD).Trim()
        $commitCurto = (Invoke-Git rev-parse --short HEAD).Trim()

        if (Invoke-Git tag --list $tag) { Erro "a tag git $tag já existe. A versão $versao já foi publicada?" }

        # Mudanças desde a última publicação do projeto, sem os próprios commits de publicação.
        $tagAnterior = Invoke-Git tag --list "$Projeto-v*" --sort=-v:refname | Select-Object -First 1
        if ($tagAnterior) { $intervalo = @("$tagAnterior..HEAD") } else { $intervalo = @('-n', '20') }
        $mudancas = @(Invoke-Git log --oneline --invert-grep "--grep=^chore($Projeto): publicar" @intervalo -- "$Projeto/")
        Assert-Sucesso 'falha no git log'
        if ($mudancas.Count -eq 0) { $mudancas = @("(sem commits em $Projeto/)") }

        Info "Construindo ${repo}:$versao (commit $commitCurto)"
        $criado = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
        Invoke-Docker build `
            -t "${repo}:$versao" `
            -t "${repo}:latest" `
            --label "org.opencontainers.image.title=$($conf.Imagem)" `
            --label "org.opencontainers.image.version=$versao" `
            --label "org.opencontainers.image.revision=$commit" `
            --label "org.opencontainers.image.created=$criado" `
            (Caminho-Docker (Join-Path $script:Raiz $Projeto))
        Assert-Sucesso 'falha no docker build'

        Info 'Publicando no Docker Hub'
        $conf.Token | Invoke-Docker login -u $conf.Usuario --password-stdin
        Assert-Sucesso 'falha no docker login'
        Invoke-Docker push "${repo}:$versao"
        Assert-Sucesso 'falha no docker push'
        Invoke-Docker push "${repo}:latest"
        Assert-Sucesso 'falha no docker push'

        Info "Criando tag git $tag"
        Invoke-Git tag -a $tag -m "$Projeto $versao publicado como ${repo}:$versao"
        Assert-Sucesso 'falha ao criar a tag git'

        Info "Avançando versão de ${Projeto}: $versao -> $nova"
        & $GravarVersao $nova

        $linhas = @(
            "## $Projeto $versao — $(Get-Date -Format 'yyyy-MM-dd')"
            ''
            "- Imagem: ``${repo}:$versao`` (também ``latest``)"
            "- Commit: ``$commitCurto`` (tag ``$tag``)"
            "- Próxima versão de desenvolvimento: $nova"
            '- Mudanças:'
        ) + ($mudancas | ForEach-Object { "  - $_" })
        Registrar-Changelog (($linhas -join "`n") + "`n")

        Invoke-Git add @ArquivosVersao CHANGELOG.md
        Assert-Sucesso 'falha no git add'
        Invoke-Git commit -m "chore(${Projeto}): publicar $versao e avançar para $nova"
        Assert-Sucesso 'falha no git commit'

        Info "Concluído: ${repo}:$versao publicado; $Projeto agora em $nova."
        Write-Host '    Envie o commit e a tag com: git push --follow-tags'
    } finally {
        Pop-Location
    }
}
