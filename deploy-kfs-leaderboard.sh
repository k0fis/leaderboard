#!/usr/bin/env bash
set -euo pipefail

###############################################################################
# deploy-kfs-leaderboard.sh
#
# Běží NA SERVERU. Stáhne poslední release z GitHubu a spustí server.
#
# Použití:
#   ./deploy-kfs-leaderboard.sh          # stáhne + deploy + start
#   ./deploy-kfs-leaderboard.sh download # jen stáhne, nespouští
###############################################################################

REPO="k0fis/leaderboard"
INSTALL_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_NAME="kfsLeaderboard.jar"
LAUNCHER="kfs-leaderboard.sh"

MODE="${1:-deploy}"

# --- 1. Zjistit poslední release ---

echo ">>> Zjišťuji poslední release z github.com/$REPO ..."

RELEASE_JSON=$(curl -sL "https://api.github.com/repos/$REPO/releases/latest")

TAG=$(echo "$RELEASE_JSON" | grep '"tag_name"' | head -1 | cut -d '"' -f 4)

if [ -z "$TAG" ]; then
  echo "CHYBA: Nepodařilo se zjistit poslední release."
  echo "       Zkontroluj https://github.com/$REPO/releases"
  exit 1
fi

echo "    Release: $TAG"

# --- 2. Stáhnout JAR ---

JAR_URL=$(echo "$RELEASE_JSON" | grep "browser_download_url.*\.jar" | head -1 | cut -d '"' -f 4)

if [ -z "$JAR_URL" ]; then
  echo "CHYBA: JAR nenalezen v releasu $TAG"
  exit 1
fi

echo ">>> Stahuji $JAR_NAME ..."
curl -sL -o "$INSTALL_DIR/$JAR_NAME.new" "$JAR_URL"
echo "    Staženo: $(du -h "$INSTALL_DIR/$JAR_NAME.new" | cut -f1)"

# --- 3. Stáhnout launcher ---

LAUNCHER_URL=$(echo "$RELEASE_JSON" | grep "browser_download_url.*$LAUNCHER" | head -1 | cut -d '"' -f 4)

if [ -n "$LAUNCHER_URL" ]; then
  echo ">>> Stahuji $LAUNCHER ..."
  curl -sL -o "$INSTALL_DIR/$LAUNCHER.new" "$LAUNCHER_URL"
  chmod +x "$INSTALL_DIR/$LAUNCHER.new"
  echo "    OK"
else
  echo "    WARN: $LAUNCHER nenalezen v releasu, ponechávám stávající"
fi

if [ "$MODE" = "download" ]; then
  echo ">>> Staženo do $INSTALL_DIR (download mode, nespouštím)"
  exit 0
fi

# --- 4. Zastavit běžící server ---

if [ -f "$INSTALL_DIR/$LAUNCHER" ]; then
  echo ">>> Zastavuji server ..."
  bash "$INSTALL_DIR/$LAUNCHER" stop || true
elif pgrep -f "$JAR_NAME" > /dev/null 2>&1; then
  echo ">>> Zastavuji běžící proces ..."
  pkill -f "$JAR_NAME" || true
  sleep 2
fi

# --- 5. Prohodit soubory ---

if [ -f "$INSTALL_DIR/$JAR_NAME" ]; then
  mv "$INSTALL_DIR/$JAR_NAME" "$INSTALL_DIR/$JAR_NAME.bak"
  echo "    Záloha: $JAR_NAME.bak"
fi
mv "$INSTALL_DIR/$JAR_NAME.new" "$INSTALL_DIR/$JAR_NAME"

if [ -f "$INSTALL_DIR/$LAUNCHER.new" ]; then
  mv "$INSTALL_DIR/$LAUNCHER.new" "$INSTALL_DIR/$LAUNCHER"
fi

# --- 6. Spustit ---

echo ">>> Spouštím server ..."
bash "$INSTALL_DIR/$LAUNCHER" start

echo ""
echo ">>> Deploy hotov! ($TAG)"
echo "    Status: $INSTALL_DIR/$LAUNCHER status"
echo "    Log:    $INSTALL_DIR/$LAUNCHER log"
