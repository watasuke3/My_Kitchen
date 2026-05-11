# Phase 2: フロントエンド Hello World

## ゴール
Vite + React + TypeScript のひな形を生成し、ローカルで起動して `http://localhost:5173` にアクセスできる状態にする。フロントエンドのディレクトリ構造と React の基本的な仕組みを理解する。

## 学ぶこと
- **Vite**: フロントエンドのビルドツール・開発サーバー
- **React**: UI コンポーネント単位で画面を組み立てる JavaScript ライブラリ
- **TSX**: TypeScript の中に HTML っぽい記法（JSX）を混在させた拡張構文
- **useState**: コンポーネント内で状態（変数）を管理する React の仕組み
- **HMR (Hot Module Replacement)**: ファイルを保存すると即座にブラウザに反映される開発体験

---

## 0. プロジェクト配置の方針

```
My_Kitchen/
├── backend/         ← Phase 1 で作成（Play アプリ）
├── frontend/        ← この Phase で作成（React アプリ）
├── docs/
├── 学習の軌跡/
└── ...
```

バックエンドと同様、フロントエンドも独立ディレクトリで管理することで、それぞれのビルドや依存関係が干渉しない。

---

## 1. Node.js のインストール（nvm 経由）

### なぜ nvm を使うか
- `sudo` 不要でユーザーホームにインストールできる（Coursier で Scala を入れたのと同じ思想）
- Node のバージョンを後から切り替えられる
- WSL と相性が良い

### コマンド
```bash
# nvm をインストール
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.3/install.sh | bash

# シェルに nvm を読み込む
source ~/.bashrc

# Node.js 22 LTS をインストール
nvm install 22
```

### 確認
```
$ node --version
v22.22.2
$ npm --version
10.9.7
```

✅ Node.js 22 LTS と npm 10 が WSL に入った。

---

## 2. Vite ひな形の生成

### コマンド
```bash
cd /home/suke3/My_Kitchen
npm create vite@latest frontend -- --template react-ts
```

**解説**:
- `npm create vite@latest`: Vite の公式プロジェクト生成ツールを実行
- `frontend`: 生成するディレクトリ名
- `--template react-ts`: React + TypeScript テンプレートを使用

### 生成されたバージョン
- React: 19.2.6
- TypeScript: 6.0.2
- Vite: 8.0.12

---

## 3. 生成されたプロジェクト構造

```
frontend/
├── src/                    ← アプリケーションコード（メイン）
│   ├── App.tsx             ← ルートコンポーネント
│   ├── App.css             ← App コンポーネント用スタイル
│   ├── main.tsx            ← エントリーポイント
│   ├── index.css           ← グローバルスタイル
│   └── assets/             ← 静的ファイル（ロゴ画像等）
├── public/                 ← 直接配信される静的ファイル
├── index.html              ← HTML テンプレート（React の mount 先）
├── package.json            ← 依存関係・スクリプト定義
├── vite.config.ts          ← Vite 設定
├── tsconfig.json           ← TypeScript 設定（全体）
├── tsconfig.app.json       ← TypeScript 設定（アプリ本体用）
└── tsconfig.node.json      ← TypeScript 設定（Vite 設定ファイル用）
```

### 主要ファイルの中身

#### `src/main.tsx`（エントリーポイント）
```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
```

**処理の流れ**:
1. `index.html` の `<div id="root">` を React の管理下に置く
2. `<App />` コンポーネントをその中にレンダリング
3. `StrictMode`: 開発中に潜在的な問題を検出して警告してくれるラッパー

#### `src/App.tsx`（ルートコンポーネント）
```tsx
import { useState } from 'react'

function App() {
  const [count, setCount] = useState(0)  // ← 状態管理

  return (
    <>
      <button onClick={() => setCount((count) => count + 1)}>
        Count is {count}
      </button>
    </>
  )
}

export default App
```

**React の基本概念**:

| 概念 | 意味 |
|---|---|
| `function App()` | React コンポーネント。関数が JSX を返す |
| `useState(0)` | 初期値 0 の状態変数 `count` と更新関数 `setCount` を作る |
| `onClick={...}` | クリックイベントのハンドラ（関数を渡す） |
| `{count}` | JSX 内で JavaScript の値を埋め込む記法 |
| `<>...</>` | Fragment。余計な `<div>` を生まずに複数要素をまとめる |

**Scala との対比**（理解の助けに）:
- `useState` は Scala の `var` に近いが、値を直接書き換えず必ず `setCount` 経由で更新する
- `setCount` を呼ぶと React がコンポーネントを再レンダリング（再描画）する

---

## 4. 依存関係インストール & 起動

```bash
cd /home/suke3/My_Kitchen/frontend
npm install
npm run dev
```

起動メッセージ:
```
  VITE v8.x.x  ready in Xms

  ➜  Local:   http://localhost:5173/
  ➜  Network: use --host to expose
```

### 動作確認

- `http://localhost:5173/` をブラウザで開く
- Vite + React のロゴと `Count is 0` ボタンが表示される
- ボタンをクリック（または Enter キー）するとカウントが増える

✅ React の状態管理（useState）が動作していることを確認。

**Enter キーでもカウントが増える理由**:
ボタンにフォーカスがある状態では Enter がクリックと同等に扱われる — ブラウザの標準動作。アクセシビリティの観点から正しい挙動。

---

## 5. バックエンドとの対比

| | バックエンド | フロントエンド |
|---|---|---|
| 言語 | Scala 2.13 | TypeScript |
| フレームワーク | Play Framework 3.0 | React 19 |
| ビルドツール | sbt | Vite |
| 起動コマンド | `sbt run` | `npm run dev` |
| ポート | 9000 | 5173 |
| ホットリロード | あり（次リクエスト時に再コンパイル） | あり（HMR で即時反映） |

---

## 残タスク（Phase 2 内）

- [x] Node.js (nvm 経由) のインストール
- [x] `npm create vite@latest` でひな形生成
- [x] `npm run dev` で起動確認
- [x] ブラウザで localhost:5173 表示確認
- [x] カウントボタンの動作確認

---

## トラブルシュート用メモ

### `node: command not found` が出たら
- `source ~/.bashrc` を実行して nvm を読み込む
- または新しい WSL ターミナルを開く

### ポート 5173 が既に使われている
- `npm run dev -- --port 5174` で別ポートを指定

### npm install が遅い
- 初回のみ遅い。node_modules のダウンロードに時間がかかる。
