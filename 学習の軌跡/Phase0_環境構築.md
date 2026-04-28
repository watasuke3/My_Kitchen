# Phase 0: 環境構築

## ゴール
Scala バックエンド + TypeScript フロントエンドを開発するための基本ツールを WSL2 (Ubuntu) 上に揃える。

## 開発環境の方針
**WSL2 (Ubuntu 24.04) に統一**。Windows 側にも sbt があったが、Java が WSL 側に無いため動かない状態だった。混乱を避けるため Linux 側に揃える。本番想定が Linux なのでローカルもそれに揃える方針。

---

## 環境チェック結果（着手前）

| ツール | 状態 |
|---|---|
| Java | ❌ 未インストール |
| sbt | ⚠️ Windows 側にのみ存在（動作不可） |
| Node.js | ✅ v20.20.2 |
| npm | ✅ 10.8.2 |
| Docker | ❌ 未インストール |
| VSCode | ✅ 1.117.0 |
| git | ✅ 2.43.0 |

---

## インストール手順と理由

### 1. Coursier (`cs`) の取得

```bash
mkdir -p ~/.local/bin
curl -fL "https://github.com/coursier/coursier/releases/latest/download/cs-x86_64-pc-linux.gz" -o /tmp/cs.gz
gzip -df /tmp/cs.gz
mv /tmp/cs ~/.local/bin/cs
chmod +x ~/.local/bin/cs
```

**理由**:
- **Coursier** は Scala 公式が推奨する **Scala 環境のオールインワンインストーラ**。
- 通常 Java は `sudo apt install` で入れるが、本マシンの sudo はパスワードが必要で対話的処理になる。Coursier は **すべてユーザー空間（`~/.cache` や `~/.local`）に展開**するため sudo 不要。
- バージョン管理も容易（複数 JDK の切り替え、後から sbt/Scala のバージョンを差し替え等）。

### 2. JDK 21 + Scala ツール一括導入

```bash
~/.local/bin/cs setup --yes --jvm temurin:21
```

**理由**:
- `cs setup` は **JDK + sbt + scala + scala-cli + scalafmt + Ammonite** を一気にインストールする。
- `--jvm temurin:21` で **Eclipse Adoptium Temurin の JDK 21 (LTS)** を指定。
    - Temurin: AdoptOpenJDK の後継。Java エコシステムで最も標準的なディストリビューション。
    - 21: 現行の LTS（長期サポート）版。Scala 3 が公式サポート。
- `--yes` で確認プロンプトをスキップ。
- 副作用: `~/.profile` に `JAVA_HOME` と `PATH` 追記、`~/.local/share/coursier/bin/` にツールへのシンボリックリンク作成。

### 3. `.bashrc` から `.profile` を読み込む追加設定

```bash
echo '
# Load JVM and coursier env from ~/.profile (added by Phase 0 setup)
if [ -f "$HOME/.profile" ]; then
  . "$HOME/.profile"
fi' >> ~/.bashrc
```

**理由**:
- `cs setup` は環境変数を `~/.profile` に書き込む。
- `~/.profile` は **ログインシェル**でのみ読まれる。
- WSL を Windows のターミナルから起動するときは通常ログインシェルだが、**VSCode の統合ターミナル等は非ログインシェル**で起動することがある。
- 念のため `.bashrc` から `.profile` を読み込むようにし、対話シェルではほぼ常に PATH が通る状態にした。

---

## 動作確認

```bash
$ java -version
openjdk version "21.0.10" 2026-01-20 LTS

$ scala -version
Scala code runner version: 1.12.4
Scala version (default): 3.8.3

$ sbt --script-version
1.11.6
```

✅ 全て期待通りに動作。

---

## インストールされた Scala ツール一覧

`~/.local/share/coursier/bin/` 配下:

| コマンド | 用途 |
|---|---|
| `cs` | Coursier 本体（パッケージ管理） |
| `coursier` | `cs` のエイリアス |
| `sbt` / `sbtn` | Scala 標準のビルドツール |
| `scala` / `scalac` | Scala REPL / コンパイラ |
| `scala-cli` | スクリプト実行に便利な軽量 CLI |
| `scalafmt` | コードフォーマッタ |
| `amm` | Ammonite（高機能 REPL） |

---

## 4. Docker Engine の WSL 直接インストール

### 方針判断
**Docker Desktop は使わず、Docker Engine を WSL 内に直接インストールする。**

**理由**:
- Docker Desktop は個人利用なら無料だが、将来的にライセンス制約（従業員250人以上 or 売上 $10M 以上の組織で有料）にぶつかる可能性。
- Docker Engine（CLI）は Apache 2.0 ライセンスで完全無料、用途制限なし。
- WSL 内に直接入れるとメモリ使用量も軽くなる（Docker Desktop の Windows 側 GUI が不要）。
- 今回 Ubuntu 24.04 on WSL2 で **systemd が有効**（`/etc/wsl.conf` で `systemd=true`）なので、`systemctl enable --now docker` でサービス常駐できる。

### 前提確認（実施済み）

```bash
$ cat /etc/wsl.conf
[boot]
systemd=true

$ ps -p 1 -o comm=
systemd
```

→ systemd が PID 1 で動作。Linux と同じ感覚で `systemctl` が使える。

### 実行スクリプト

手順をまとめた **`scripts/install-docker.sh`** を用意済み。

```bash
bash scripts/install-docker.sh
```

スクリプト内の処理は以下と同等。学習目的で中身を理解したい場合は、スクリプトを開きながら以下と照らし合わせること。

### 実行コマンド（スクリプトの中身）

```bash
# 1. Docker 公式 apt リポジトリの登録
sudo apt-get update
sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update

# 2. Docker Engine + Compose プラグインのインストール
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 3. systemd で常駐させる
sudo systemctl enable --now docker

# 4. 現ユーザーを docker グループに追加（sudo なしで docker コマンドを使うため）
sudo usermod -aG docker $USER
```

### コマンドの意味

| コマンド | 役割 |
|---|---|
| `apt-get update` | パッケージ情報を最新化 |
| `install ca-certificates curl` | HTTPS でリポジトリにアクセスする前提 |
| `install -m 0755 -d /etc/apt/keyrings` | Docker GPG 鍵を置くディレクトリを作成 |
| `curl ... -o /etc/apt/keyrings/docker.asc` | Docker 公式の GPG 公開鍵を取得 |
| `echo "deb ... " | sudo tee /etc/apt/sources.list.d/docker.list` | Docker 公式 apt リポジトリを登録 |
| `install docker-ce ...` | Docker Engine、CLI、containerd、Compose プラグインをインストール |
| `systemctl enable --now docker` | サービス自動起動 + 即時起動 |
| `usermod -aG docker $USER` | docker グループにユーザー追加 |

### 反映のため WSL 再起動

グループ変更は再ログインで反映される。Windows 側 PowerShell で:

```powershell
wsl --shutdown
```

その後 WSL ターミナルを開き直す。

### 動作確認

```bash
docker --version            # → Docker version 28.x.x
docker compose version      # → Docker Compose version v2.x.x
docker run --rm hello-world # → "Hello from Docker!"
```

---

## 残タスク（Phase 0 内）

- [ ] **Docker Engine インストール**（ユーザー側で実施中）
- [ ] **VSCode の Metals 拡張インストール**（ユーザー側で実施）
    - VSCode → 拡張機能 → "Metals" で検索 → "Scala (Metals)" をインストール
- [ ] **PostgreSQL 用の docker-compose.yml 作成**（Docker 導入後に着手）
