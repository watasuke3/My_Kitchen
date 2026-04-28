#!/usr/bin/env bash
# ============================================================
# Docker Engine インストールスクリプト
# 対象環境: Ubuntu 24.04 (WSL2 / systemd 有効)
# 実行方法: bash scripts/install-docker.sh
# 詳細解説:  学習の軌跡/Phase0_環境構築.md を参照
# ============================================================
#
# このスクリプトは以下を実行する:
#   1. Docker 公式 apt リポジトリの登録（GPG 鍵 + sources.list）
#   2. Docker Engine + CLI + containerd + Compose プラグインのインストール
#   3. systemd で docker サービスを自動起動
#   4. 現ユーザーを docker グループに追加（sudo なしで docker コマンドを使うため）
#
# 注意:
# - 各 sudo コマンドでパスワードを求められる場合があります。
# - 完了後は WSL を再起動（`wsl --shutdown` → 開き直し）してください。
# ============================================================

set -euo pipefail

# 色付き出力用ヘルパー
log() { printf "\n\033[1;36m=== %s ===\033[0m\n" "$1"; }

# --- 前提チェック ---
log "[0/5] 前提条件の確認"
if ! grep -q "Ubuntu" /etc/os-release; then
  echo "⚠️  Ubuntu 以外のディストリビューションでは動作未確認です。"
fi
if [ "$(ps -p 1 -o comm=)" != "systemd" ]; then
  echo "⚠️  PID 1 が systemd ではありません。WSL の systemd 設定を確認してください。"
fi
echo "OK"

# --- ステップ 1: 公式 apt リポジトリの登録 ---
log "[1/5] Docker 公式 apt リポジトリの登録"
sudo apt-get update
sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

# --- ステップ 2: sources.list へ追加 ---
log "[2/5] sources.list に Docker リポジトリを追加"
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update

# --- ステップ 3: Docker 本体のインストール ---
log "[3/5] Docker Engine + Compose プラグインをインストール"
sudo apt-get install -y \
  docker-ce \
  docker-ce-cli \
  containerd.io \
  docker-buildx-plugin \
  docker-compose-plugin

# --- ステップ 4: サービスの自動起動 ---
log "[4/5] systemd で docker サービスを有効化＆起動"
sudo systemctl enable --now docker

# --- ステップ 5: docker グループへの追加 ---
log "[5/5] 現ユーザー ($USER) を docker グループに追加"
sudo usermod -aG docker "$USER"

# --- 完了メッセージ ---
cat <<'EOF'

============================================================
✅ Docker Engine インストール完了
============================================================

次に行うこと:

1. Windows 側の PowerShell で WSL を再起動
   PS> wsl --shutdown

2. WSL ターミナルを開き直す

3. 動作確認:
   $ docker --version
   $ docker compose version
   $ docker run --rm hello-world

すべて成功すれば Phase 0 完了の準備が整います。
詳細は 学習の軌跡/Phase0_環境構築.md を参照。
============================================================
EOF
