# Constrói a imagem do frontend com a versão do package.json, publica no Docker Hub,
# cria a tag git, avança a versão e registra no CHANGELOG.md.
# Uso: .\scripts\docker\publicar-frontend.ps1 [major|minor|patch]   (padrão: patch)
param(
    [ValidateSet('major', 'minor', 'patch')][string]$Incremento = 'patch'
)

. (Join-Path $PSScriptRoot 'comum.ps1')

$frontend = Join-Path $script:Raiz 'frontend'

$lerVersao = {
    (Ler-Texto (Join-Path $frontend 'package.json') | ConvertFrom-Json).version
}

# npm version atualiza package.json e package-lock.json juntos.
$gravarVersao = {
    param([string]$Nova)
    $ErrorActionPreference = 'Continue'
    Push-Location $frontend
    try {
        npm version $Nova --no-git-tag-version | Out-Null
        Assert-Sucesso 'falha no npm version'
    } finally {
        Pop-Location
    }
}

Exigir-Comando npm
Publicar-Imagem -Projeto frontend -ImagemChave FRONTEND_IMAGE `
    -ArquivosVersao @('frontend/package.json', 'frontend/package-lock.json') `
    -LerVersao $lerVersao -GravarVersao $gravarVersao -Incremento $Incremento
