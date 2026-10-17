# Constrói a imagem do backend com a versão do pom.xml, publica no Docker Hub,
# cria a tag git, avança a versão e registra no CHANGELOG.md.
# Uso: .\scripts\docker\publicar-backend.ps1 [major|minor|patch]   (padrão: patch)
param(
    [ValidateSet('major', 'minor', 'patch')][string]$Incremento = 'patch'
)

. (Join-Path $PSScriptRoot 'comum.ps1')

$pom = Join-Path $script:Raiz 'backend\pom.xml'
# A versão do projeto é a primeira <version> depois de <artifactId>financas</artifactId>
# (a do parent vem antes e é ignorada).
$regexVersao = [regex]'(?s)(<artifactId>financas</artifactId>.*?<version>)([^<]+)(</version>)'

$lerVersao = {
    $m = $regexVersao.Match((Ler-Texto $pom))
    if ($m.Success) { $m.Groups[2].Value.Trim() }
}

$gravarVersao = {
    param([string]$Nova)
    $avaliador = [System.Text.RegularExpressions.MatchEvaluator] { param($m) $m.Groups[1].Value + $Nova + $m.Groups[3].Value }
    $texto = $regexVersao.Replace((Ler-Texto $pom), $avaliador, 1)
    Gravar-Texto $pom $texto
}

Publicar-Imagem -Projeto backend -ImagemChave BACKEND_IMAGE -ArquivosVersao @('backend/pom.xml') `
    -LerVersao $lerVersao -GravarVersao $gravarVersao -Incremento $Incremento
