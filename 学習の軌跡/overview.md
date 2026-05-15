# My Kitchen 開発・学習ロードマップ

献立アプリ "My Kitchen" の開発を通じて Scala / React / PostgreSQL を学ぶプロジェクトの全体記録。

---

## プロジェクト概要

ユーザーの身体情報（性別・身長・体重・年齢・活動レベル）に基づいて最適な献立を作成・管理する Web アプリ。

| レイヤ | 技術 |
|---|---|
| バックエンド | Scala 2.13 + Play Framework 3.0 |
| フロントエンド | TypeScript + React 19 + Vite 8 |
| DB | PostgreSQL 17 |
| DBマイグレーション | Flyway |
| 開発環境 | WSL2 (Ubuntu 24.04) |

---

## 進捗状況

### ✅ Phase 0: 環境構築（完了）
**何をやったか**
- Coursier 経由で Java 21 (Temurin) と sbt をインストール
- WSL の PATH を修正（Windows 側の sbt より WSL 側を優先させる）
- Docker Engine を WSL に直接インストール（Docker Desktop は不使用）
- PostgreSQL 17 を Docker Compose で起動できる状態にした
- nvm 経由で Node.js 22 をインストール

**学んだこと**
- `sudo` 不要のツール管理（Coursier / nvm）
- WSL と Windows の PATH 優先順位の仕組み
- Docker Engine と Docker Desktop の違い（ライセンス・構成）
- `docker-compose.yml` と `.env` ファイルによる認証情報の分離

---

### ✅ Phase 1: バックエンド Hello World（完了）
**何をやったか**
- `sbt new playframework/play-scala-seed.g8` で Play Framework のひな形を生成
- `sbt run` でサーバーを起動し `http://localhost:9000` で表示確認
- Scala のバージョンを 3 → 2.13 に変更（公式テンプレートのデフォルト・情報量の多さを優先）

**学んだこと**
- Play Framework のディレクトリ構造（`app/`, `conf/`, `build.sbt` 等）
- ルーティングの仕組み（`routes` ファイル → コントローラ → アクション）
- Guice による DI（`@Singleton`, `@Inject`）
- Twirl テンプレートエンジン
- `sbt run` のホットリロード

---

### ✅ Phase 2: フロントエンド Hello World（完了）
**何をやったか**
- `npm create vite@latest frontend -- --template react-ts` でひな形を生成
- `npm run dev` でサーバーを起動し `http://localhost:5173` で表示確認
- カウントボタンの動作確認（React の状態管理が動いていることを確認）

**学んだこと**
- Vite の役割（開発サーバー + ビルドツール）
- React コンポーネントの基本構造（関数コンポーネント + JSX）
- `useState` による状態管理の基本
- HMR（Hot Module Replacement）による即時反映

---

### ✅ Phase 3: フロントとバックを HTTP で繋ぐ（完了）
**何をやったか**
- バックエンドに `GET /api/health` エンドポイントを追加（JSON レスポンス）
- Play Framework に CORS 設定を追加
- フロントエンドから `fetch` で `/api/health` を呼び出し、結果を画面に表示

**学んだこと**
- Play での JSON レスポンス（`Json.obj`、`play.api.libs.json`）
- CORS の仕組み（異なるオリジン間の通信制御）
- `localhost` と `127.0.0.1` は CORS の判定では別物
- React の `useEffect`（初回レンダリング時の副作用処理）
- `fetch` API と Promise チェーン（`.then().catch()`）

---

## 今後の開発・学習計画

### 🔲 Phase 4: ユーザー登録・ログイン
**ゴール**: メールアドレス + パスワードで登録・ログインができる状態

**やること（バックエンド）**
- PostgreSQL を Docker で起動
- Flyway でスキーママイグレーション（`users` テーブル作成）
- Slick でDB接続・ユーザー取得
- パスワードのハッシュ化（BCrypt）
- セッション or JWT による認証状態の管理
- API: `POST /api/register`、`POST /api/login`、`POST /api/logout`

**やること（フロントエンド）**
- 登録フォーム・ログインフォームの作成
- React Router でページ遷移（ログイン前後の画面切り替え）
- ログイン状態の管理（Context or TanStack Query）

**学ぶこと**
- Slick（Scala の DB アクセスライブラリ）の基本
- Flyway によるスキーマバージョン管理
- パスワードのセキュアな扱い（平文保存は絶対 NG）
- Cookie セッション vs JWT の違いと選択
- React Router v6 の基本

---

### 🔲 Phase 5: レシピの CRUD
**ゴール**: レシピを登録・一覧表示・編集・削除できる状態

**やること**
- `recipes` テーブルの設計・マイグレーション
- バックエンド: CRUD の REST API 実装
- フロントエンド: TanStack Query でデータ取得・キャッシュ管理
- フォームバリデーション（React Hook Form + Zod）

**学ぶこと**
- REST API の設計（リソース指向）
- Slick でのテーブル定義・クエリ記述
- TanStack Query（サーバーステート管理の標準ライブラリ）
- Zod による型安全なバリデーション

---

### 🔲 Phase 6: 献立カレンダー / 買い物リスト
**ゴール**: カレンダーに献立を割り当て、買い物リストを自動生成できる状態

**やること**
- `meal_plans`、`shopping_lists` テーブルの設計
- カレンダー UI の実装
- 買い物リストの食材合算ロジック

**学ぶこと**
- 関連テーブルの設計（JOIN、外部キー）
- 集計クエリ
- カレンダー UI ライブラリの選定と活用

---

### 🔲 Phase 7: 栄養計算・フィードバック
**ゴール**: 献立の栄養情報を表示し、目標カロリーとの比較ができる状態

**やること**
- Ganpule の式による BMR 計算
- PAL（活動レベル係数）を掛けた必要カロリー計算
- PFC バランスの表示

**学ぶこと**
- 計算ロジックの Scala での実装
- グラフ・数値表示の UI

---

### 🔲 Phase 8: アレルギー除外・人数スケーリング・重複回避
**ゴール**: アレルギー対応と食材量の自動調整ができる状態

---

### 🔲 Phase 9（将来）: 本番デプロイ
**やること（予定）**
- HTTPS 化（Nginx / CDN）
- デプロイ先の選定（Render / Fly.io / VPS 等）
- 環境変数の本番用管理

---

## 技術的な未確定事項

| 項目 | 決定時期の目安 |
|---|---|
| 認証方式（Cookie セッション or JWT） | Phase 4 |
| UI コンポーネントライブラリ（shadcn/ui or Mantine 等） | Phase 4〜5 |
| レシピデータのソース（自前投入 or 外部 API） | Phase 5 着手前 |
| PFC バランスの比率 | Phase 7 |
| デプロイ先 | Phase 8 以降 |

---

## 各 Phase のファイル

| Phase | ファイル |
|---|---|
| Phase 0 | [Phase0_環境構築.md](Phase0_環境構築.md) |
| Phase 1 | [Phase1_バックエンドHelloWorld.md](Phase1_バックエンドHelloWorld.md) |
| Phase 2 | [Phase2_フロントエンドHelloWorld.md](Phase2_フロントエンドHelloWorld.md) |
| Phase 3 | [Phase3_フロントとバックを繋ぐ.md](Phase3_フロントとバックを繋ぐ.md) |
