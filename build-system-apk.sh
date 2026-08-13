#!/usr/bin/env bash
# Сборка системной версии лаунчера.
#
# Зачем отдельный скрипт: Gradle подписывает JKS-хранилищем, но ключ platform
# из AOSP использует MD5withRSA, который современный keytool считает
# небезопасным и отказывается с ним работать при верификации. Поэтому
# подписываем APK напрямую парой pk8+pem — ровно так, как это делает
# сборочная система Android.
set -e

cd "$(dirname "$0")"
export JAVA_HOME=${JAVA_HOME:-/home/user/.cache/jdk17}
export PATH=$JAVA_HOME/bin:$PATH
export ANDROID_HOME=${ANDROID_HOME:-/home/user/.cache/android-sdk}
BT=$ANDROID_HOME/build-tools/34.0.0
KEYS=${KEYS:-$(pwd)/keys}

echo "==> Сборка"
./gradlew :app:assembleSystemRelease

RAW=app/build/outputs/apk/system/release/app-system-release.apk

echo "==> Выравнивание"
$BT/zipalign -f 4 "$RAW" /tmp/aligned.apk

echo "==> Подпись ключом platform (AOSP)"
$BT/apksigner sign \
  --key "$KEYS/platform.pk8" \
  --cert "$KEYS/platform.x509.pem" \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out CarLauncher-SYSTEM.apk /tmp/aligned.apk

echo "==> Проверка"
$BT/apksigner verify --print-certs CarLauncher-SYSTEM.apk | grep -E "SHA-256 digest|DN"

echo "Готово: CarLauncher-SYSTEM.apk"
