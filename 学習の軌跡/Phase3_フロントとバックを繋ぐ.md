# Phase 3: フロントとバックを HTTP で繋ぐ

## ゴール
バックエンドに JSON を返す API エンドポイントを追加し、フロントエンドからそれを `fetch` で呼び出して画面に表示する。CORS の設定を理解する。

## 学ぶこと
- **Play Framework での JSON レスポンス**: `Json.obj` で JSON を返す
- **CORS**: 異なるオリジン間のリクエストをブラウザが制御する仕組み
- **React の `useEffect`**: コンポーネント表示時に一度だけ処理を実行する
- **React の `useState` + 非同期**: fetch の結果で画面を更新する

---

## 1. バックエンド: JSON エンドポイントの追加

### `conf/routes` に追加
```
GET     /api/health                 controllers.HomeController.health()
```

### `app/controllers/HomeController.scala` に追加
```scala
package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._

@Singleton
class HomeController @Inject()(val controllerComponents: ControllerComponents) extends BaseController {

  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }

  def health() = Action {
    Ok(Json.obj("status" -> "ok"))
  }
}
```

**解説**:
- `play.api.libs.json._` をインポートすることで `Json.obj` が使える
- `Json.obj("status" -> "ok")` は `{"status": "ok"}` という JSON を生成する
- `Ok(...)` に JSON を渡すと `Content-Type: application/json` が自動でセットされる
- `Action { ... }` の引数に `implicit request` が不要な場合は省略できる

---

## 2. バックエンド: CORS 設定

### CORS とは
ブラウザのセキュリティ機能。**異なるオリジン**（ホスト・ポートの組み合わせ）へのリクエストを、サーバーが明示的に許可しないとブラウザがブロックする。

今回の構成:
- フロントエンド: `http://localhost:5173`（または `http://127.0.0.1:5173`）
- バックエンド: `http://localhost:9000`

ポートが違うので「異なるオリジン」扱いになる。Play 側で許可設定が必要。

### `conf/application.conf` に追加
```hocon
play.filters.enabled += "play.filters.cors.CORSFilter"

play.filters.cors {
  allowedOrigins = ["http://localhost:5173", "http://127.0.0.1:5173"]
  allowedHttpMethods = ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
  allowedHttpHeaders = ["Content-Type", "Authorization"]
}
```

### トラブル: `localhost` と `127.0.0.1` は別物
CORS の許可リストは文字列の完全一致で判定される。ブラウザが内部的に `127.0.0.1` を使う場合があるため、両方を許可リストに入れる必要があった。

```
WARN play.filters.cors.CORSFilter  Invalid CORS request;Origin=Some(http://127.0.0.1:5173)
```

このエラーが出た場合は `allowedOrigins` に `http://127.0.0.1:5173` を追加する。

---

## 3. フロントエンド: fetch でバックエンドを呼び出す

### `src/App.tsx` の変更点
```tsx
import { useState, useEffect } from 'react'

function App() {
  const [count, setCount] = useState(0)
  const [backendStatus, setBackendStatus] = useState<string>('確認中...')

  useEffect(() => {
    fetch('http://localhost:9000/api/health')
      .then((res) => res.json())
      .then((data) => setBackendStatus(data.status))
      .catch(() => setBackendStatus('接続失敗'))
  }, [])

  return (
    <>
      <p>バックエンド: {backendStatus}</p>
      ...
    </>
  )
}
```

**解説**:

| コード | 意味 |
|---|---|
| `useEffect(() => { ... }, [])` | コンポーネントが画面に表示されたとき1回だけ実行する。`[]` は「依存なし＝初回のみ」の意味 |
| `fetch('http://...')` | HTTP GET リクエストを送る。Promise を返す |
| `.then((res) => res.json())` | レスポンスを JSON としてパース |
| `.then((data) => setBackendStatus(data.status))` | パースした JSON の `status` フィールドで状態を更新 → 再レンダリング |
| `.catch(() => setBackendStatus('接続失敗'))` | エラー時（バックが落ちている等）のフォールバック |
| `useState<string>('確認中...')` | TypeScript の型指定。`string` 型の状態変数 |

---

## 4. 動作確認

### 起動手順
ターミナルを2つ開いて同時に起動する。

**ターミナル①（バックエンド）**:
```bash
cd /home/suke3/My_Kitchen/backend
sbt run
```

**ターミナル②（フロントエンド）**:
```bash
cd /home/suke3/My_Kitchen/frontend
nvm use 22   # Node 22 に切り替え（apt の Node 18 では Vite 8 が動かないため）
npm run dev
```

### 確認
`http://localhost:5173` を開いて **「バックエンド: ok」** と表示されれば成功。

---

## 5. Node.js バージョンに関するメモ

`sudo apt install npm` で入る Node.js は v18 系（Ubuntu 24.04 のリポジトリ）。  
Vite 8 は Node.js 20 以上が必要なため、nvm 経由の Node 22 を使う必要がある。

```bash
source ~/.bashrc   # nvm を読み込む
nvm use 22         # Node 22 に切り替え
node --version     # v22.x.x であることを確認
```

新しいターミナルを開くたびに `nvm use 22` が必要な場合は、`~/.bashrc` に以下を追記することで自動化できる:
```bash
nvm use 22 --silent
```

---

## 残タスク（Phase 3 内）

- [x] バックエンドに `GET /api/health` エンドポイントを追加
- [x] Play Framework の CORS 設定
- [x] フロントエンドから `fetch` で呼び出し
- [x] 「バックエンド: ok」の表示確認
