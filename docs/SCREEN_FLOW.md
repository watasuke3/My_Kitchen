# 画面遷移図

My Kitchen フロントエンド（React Router）の画面遷移をまとめる。
対応コード: `frontend/src/App.tsx`

## 画面一覧

| パス | コンポーネント | 認証要否 |
|---|---|---|
| `/login` | `Login.tsx` | 不要 |
| `/register` | `Register.tsx` | 不要 |
| `/` | `Home.tsx` | 要（`ProtectedRoute`） |
| `/recipes` | `RecipeList.tsx` | 要 |
| `/recipes/new` | `RecipeForm.tsx`（新規作成モード） | 要 |
| `/recipes/:id` | `RecipeDetail.tsx` | 要 |
| `/recipes/:id/edit` | `RecipeForm.tsx`（編集モード） | 要 |

`ProtectedRoute`（`frontend/src/components/ProtectedRoute.tsx`）は `AuthContext` のログイン状態を確認し、
未ログインの場合は `/login` にリダイレクトする。

## 遷移図

```mermaid
flowchart TD
    Start([アプリ起動]) --> AuthCheck{ログイン済み?}

    AuthCheck -- いいえ --> Login[/login\nログイン画面/]
    AuthCheck -- はい --> Home[/\nホーム画面/]

    Login -- 新規登録リンク --> Register[/register\n会員登録画面/]
    Register -- 登録成功 --> Home
    Register -- 既にアカウントあり --> Login

    Login -- ログイン成功 --> Home
    Login -- ログイン失敗 --> Login

    Home -- レシピ一覧へ --> RecipeList[/recipes\nレシピ一覧画面/]

    RecipeList -- 新規作成ボタン --> RecipeNew[/recipes/new\nレシピ作成フォーム/]
    RecipeList -- レシピ選択 --> RecipeDetail[/recipes/:id\nレシピ詳細画面/]

    RecipeNew -- 保存成功 --> RecipeList
    RecipeNew -- キャンセル --> RecipeList

    RecipeDetail -- 編集ボタン --> RecipeEdit[/recipes/:id/edit\nレシピ編集フォーム/]
    RecipeDetail -- 削除ボタン --> RecipeList
    RecipeDetail -- 戻る --> RecipeList

    RecipeEdit -- 保存成功 --> RecipeDetail
    RecipeEdit -- キャンセル --> RecipeDetail

    Home -- ログアウト --> Login
    RecipeList -- 未ログイン検知 --> Login
    RecipeDetail -- 未ログイン検知 --> Login
    RecipeNew -- 未ログイン検知 --> Login
    RecipeEdit -- 未ログイン検知 --> Login
```

## 備考

- Phase 6 以降（カレンダー・献立管理、買い物リスト）の画面はまだ実装されていないため、本図には含めていない。
  実装が進んだ時点で本ファイルを更新すること。
