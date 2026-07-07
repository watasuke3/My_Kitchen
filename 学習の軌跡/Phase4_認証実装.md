# Phase 4: 認証実装 — 学習の軌跡

## 概要

Phase 4 では Cookie Session 方式の認証を実装した。
バックエンド(Scala / Play Framework) と フロントエンド(React / TypeScript) の両方に変更を加えた。

---

## Scala 言語の学習ポイント

### 1. sealed trait と case object — エラー型の設計

```scala
// AuthService.scala より
sealed trait AuthError
case object EmailAlreadyExists extends AuthError
case object InvalidCredentials extends AuthError
case object AccountLocked      extends AuthError
case class  UnexpectedError(msg: String) extends AuthError
```

**Java との比較:**

| Java | Scala |
|------|-------|
| `enum` (値のみ保持) | `sealed trait` + `case object` |
| `Exception` のサブクラス | `case class UnexpectedError(msg: String)` でデータも持てる |
| instanceof + cast | パターンマッチで安全に分岐 |

**sealed とは:** そのファイル外でサブクラスを定義できない宣言。
コンパイラが「すべてのサブタイプを把握している」ので、match が網羅的でない場合に警告を出せる。

```scala
// match が網羅的かどうかをコンパイル時に検証できる
error match {
  case EmailAlreadyExists    => ...
  case InvalidCredentials    => ...
  case AccountLocked         => ...
  case UnexpectedError(msg)  => ...
  // ← ここを書き忘れるとコンパイラが警告
}
```

---

### 2. Either 型 — 例外を使わないエラー処理

```scala
def register(email: String, password: String): Future[Either[AuthError, String]]
```

`Either[L, R]` は「左(L)かRight(R)のどちらか」を表す型。
- `Right(value)` → 成功
- `Left(error)` → 失敗

**なぜ例外を使わないのか?**
- 例外はシグネチャに現れないため、呼び出し元が忘れやすい
- `Either` は戻り値の型にエラーが明示されるので、コンパイラが強制する

**Java との比較:**

```java
// Java: 例外を使う場合
String register(String email, String password) throws EmailAlreadyExistsException { ... }

// 呼び出し側でtry-catchを書き忘れてもコンパイルエラーにならない
```

```scala
// Scala: Either を使う場合
// 呼び出し側は必ず Left/Right を処理しないといけない（コンパイラが保証）
authService.register(email, password).map {
  case Right(sessionId) => Created(...)
  case Left(EmailAlreadyExists) => Conflict(...)
}
```

---

### 3. Future — 非同期処理

```scala
def register(...): Future[Either[AuthError, String]] = {
  userRepo.findByEmail(email).flatMap {
    case None =>
      sessionRepo.create(...).map(Right(_))
    case Some(_) =>
      Future.successful(Left(EmailAlreadyExists))
  }
}
```

**Java との比較:**

| Java | Scala |
|------|-------|
| `CompletableFuture<T>` | `Future[T]` |
| `.thenCompose()` | `.flatMap()` |
| `.thenApply()` | `.map()` |
| `CompletableFuture.completedFuture(x)` | `Future.successful(x)` |

- `map`: 成功した値を変換する (Future の中身を加工する)
- `flatMap`: 成功した値を使って次の Future を返す (非同期処理を連鎖させる)

---

### 4. @Inject と Singleton — 依存性注入(DI)

```scala
@Singleton
class AuthService @Inject()(
  userRepo: UserRepository,
  sessionRepo: SessionRepository
)(implicit ec: ExecutionContext) { ... }
```

**Java との比較:**

| Java (Spring) | Scala (Play + Guice) |
|---------------|----------------------|
| `@Service` | `@Singleton` |
| `@Autowired` | `@Inject` (コンストラクタに書く) |
| `@Autowired ExecutorService exec` | `implicit ec: ExecutionContext` |

`implicit` は「暗黙的に渡す」という意味。
`ExecutionContext` はスレッドプールの設定で、Future を実行するために必要。
Play が自動で用意したものを `implicit` で受け取るのが慣習。

---

### 5. Play Framework のアーキテクチャ

```
HTTP リクエスト
  → Router (conf/routes)        ← URLとメソッドのマッピング
  → Controller (AuthController) ← リクエスト/レスポンスの処理
  → Service (AuthService)       ← ビジネスロジック
  → Repository (UserRepository) ← DB アクセス
  → DB (PostgreSQL via Slick)
```

**Java Spring との対比:**

| Spring | Play Framework |
|--------|---------------|
| `@RestController` + `@RequestMapping` | `AbstractController` + `routes` ファイル |
| `@Service` | `@Singleton` クラス + `@Inject` |
| `@Repository` + JPA | `@Singleton` クラス + Slick |
| `application.properties` | `application.conf` (HOCON 形式) |

---

### 6. Evolutions — DB マイグレーション

`conf/evolutions/default/1.sql` に `# --- !Ups` と `# --- !Downs` を書くと
Play が自動でテーブルを作成・削除してくれる仕組み。

```sql
# --- !Ups
CREATE TABLE users ( ... );

# --- !Downs
DROP TABLE users;
```

---

## TypeScript / React の学習ポイント

### 1. 型の明示 — Java との比較

```typescript
// TypeScript
interface AuthState {
  userId: number | null   // null になりうることを型で表現
  loading: boolean
}

// Java
class AuthState {
  Integer userId;  // null になりうるが型からは分からない
  boolean loading;
}
```

TypeScript の `number | null` は「Union型」。
Java の `Integer`(nullable) と違い、コンパイラが `null` チェックを強制できる。

---

### 2. React Context — グローバル状態管理

```
AuthProvider (最上位)
  └── App
      ├── Login       ← useAuth() でログイン状態を読み書き
      ├── Register    ← useAuth() でログイン状態を読み書き
      └── Home        ← useAuth() でユーザーIDを取得
```

**Props drilling との違い:**
Context を使わないと「Login → App → Home → ... 」と props を何段も渡す必要がある。
Context は「どこからでも読み書きできる共有変数」のようなもの。

---

### 3. React Router — SPA のページ遷移

```typescript
// URL に応じてどのコンポーネントを表示するか定義
<Routes>
  <Route path="/login"    element={<Login />} />
  <Route path="/register" element={<Register />} />
  <Route path="/"         element={<ProtectedRoute><Home /></ProtectedRoute>} />
</Routes>
```

**従来のHTMLとの違い:**
通常の `<a href="/login">` はサーバーにページを取りに行く。
React Router の `<Link to="/login">` はJavaScriptだけでコンポーネントを切り替える(ページ再読み込みなし)。

---

### 4. ProtectedRoute パターン

```typescript
export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const { userId, loading } = useAuth()
  if (loading) return <p>読み込み中...</p>
  if (userId === null) return <Navigate to="/login" replace />
  return <>{children}</>   // ← ログイン済みなら子要素をそのまま表示
}
```

使い方:
```typescript
<Route path="/" element={<ProtectedRoute><Home /></ProtectedRoute>} />
```

`children` に渡したコンポーネントを「ガード」する仕組み。
未ログインなら `/login` にリダイレクトする。

---

### 5. フロント ↔ バックの通信フロー

```
ブラウザ (React)                    サーバー (Play)
    |                                    |
    |-- POST /api/v1/auth/login -------->|
    |   { email, password }              |  bcryptでパスワード検証
    |   credentials: 'include'           |  DBでセッション作成
    |                                    |
    |<-- 200 OK + Set-Cookie: SESSION_ID=xxx --|
    |   (HttpOnly Cookie がブラウザに保存される)  |
    |                                    |
    |-- GET /api/v1/auth/me ------------>|
    |   Cookie: SESSION_ID=xxx (自動送信)|  DBでセッション検索
    |                                    |
    |<-- 200 { userId: 1 } -------------|
```

**credentials: 'include' が必要な理由:**
デフォルトでは `fetch` はクロスオリジン(ドメインが違うサーバー)に Cookie を送らない。
フロント `localhost:5173` → バック `localhost:9000` はポートが違うため「クロスオリジン」扱い。
`credentials: 'include'` で Cookie を送るようにする。

バック側でも `application.conf` に CORS 設定が必要:
```hocon
play.filters.cors {
  allowedOrigins = ["http://localhost:5173"]
  supportsCredentials = true
}
```

---

### 6. Cookie Session vs JWT — 認証方式の比較

| | Cookie Session (今回) | JWT |
|--|--|--|
| セッション保存先 | DB | なし (トークン自体に情報) |
| ログアウト | DBのセッションを削除するだけ | トークンの無効化が難しい |
| セキュリティ | HttpOnly Cookie → XSS に強い | LocalStorage に保存するとXSS弱い |
| スケール | DBが必要 | ステートレスでスケールしやすい |

今回は学習目的なのでシンプルな Cookie Session 方式を選択。

---

## 実装したファイル一覧

### バックエンド (Scala)
| ファイル | 役割 |
|---------|------|
| `app/models/User.scala` | データクラス (case class) |
| `app/repositories/UserRepository.scala` | DB アクセス (Slick) |
| `app/repositories/SessionRepository.scala` | セッション DB アクセス |
| `app/services/AuthService.scala` | 認証ロジック (bcrypt, Either) |
| `app/controllers/AuthController.scala` | HTTP エンドポイント |
| `conf/evolutions/default/1.sql` | DB マイグレーション |

### フロントエンド (TypeScript)
| ファイル | 役割 |
|---------|------|
| `src/contexts/AuthContext.tsx` | 認証状態の共有 (Context) |
| `src/components/ProtectedRoute.tsx` | 未ログイン時リダイレクト |
| `src/pages/Login.tsx` | ログインフォーム |
| `src/pages/Register.tsx` | 新規登録フォーム |
| `src/pages/Home.tsx` | ログイン後のトップ画面 |
| `src/App.tsx` | ルーティング定義 |

---

## 動作確認コマンド

```bash
# 1. DB 起動
cd ~/My_Kitchen && docker compose up -d

# 2. バックエンド起動
cd ~/My_Kitchen/backend
source ../.env && export POSTGRES_USER POSTGRES_PASSWORD POSTGRES_DB APPLICATION_SECRET
sbt run

# 3. フロントエンド起動 (別ターミナル)
cd ~/My_Kitchen/frontend && npm run dev

# 4. ブラウザで確認
# http://localhost:5173/register → 登録
# http://localhost:5173/login   → ログイン
# http://localhost:5173/        → ホーム (未ログインでは /login にリダイレクト)
```
