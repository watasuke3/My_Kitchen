# Phase 1: バックエンド Hello World

## ゴール
Play Framework のひな形を生成し、ローカルで起動して `http://localhost:9000` にアクセスできる状態にする。Scala / sbt / Play のディレクトリ構造を理解する。

## 学ぶこと
- **sbt new と giter8 テンプレート**: Scala プロジェクトの典型的な作り方
- **Play Framework のディレクトリ構造**: `app/`, `conf/`, `build.sbt` など各役割
- **ルーティング**: URL → コントローラ → アクションの流れ
- **sbt run の挙動**: ホットリロード（ファイル変更で自動再コンパイル）

---

## 0. プロジェクト配置の方針

```
My_Kitchen/
├── backend/         ← この Phase で作成（Play アプリ）
├── frontend/        ← Phase 2 で作成予定
├── docs/
├── scripts/
├── 学習の軌跡/
├── docker-compose.yml
└── ...
```

**理由**:
- バックエンドとフロントエンドを別ディレクトリに分けることで、それぞれのビルド・依存関係を独立に管理できる。
- 将来的にデプロイを別々の環境で行う想定。

---

## 1. PATH の修正（前提整備）

### 問題
WSL に Coursier 経由で sbt を入れたが、`which sbt` が `/mnt/c/Program Files (x86)/sbt/bin/sbt`（Windows 側）を指していた。
PATH の登録順が `[Windows-side sbt] : ... : [Coursier sbt]` となっており、シェルが先に見つかった Windows 側を優先していた。

### 修正
`~/.profile` の Coursier 関連の `export PATH` を **append から prepend に変更**:

```bash
# Before
export PATH="$PATH:$JAVA_HOME/bin"
export PATH="$PATH:/home/suke3/.local/share/coursier/bin"

# After
export PATH="$JAVA_HOME/bin:$PATH"
export PATH="/home/suke3/.local/share/coursier/bin:$PATH"
```

### 確認

```bash
$ bash -lc 'which sbt'
/home/suke3/.local/share/coursier/bin/sbt
```

✅ WSL 側 sbt が優先された。

---

## 2. Play Framework ひな形の生成

### コマンド

```bash
cd /home/suke3/My_Kitchen
sbt new playframework/play-scala-seed.g8 --name=backend
```

**理由・解説**:
- `sbt new <template>` は **giter8** という Scala プロジェクトテンプレート機構を使ってプロジェクトのひな形を生成する。
- `playframework/play-scala-seed.g8` は GitHub の `playframework/play-scala-seed.g8` リポジトリを参照する公式テンプレート。
- `--name=backend` でプロジェクト名（とディレクトリ名）を `backend` に指定。対話プロンプトをスキップできる。
- 結果として `My_Kitchen/backend/` 以下にひな形が生成された。

### 結果出力

```
Template applied in /home/suke3/My_Kitchen/./backend
```

---

## 3. 生成されたプロジェクト構造

```
backend/
├── app/                            ← アプリケーションコード（メイン）
│   ├── controllers/
│   │   └── HomeController.scala    ← ルートに対応するコントローラ
│   └── views/
│       ├── index.scala.html        ← トップページのテンプレート
│       └── main.scala.html         ← レイアウト用テンプレート
├── conf/                           ← 設定ファイル群
│   ├── application.conf            ← アプリ設定（DB接続、秘密鍵など）
│   ├── routes                      ← URL → コントローラのマッピング
│   ├── logback.xml                 ← ロギング設定
│   └── messages                    ← i18n 翻訳ファイル
├── public/                         ← 静的アセット（CSS, JS, 画像）
│   ├── images/
│   ├── javascripts/
│   └── stylesheets/
├── test/                           ← テストコード
│   └── controllers/
│       └── HomeControllerSpec.scala
├── project/                        ← sbt 自体の設定
│   ├── build.properties            ← sbt のバージョン
│   └── plugins.sbt                 ← sbt プラグイン（Play 含む）
├── target/                         ← ビルド成果物（gitignore）
└── build.sbt                       ← プロジェクトのビルド定義
```

### 主要ファイルの中身

#### `build.sbt`
```scala
name := """backend"""
organization := "com.example"
version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.18"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test
```

| 行 | 意味 |
|---|---|
| `name := """backend"""` | プロジェクト名 |
| `organization := "com.example"` | 組織名（Maven の groupId に使われる） |
| `version := "1.0-SNAPSHOT"` | バージョン |
| `enablePlugins(PlayScala)` | Play フレームワーク用の sbt プラグインを有効化 |
| `scalaVersion := "2.13.18"` | **Scala 2.13 系**（後述、Scala 3 への移行は要検討） |
| `libraryDependencies += guice` | Guice（DI コンテナ）を依存に追加 |
| `... % Test` | テスト時のみ使う依存 |

#### `project/plugins.sbt`
```scala
addSbtPlugin("org.playframework" % "sbt-plugin" % "3.0.10")
addSbtPlugin("org.foundweekends.giter8" % "sbt-giter8-scaffold" % "0.18.0")
```
- **sbt-plugin 3.0.10**: Play Framework 3.0.10 の sbt プラグイン（`enablePlugins(PlayScala)` のソース）
- **sbt-giter8-scaffold**: テンプレート用の補助プラグイン（実装段階では削除可）

#### `project/build.properties`
```
sbt.version=1.12.9
```
sbt 自体のバージョン。

#### `conf/routes`
```
GET     /                           controllers.HomeController.index()
GET     /assets/*file               controllers.Assets.versioned(path="/public", file: Asset)
```

**ルーティングの記法**:
- 1列目: HTTP メソッド（GET, POST, PUT, DELETE...）
- 2列目: URL パス
- 3列目: 呼び出すコントローラとメソッド

`/` への GET → `HomeController` の `index()` メソッドを呼ぶ。

#### `app/controllers/HomeController.scala`
```scala
package controllers

import javax.inject._
import play.api._
import play.api.mvc._

@Singleton
class HomeController @Inject()(val controllerComponents: ControllerComponents) extends BaseController {

  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }
}
```

**処理の流れ**:
1. `@Singleton` + `@Inject()`: Guice DI でシングルトンとしてインスタンス化
2. `extends BaseController`: Play のコントローラ基底クラス
3. `def index() = Action { ... }`: HTTP リクエストを処理する **アクション**
4. `Ok(views.html.index())`: 200 OK で `index.scala.html` テンプレートをレンダリング

**Scala 文法のポイント**（初学者向け）:
- `implicit request:`: 暗黙の引数。Action 本体内で他の Play 機能が `request` を自動的に参照できる。
- `Ok(...)`: 200 ステータスを返すヘルパー。`BadRequest(...)`, `NotFound(...)` などもある。
- `views.html.index()`: テンプレートが Scala 関数に自動コンパイルされる（Twirl エンジン）。

---

## 4. コンパイル & 起動

### コンパイル

```bash
cd /home/suke3/My_Kitchen/backend
sbt compile
```

**結果**:
```
[info] Version 3.0.10 running Java 21.0.10
[info] compiling 7 Scala sources and 1 Java source to .../target/scala-2.13/classes ...
[info] done compiling
[success] Total time: 17 s
```

✅ Play 3.0.10 + Java 21.0.10 + Scala 2.13 でコンパイル成功。

### 起動

```bash
sbt run
```

サーバー起動メッセージ:
```
--- (Running the application, auto-reloading is enabled) ---
INFO p.c.s.PekkoHttpServer - Listening for HTTP on /[0:0:0:0:0:0:0:0]:9000
(Server started, use Enter to stop and go back to the console...)
```

**ポイント**:
- **Pekko HTTP**（旧 Akka HTTP の後継）が HTTP サーバー本体。
- **auto-reloading**: ソースコードを変更すると次のリクエスト時に自動で再コンパイル＆再起動。
- 停止は `Enter` キー（または `Ctrl+D`）。

### 動作確認

```bash
$ curl -i http://localhost:9000/
HTTP/1.1 200 OK
Content-Type: text/html; charset=UTF-8
Content-Length: 437

<!DOCTYPE html>
<html lang="en">
    <head>
        <title>Welcome to Play</title>
    ...
    <h1>Welcome to Play!</h1>
```

✅ HTTP 200、`Welcome to Play!` ページがレンダリングされた。

---

## 5. 議論ポイント: Scala 2.13 vs Scala 3

### 現状
`play-scala-seed.g8` のデフォルトは **Scala 2.13.18**。一方、`docs/DESIGN.md` では Scala 3 採用を予定していた。

### Scala 2.13 のままにする場合
- 公式テンプレートでテスト済みの組み合わせ
- Play のチュートリアル・サンプルの大半が 2.13
- 学習のしやすさ：◎

### Scala 3 に切り替える場合
- DESIGN.md の方針に沿う
- より新しい構文（インデント構文、enum、Given/Using など）
- Play 3.0.x は Scala 3 対応済み（3.3.x LTS が推奨）
- 一部のライブラリの組み合わせで詰まる可能性

→ **次のターンで決定する**。

---

## 残タスク（Phase 1 内）

- [ ] Scala 2.13 / 3 の方針を確定
- [ ] 必要なら build.sbt を編集して Scala 3 化
- [ ] 自分の手でブラウザから localhost:9000 にアクセス確認
- [ ] Phase 2（フロントエンド）に進む前に、簡単な API エンドポイントを1つ追加して動作確認すると理解が深まる（オプション）

---

## トラブルシュート用メモ

### `which sbt` が Windows 側を指したら
- `~/.profile` の Coursier `export PATH` が prepend になっているか確認。
- 新しい WSL ターミナルで `bash -lc 'which sbt'` を実行して `/home/suke3/.local/share/coursier/bin/sbt` が出るか。

### `sbt run` が起動しない
- Java が見つからない場合: `echo $JAVA_HOME` でパスが正しいか確認。
- ポート衝突: 9000 が既に使われている場合 → `sbt 'run 9001'` で別ポート指定。

### ホットリロードが効かない
- ソース保存時に sbt コンソールにメッセージが出るはず。
- `sbt run` を停止 → `sbt clean` → `sbt run` で再起動。
