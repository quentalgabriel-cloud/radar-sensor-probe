# Releases assinadas do Radar Sensor

## Por que existe uma chave permanente

O Android aceita uma atualização somente quando ela mantém o mesmo `applicationId`, usa a mesma chave de assinatura e possui `versionCode` maior. Builds de debug geradas em runners temporários não oferecem essa garantia.

## Preparação única

Gere a chave fora do repositório e guarde duas cópias seguras:

```bash
keytool -genkeypair \
  -keystore radar-sensor-release.jks \
  -alias radar-sensor \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Nunca faça commit do arquivo ou das senhas. Cadastre no GitHub Actions:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

`ANDROID_KEYSTORE_BASE64` deve conter o arquivo `.jks` codificado integralmente em Base64, sem quebras de linha.

## Publicação

1. Atualize `versionCode` e `versionName` em `app/build.gradle`.
2. Faça merge da mudança validada em `main`.
3. Crie e envie uma tag exatamente igual a `v` + `versionName`.

Exemplo:

```bash
git tag v0.2.0-probe
git push origin v0.2.0-probe
```

O workflow valida versão e secrets, compila, verifica a assinatura e publica APK + checksum SHA-256 em uma GitHub Release.

## Transição da v0.1

A `0.1.0-probe` foi assinada por um runner temporário. Antes de removê-la, exporte o diagnóstico. Depois:

1. desinstale a v0.1;
2. instale a primeira release assinada;
3. conceda novamente acesso às notificações e revise a bateria;
4. das próximas versões em diante, instale por cima sem desinstalar.

Perder a chave permanente impede futuras atualizações in-place.
