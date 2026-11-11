#!/usr/bin/env bash
# Сборка Fitbit-приложения. Требуется Node 18+ и Fitbit SDK.
set -e
cd "$(dirname "$0")"

NODE_BIN="${NODE:-node}"
if ! command -v "$NODE_BIN" >/dev/null 2>&1; then
  echo "Не найден Node.js. Установите Node 18+ или задайте NODE=/path/to/node"
  exit 1
fi

echo "[1/3] синхронизация ядра"
cp core.mjs app/core.js
cp core.mjs companion/core.js

echo "[2/3] тесты логики"
"$NODE_BIN" test-core.mjs

echo "[3/3] сборка пакета"
if [ -x node_modules/.bin/fitbit-build ]; then
  node_modules/.bin/fitbit-build
  echo "готово: build/*.fba"
else
  cat <<'TXT'

Fitbit SDK не установлен — .fba здесь не собрать.
Варианты:
  1) npm install && npm run build      (нужен доступ к npm и Node 18+)
  2) открыть эту папку в Fitbit Studio и собрать там
  3) установить приложение на часы через Developer Bridge:
     npx fitbit-build && npx fitbit
См. README.md → «Сборка и установка».
TXT
fi
