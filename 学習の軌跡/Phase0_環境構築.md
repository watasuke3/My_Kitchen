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

## 残タスク（Phase 0 内）

- [ ] **Docker Desktop for Windows のインストール**（ユーザー側で実施）
    - https://www.docker.com/products/docker-desktop/ から DL
    - 設定で「Enable integration with WSL」を ON
- [ ] **VSCode の Metals 拡張インストール**（ユーザー側で実施）
    - VSCode → 拡張機能 → "Metals" で検索 → "Scala (Metals)" をインストール
- [ ] **PostgreSQL 用の docker-compose.yml 作成**（Docker 導入後に着手）
