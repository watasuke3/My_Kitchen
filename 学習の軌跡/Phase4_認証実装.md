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

---

## 動作確認で発覚したバグと修正方針 (2026-07-07)

### バグ1: OffsetDateTime が DB に書き込めない (users / sessions / recipes 共通)

**症状:** 登録・ログイン時に `PSQLException: column "created_at" is of type timestamp with time zone but expression is of type character varying` が発生。

**原因:** Slick 自体が `java.time.OffsetDateTime` 用の組み込み型変換 (`OffsetDateTimeJdbcType`) を持っており、内部的に `VARCHAR` で読み書きする実装になっている。
各 Repository で独自定義していた `implicit val offsetDateTimeMapper`（`TIMESTAMPTZ` と正しく対応する変換）と型が同じ (`BaseColumnType[OffsetDateTime]`) だったため、暗黙解決で Slick 組み込みの方が採用され、DB の `TIMESTAMPTZ` 型カラムに `VARCHAR` として書き込もうとして失敗していた。読み込み時も Postgres の日時文字列フォーマット不一致でパースエラーになる。

**学び:** Scala の implicit 解決は「ローカルスコープの方が import より優先される」のが原則だが、ライブラリ側が同じ型のインスタンスを持つ場合は意図せず衝突することがある。`implicit val` を定義しただけで安心せず、実際にどちらが使われているかログで確認する必要がある。

**修正:** `column[OffsetDateTime](...)` の呼び出し時に自作の `offsetDateTimeMapper` を `column[OffsetDateTime]("created_at")(offsetDateTimeMapper)` のように明示的に渡し、暗黙解決の曖昧さを排除した。対象: `UserRepository.scala`, `SessionRepository.scala`, `RecipeRepository.scala`。

### バグ2: ログイン後の POST/PUT/DELETE が全て CSRF エラー (403) になる

**症状:** 登録・ログイン（Cookie なしの初回リクエスト）は成功するが、Cookie (`SESSION_ID`) を持った状態でのレシピ作成・ログアウトなど、状態変更系リクエストが全て `403 Forbidden` (Play のデフォルト `Unauthorized` ページ) になる。

**原因:** Play Framework の `CSRFFilter`（`play.filters.enabled` により標準で有効）は、「Cookie を1つでも持っている = セッションがあるかもしれない」とみなし、POST/PUT/DELETE に有効な CSRF トークンを要求する。しかしこのアプリは Play の標準セッション機構を使わず、独自の `SESSION_ID` Cookie でセッション管理をしており、CSRF トークンの発行・検証を実装していない。そのため、ログイン後の全ての書き込み系リクエストが CSRF チェックで弾かれていた。

**検討した選択肢:**

| 方式 | 内容 | 判断 |
|------|------|------|
| CSRFFilter を無効化し `SameSite=Strict` で代替 | 独自 Cookie は既に `SameSite=Strict` 設定済み。クロスサイトリクエストではそもそも Cookie が送信されないため、CSRF 対策として機能する | ✅ 採用（ユーザーと相談の上決定） |
| CSRFFilter を有効のまま、API にもトークン方式を導入 | フロントがトークン取得 → ヘッダー送信を実装。二重防御になるが SPA 構成では実装コストが高い | 不採用 |

**修正:** `application.conf` の `play.filters.enabled` から `play.filters.csrf.CSRFFilter` を除外し、CORS / SecurityHeaders / AllowedHosts フィルタのみ有効化する。CSRF 対策は Cookie の `SameSite=Strict` 属性に一本化する。

## ログインAPIのレート制限 (2026-07-27)

### 方針決定の経緯

CLAUDE.md のセキュリティ要件「ログインAPIへのレート制限（ブルートフォース対策）」を満たすため、`AuthService` に自前実装していた `AccountLocked` / `MaxFailedAttempts`（失敗回数5回でロックする想定のフィールド・エラー型）を検討したが、これらは実際にはどこからも呼ばれない到達不能コードだった（`login` メソッド内で一度も参照されていない）。CLAUDE.md の方針「未使用のimport・変数は残さない」に反するため、自前実装を維持するのではなく、ライブラリ `play-guard`（`com.digitaltangible %% play-guard % 3.0.0`, Play 3.0 / Scala 2.13 対応）に一本化することにした。

**検討した2つの論点:**

1. **レート制限のキー: IPアドレス単位 vs メールアドレス単位**
   → **IPアドレス単位を採用**。CLAUDE.mdの要件は「同一送信元からの大量試行を止める」ことが主目的であり、IP単位が直接効く。メールアドレス単位だと「他人のメールアドレスを使って大量ログイン試行し、正規ユーザーを締め出す」DoS的悪用が可能になってしまう弱点があるため避けた。

2. **既存の `AccountLocked` / `MaxFailedAttempts` の扱い**
   → **削除して play-guard に一本化**。中途半端に両方残すと、実際には何もしていない `MaxFailedAttempts` の存在が将来読んだときに誤解を招く。レート制限（インフラ的関心事）を `AuthController` 側の Action 合成に持たせることで、`AuthService`（認証ドメインロジック）との責務分離もできる。

### 実装

`play-guard` 3.0.0 の実際のクラス構成は README等の二次情報と食い違いがあったため、`coursier fetch --sources` で取得した実際のソース（`RateLimitActionFilter.scala`）を読んで正確なAPIを確認した:

- `com.digitaltangible.ratelimit.RateLimiter(size: Long, rate: Double, name: String, clock: Clock)` … トークンバケット本体。`consumeAndCheck(key)` でトークン消費と可否判定を同時に行う。
- `com.digitaltangible.playguard.IpRateLimitFilter[R[_] <: Request[_]](rateLimiter, ipWhitelist = Set.empty)` … `RateLimitActionFilter` を継承した抽象クラスで、`keyFromRequest` に `request.remoteAddress` を使うよう実装済み。利用側は `rejectResponse[A](implicit request: R[A]): Future[Result]` だけをオーバーライドすればよい。
- Action合成: `(Action(parse.json) andThen ipRateLimitFilter).async { ... }` のように `ActionFilter` として `andThen` で通常の `Action` に連結する。

`AuthController.scala` に `loginRateLimiter = new RateLimiter(5, 1f / 10, "login-by-ip")`（トークン5個まで即時許可、以降10秒に1個回復）と `loginRateLimitFilter` を定義し、`login()` アクションにのみ適用した（`register()` には適用しない。ブルートフォース対策は「ログインAPI」が対象であり、登録APIは連続試行によるパスワード推測の対象にならないため）。

**学び:** ライブラリのAPIをWeb検索やAIによる要約経由で調べると、バージョン差異やハルシネーションで実際と異なる情報が返ってくることがある（今回も「クラス継承」「object経由のapply」など複数の矛盾した回答を得た）。決定的なのは `coursier fetch --sources` で実際のソースを取得して読むこと、そして最終的に `sbt compile` を通して確認することだった。

**削除したもの:** `AuthService.scala` の `AccountLocked` ケースオブジェクトと `MaxFailedAttempts` 定数、`AuthController.scala` の `AccountLocked` 分岐、および到達不能な `AuthService.isAccountLocked`（存在しないメソッド）をテストしていた `AuthServiceSpec.scala`（コンパイルが通っていなかった壊れたテスト）。

## バックエンドのテストコード追加による動作確認 (2026-07-27)

### 方針決定の経緯

Phase4/5共通の残タスクだった「動作確認」を、手動でのブラウザ操作ではなくテストコードで行うことにした（ユーザー指示）。あわせて「フォルダ構成をベストプラクティスにする」という要望を受け、`backend/test/` 配下を `backend/app/` と同じ `controllers/` `services/` のレイヤー構成に揃えた（Play/sbtの標準的な慣習）。`repositories/` 層は実DBが必要な結合テストになるため、テスト用DB環境が未整備な現状ではスコープ外とし、Phase5で確立した「Service層はリポジトリをモックしてユニットテストする」方針を踏襲した。

### UserRepository / SessionRepository のトレイト化

`AuthServiceSpec` を書く際、Phase5の `RecipeRepository` と同じ理由（具象クラスのコンストラクタがDB接続を即座に確立するため ScalaMock で `mock[T]` すると NPE になる）で `UserRepository` / `SessionRepository` も動かせなかった。同じ「トレイト + `@ImplementedBy(classOf[実装クラス])`」パターンでリファクタリングし、`UserRepositoryImpl` / `SessionRepositoryImpl` に分離した。これでリポジトリ全体（Recipe/Ingredient/User/Session）のDI設計が統一された。

### 追加したテスト

- `test/services/AuthServiceSpec.scala`: `register`（パスワードポリシー違反・メール重複・正常系でbcryptハッシュとセッション発行を検証）/ `login`（未登録・パスワード不一致・正常系）/ `logout` / `validateSession` の10ケース。
- `test/controllers/AuthControllerSpec.scala`: `register` / `login` / `logout` / `me` の各エンドポイントを `AuthService` をモックしてコントローラー単体でテスト（9ケース）。
- `test/controllers/RecipeControllerSpec.scala`: Phase5の `RecipeController` の `list`/`create`/`show`/`update`/`delete` をモックでテスト（10ケース）。

### コントローラーテストで踏んだ2つのハマりどころ

1. **Play 3.0 は内部で Akka ではなく Pekko を使っている。** `withJsonBody` を使うテストで `A Materializer is required` エラーが出た際、最初 `akka.actor.ActorSystem` / `akka.stream.Materializer` をimportしたが `not found: object akka` になった。Play/Playframework 3.0 系はLightbendのライセンス変更を受けて Apache Pekko（Akkaのフォーク）に移行しているため、`org.apache.pekko.actor.ActorSystem` / `org.apache.pekko.stream.Materializer` を使う必要がある。
2. **`FakeRequest#withJsonBody` は Content-Type ヘッダーを付与しない。** `controller.someAction().apply(fakeRequest)` のように `Action[JsValue]` を直接 `apply` する形でテストを書いていたところ、`withJsonBody` を使ったリクエストが `415 Unsupported Media Type` になった。原因を `play-test` の実ソース（`Fakes.scala`）で確認したところ、`Action[A]` は `apply(request: Request[A]): Future[Result]`（ボディパーサーを経由せず、既にパース済みのボディをそのまま使う）というオーバーロードを持っているが、`withJsonBody` は `FakeRequest[AnyContentAsJson]` を返すため型が一致せず、`EssentialAction.apply(rh: RequestHeader): Accumulator[...]` の方に解決されてしまい、実際に `parse.json` ボディパーサーが（Content-Typeヘッダーのチェック込みで）動いてしまっていた。解決策は `withJsonBody` の代わりに `FakeRequest(...).withBody(jsValue)` を使うこと。これは `FakeRequest[JsValue]` を返すため、アクションの `apply(request: Request[JsValue])` オーバーロードに正しく解決され、ボディパーサーを経由せず単体テストとして意図通り動く。

### 確認結果

`sbt test` で全44ケース中41ケースが成功。残り3件は Phase1 から存在する Play デフォルトの `HomeControllerSpec`（テスト実行時にDBのSCRAM認証パスワードが渡っておらず接続できない、という今回の変更と無関係な既存の環境要因）で、今回のテスト追加やリファクタリングとは無関係。
