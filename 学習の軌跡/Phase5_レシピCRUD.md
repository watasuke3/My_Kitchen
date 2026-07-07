# Phase 5: レシピ CRUD 実装

## 実装した機能

| エンドポイント | メソッド | 説明 |
|--------------|---------|------|
| /api/v1/recipes | GET | 自分のレシピ一覧 |
| /api/v1/recipes | POST | レシピ作成 |
| /api/v1/recipes/:id | GET | レシピ詳細 |
| /api/v1/recipes/:id | PUT | レシピ更新 |
| /api/v1/recipes/:id | DELETE | レシピ削除 |

---

## Scala の学習ポイント

### 1. sealed trait で閉じたエラー型を作る

```scala
sealed trait RecipeError
case object RecipeNotFound  extends RecipeError
case object RecipeForbidden extends RecipeError
```

`sealed trait` は同一ファイル内でしか継承できない。  
これにより `match` 式でパターンを書き忘れるとコンパイルエラーになる（網羅性チェック）。

Java の `enum` に近いが、各ケースがデータを持てるのが違い：

```scala
// Scala: データを持つことができる
case class UnexpectedError(msg: String) extends AuthError

// Java の enum はこれが苦手（workaround が必要）
```

### 2. Option[T] で null を使わない

```scala
case class Recipe(
  description: Option[String],  // あってもなくてもよい
  ...
)
```

- `Option[String]` = `Some("値")` か `None`
- Java では `String` が `null` になる可能性があり、NPE が実行時まで気づけない
- Scala では型に表れるので「null かも」という情報が伝わる

```scala
// match でパターン展開
r.description match {
  case Some(text) => println(text)
  case None       => println("説明なし")
}

// map で変換（None なら None のまま）
val upper: Option[String] = r.description.map(_.toUpperCase)
```

### 3. Future の flatMap チェーン (モナド合成)

RecipeService の `update` メソッド：

```scala
def update(...): Future[Either[RecipeError, Recipe]] =
  recipeRepo.findById(id).flatMap {
    // findById の結果を受け取り、次の Future を返す
    case None         => Future.successful(Left(RecipeNotFound))
    case Some(r) if r.userId != userId => Future.successful(Left(RecipeForbidden))
    case Some(_) =>
      recipeRepo.update(...).flatMap { _ =>
        recipeRepo.findById(id).map {
          case Some(updated) => Right(updated)
          case None          => Left(RecipeNotFound)
        }
      }
  }
```

`flatMap` は「非同期処理の結果を次の非同期処理に渡す」もの。  
Java の `CompletableFuture.thenCompose` に相当する。

```
findById → (非同期) → update → (非同期) → findById → (非同期) → 結果
         ↑flatMap             ↑flatMap               ↑map
```

### 4. Slick の型マッピング: テーブル定義の仕組み

```scala
private class RecipesTable(tag: Tag)
    extends Table[RecipeRow](tag, "recipes") {

  def id    = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def title = column[String]("title")
  def *     = (id, userId, title, ...)  // デフォルト射影 (SELECT * に対応)
}
```

- `Table[RecipeRow]` の型引数が「行1件の Scala 型」
- `*` メソッドが「どのカラムを返すか」を定義する
- Java で JDBC を書くと `rs.getString("title")` と手書きだが、Slick は型安全

```scala
// Slick でのクエリ例
recipes.filter(_.userId === userId)   // WHERE user_id = ?
       .sortBy(_.createdAt.desc)      // ORDER BY created_at DESC
       .result                        // SELECT * を実行
```

### 5. 認証ヘルパーパターン

RecipeController の `withAuth` メソッド：

```scala
private def withAuth[A](request: Request[A])(f: Long => Future[Result]): Future[Result] =
  request.cookies.get("SESSION_ID") match {
    case None => Future.successful(Unauthorized(...))
    case Some(cookie) =>
      authService.validateSession(cookie.value).flatMap {
        case None         => Future.successful(Unauthorized(...))
        case Some(userId) => f(userId)  // 認証済み → 本処理に委譲
      }
  }
```

**カリー化 (Currying)** を使っている：

```scala
def withAuth[A](request: Request[A])(f: Long => Future[Result])
//             ↑ 第1引数グループ     ↑ 第2引数グループ (関数を渡す)
```

使う側：

```scala
withAuth(request) { userId =>
  // userId を使って処理
  recipeService.list(userId).map(...)
}
```

Java で書くと：

```java
// Java 的に書くと...
withAuth(request, userId -> {
    return recipeService.list(userId).thenApply(...);
});
```

Scala のほうが「最後の引数がブロックなら {} で書ける」という慣習があり、
自前の制御構文のように見せることができる。

---

## TypeScript / React の学習ポイント

### 1. 型定義でAPIレスポンスを守る

```typescript
// src/api/recipes.ts
export interface Recipe {
  id: number
  title: string
  description: string | null  // null も許容 (Scala の Option[String] に対応)
  category: string
  servings: number
  cookTimeMinutes: number
  createdAt: string
  updatedAt: string
}
```

- `description: string | null` = Union型。「文字列か null」
- Scala の `Option[String]` はシリアライズされると JSON では `null` か `"値"` になる
- TypeScript で `null` を型に入れることで「null チェック忘れ」をコンパイラが検出できる

### 2. カスタム API モジュール (関心の分離)

```typescript
// すべてのAPI通信を1ファイルに集約
export async function fetchRecipes(): Promise<Recipe[]> {
  const res = await fetch(`${BASE}/recipes`, { credentials: 'include' })
  if (!res.ok) throw new Error('レシピ一覧の取得に失敗しました')
  return res.json()
}
```

- `credentials: 'include'` = Cookie を送る (認証セッションに必要)
- エラー処理を一箇所に書くことでページコンポーネントが薄くなる
- Scala の Repository パターンと同じ「DBアクセスの詳細を隠す」考え方

### 3. useEffect でデータ取得

```typescript
useEffect(() => {
  fetchRecipes()
    .then(setRecipes)        // 成功 → state に格納
    .catch(e => setError(e.message))  // 失敗 → エラー表示
    .finally(() => setLoading(false)) // 完了 → ローディング解除
}, [])  // [] = マウント時に1回だけ実行
```

- `useEffect` の依存配列 `[]` が空 = コンポーネント初期表示時だけ実行
- Java の `@PostConstruct` に似た「初期化処理」
- `setLoading(false)` を `finally` に書くことで成功・失敗両方でローディングを解除

### 4. ルーティングとパラメータ

```typescript
// App.tsx での定義
<Route path="/recipes/:id"      element={<RecipeDetail />} />
<Route path="/recipes/:id/edit" element={<RecipeForm />} />

// RecipeForm.tsx での使用
const { id } = useParams<{ id?: string }>()
const isEdit  = id !== undefined  // パラメータがあれば編集モード
```

- URL の `:id` が動的セグメント
- `useParams` でコンポーネントから URL パラメータを取得
- 同じ `RecipeForm` コンポーネントを新規作成・編集の両方に使い回している

### 5. フロントとバックエンドのデータ型の対応

| Scala (バックエンド) | JSON (通信) | TypeScript (フロントエンド) |
|--------------------|------------|--------------------------|
| `Long` | `number` | `number` |
| `String` | `"文字列"` | `string` |
| `Option[String]` | `null` または `"値"` | `string \| null` |
| `Int` | `number` | `number` |
| `OffsetDateTime` | `"2024-01-01T00:00:00+09:00"` | `string` (Date に変換して表示) |

---

## アーキテクチャ全体像 (Phase 5 時点)

```
Browser
  ↓ HTTP (Cookie付き)
Play Router
  ↓
RecipeController
  ├─ withAuth() → AuthService.validateSession() → SessionRepository → DB
  ↓
RecipeService  (ビジネスロジック: 所有権チェックなど)
  ↓
RecipeRepository (Slick)
  ↓
PostgreSQL
```

**所有権チェック** がポイント：

```scala
case Some(r) if r.userId != userId => Future.successful(Left(RecipeForbidden))
```

- 他人のレシピを操作しようとすると `403 Forbidden`
- `if` ガード付き `case` = Java の `if-else` をパターンマッチ内に書く Scala の書き方

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

# curl でのテスト
# ログイン
curl -s -c /tmp/c.txt -X POST http://localhost:9000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test1234"}' | jq

# レシピ作成
curl -s -b /tmp/c.txt -X POST http://localhost:9000/api/v1/recipes \
  -H "Content-Type: application/json" \
  -d '{"title":"肉じゃが","description":"定番の家庭料理","category":"夕食","servings":4,"cookTimeMinutes":40}' | jq

# 一覧取得
curl -s -b /tmp/c.txt http://localhost:9000/api/v1/recipes | jq
```
