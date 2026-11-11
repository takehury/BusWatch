#!/bin/bash
# Пакет Tizen (.wgt) для Galaxy Watch 3 + прогон тестов логики.
# ВАЖНО: для установки на часы пакет нужно подписать сертификатом Samsung
# (Tizen Studio → Certificate Manager). Здесь создаётся НЕподписанный .wgt.
set -euo pipefail
cd "$(dirname "$0")"

NODE="${NODE:-/Users/k/.dsh/dsh-runtimes/dsh-primary-runtime/dependencies/node/bin/node}"

echo "[1/3] тесты логики"
"$NODE" test-core.js

echo "[2/3] сборка .wgt"
rm -rf build dist
mkdir -p dist
cp -R app build
(cd build && zip -q -r ../dist/KrskBusWatch-tizen-1.0.0.wgt . -x "*.DS_Store")

echo "[3/3] готово"
ls -lh dist/KrskBusWatch-tizen-1.0.0.wgt
echo
echo "Пакет НЕ подписан. Подпишите его в Tizen Studio (Certificate Manager)"
echo "или командой: tizen package -t wgt -s <profile> -- ."
