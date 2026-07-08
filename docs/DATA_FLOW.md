# データフロー図

フロントエンド（React）⇔ バックエンド（Play Framework）⇔ DB（PostgreSQL）間のデータの流れをまとめる。
対応コード:
- `frontend/src/contexts/AuthContext.tsx`, `frontend/src/api/recipes.ts`
- `backend/conf/routes`, `backend/app/controllers/*Controller.scala`
- `backend/app/services/AuthService.scala`
- `backend/app/repositories/*Repository.scala`

## 全体構成

```mermaid
flowchart LR
    subgraph Client["ブラウザ (localhost:5173)"]
        UI[React コンポーネント]
        AuthCtx[AuthContext]
        RecipesApi[api/recipes.ts]
    end

    subgraph Server["Play Framework (localhost:9000)"]
        AuthCtrl[AuthController]
        RecipeCtrl[RecipeController]
        AuthSvc[AuthService]
        UserRepo[UserRepository]
        SessionRepo[SessionRepository]
        RecipeRepo[RecipeRepository]
    end

    DB[(PostgreSQL)]

    UI --> AuthCtx
    UI --> RecipesApi

    AuthCtx -- "fetch (credentials: include)" --> AuthCtrl
    RecipesApi -- "fetch (credentials: include)" --> RecipeCtrl

    AuthCtrl --> AuthSvc
    AuthSvc --> UserRepo
    AuthSvc --> SessionRepo
    RecipeCtrl --> RecipeRepo

    UserRepo --> DB
    SessionRepo --> DB
    RecipeRepo --> DB
```

## 認証フロー（登録・ログイン・セッション確認）

```mermaid
sequenceDiagram
    participant U as ユーザー
    participant FE as フロントエンド (AuthContext)
    participant AC as AuthController
    participant AS as AuthService
    participant UR as UserRepository
    participant SR as SessionRepository
    participant DB as PostgreSQL

    U->>FE: メール・パスワード入力
    FE->>AC: POST /api/v1/auth/register (or /login)
    AC->>AS: register(email, password) / login(...)

    AS->>UR: findByEmail(email)
    UR->>DB: SELECT users
    DB-->>UR: 結果
    UR-->>AS: Option[User]

    alt 登録の場合
        AS->>AS: bcryptでパスワードハッシュ化
        AS->>UR: create(email, hash)
        UR->>DB: INSERT users
    else ログインの場合
        AS->>AS: bcryptでハッシュ照合
    end

    AS->>SR: create(userId)
    SR->>DB: INSERT sessions
    DB-->>SR: sessionId
    SR-->>AS: sessionId
    AS-->>AC: Right(sessionId)
    AC-->>FE: Set-Cookie: SESSION_ID (HttpOnly, SameSite=Strict)
    FE-->>U: ホーム画面へ遷移

    Note over FE,AC: 以降のリクエストは Cookie の SESSION_ID を自動送信

    FE->>AC: GET /api/v1/auth/me
    AC->>SR: findValidSession(sessionId)
    SR->>DB: SELECT sessions
    DB-->>SR: userId
    SR-->>AC: Some(userId)
    AC-->>FE: ユーザー情報 JSON
```

## レシピCRUDフロー

```mermaid
sequenceDiagram
    participant U as ユーザー
    participant FE as フロントエンド (RecipeList/Form/Detail)
    participant API as api/recipes.ts
    participant RC as RecipeController
    participant RR as RecipeRepository
    participant DB as PostgreSQL

    U->>FE: レシピ一覧を開く
    FE->>API: fetchRecipes()
    API->>RC: GET /api/v1/recipes (Cookie付き)
    RC->>RR: findAll(userId)
    RR->>DB: SELECT recipes WHERE user_id = ?
    DB-->>RR: レシピ一覧
    RR-->>RC: Seq[Recipe]
    RC-->>API: JSON配列
    API-->>FE: Recipe[]
    FE-->>U: 一覧表示

    U->>FE: 新規作成フォーム送信
    FE->>API: createRecipe(input)
    API->>RC: POST /api/v1/recipes
    RC->>RR: insert(recipe)
    RR->>DB: INSERT recipes
    DB-->>RR: 作成されたレシピ
    RR-->>RC: Recipe
    RC-->>API: JSON
    API-->>FE: Recipe
    FE-->>U: 一覧 or 詳細へ遷移
```

## 備考

- 認証は Cookie ベースのセッション管理（`SESSION_ID`、HttpOnly + SameSite=Strict）であり、
  JWTは使用していない（`docs/AUTH.md` 参照）。
- Phase 6以降で `meal_plans` / `shopping_lists` のデータフローが追加される予定。実装時に本ファイルを更新すること。
