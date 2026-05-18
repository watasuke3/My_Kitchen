# Database Design - My_Kitchen

**バージョン**: 1.0  
**最終更新**: 2026-05-15  
**対象フェーズ**: Phase 4 (User Registration/Login)

---

## ER 図 (Entity Relationship Diagram)

### Phase 4 最小構成

```
┌─────────────────────┐
│      Users          │
├─────────────────────┤
│ id (PK, bigserial)  │
│ email (unique)      │
│ password_hash       │
│ created_at          │
│ updated_at          │
└─────────────────────┘
        ↓
   Phase 5+ で
   他テーブルと
   関連付け
```

### Phase 5+ 拡張計画

```
┌──────────────────┐         ┌──────────────────────┐
│    Users         │         │   Recipes            │
├──────────────────┤         ├──────────────────────┤
│ id (PK)          │◄────────│ user_id (FK)         │
│ email            │         │ id (PK)              │
│ password_hash    │         │ name                 │
│ created_at       │         │ source_url           │  (クックパッド等の URL)
│ updated_at       │         │ category             │  (主食/主菜/副菜/汁物/その他)
└──────────────────┘         │ servings             │  (基準人数)
                             │ memo                 │  (個人メモ)
                             │ created_at           │
                             │ updated_at           │
                             └──────────────────────┘
```

---

## テーブル仕様

### Users (Phase 4 - ユーザー認証)

#### 目的
ユーザー登録・ログイン機能を実装するための最小限のユーザー情報管理。

#### スキーマ

| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| `id` | `BIGSERIAL` | PRIMARY KEY | ユーザーの一意識別子 |
| `email` | `VARCHAR(255)` | UNIQUE NOT NULL | ログイン用メールアドレス |
| `password_hash` | `VARCHAR(255)` | NOT NULL | bcrypt でハッシュ化されたパスワード |
| `created_at` | `TIMESTAMP` | NOT NULL DEFAULT CURRENT_TIMESTAMP | 作成日時 |
| `updated_at` | `TIMESTAMP` | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新日時 |

#### SQL 定義

```sql
CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
```

#### 備考
- `email`: メールアドレスの形式検証はアプリケーション層で実施
- `password_hash`: bcrypt で生成（フロント側で平文を送信しない、HTTPS 必須）
- `updated_at`: トリガーまたはアプリケーション層で更新

---

### Recipes (Phase 5+ - レシピ管理)

#### 目的
ユーザーが外部料理サイト（クックパッド等）のURLを登録して献立に活用する。

#### スキーマ

| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| `id` | `BIGSERIAL` | PRIMARY KEY | レシピの一意識別子 |
| `user_id` | `BIGINT` | FOREIGN KEY (users.id) NOT NULL | レシピの所有者 |
| `name` | `VARCHAR(255)` | NOT NULL | レシピ名（ユーザーが入力） |
| `source_url` | `VARCHAR(2048)` | NOT NULL | 外部サイトの URL (クックパッド等) |
| `category` | `VARCHAR(50)` | NOT NULL | '主食', '主菜', '副菜', '汁物', 'その他' |
| `servings` | `INTEGER` | NOT NULL DEFAULT 2 | 基準人数（例：2人前） |
| `memo` | `TEXT` | NULL | 個人メモ |
| `created_at` | `TIMESTAMP` | NOT NULL DEFAULT CURRENT_TIMESTAMP | 作成日時 |
| `updated_at` | `TIMESTAMP` | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新日時 |

#### SQL 定義

```sql
CREATE TABLE recipes (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name VARCHAR(255) NOT NULL,
  source_url VARCHAR(2048) NOT NULL,
  category VARCHAR(50) NOT NULL CHECK (category IN ('主食', '主菜', '副菜', '汁物', 'その他')),
  servings INTEGER NOT NULL DEFAULT 2,
  memo TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_recipes_user_id ON recipes(user_id);
CREATE INDEX idx_recipes_category ON recipes(category);
```

#### 備考
- `user_id`: 必須（プリセットなし、全レシピはユーザー所有）
- `source_url`: クックパッド・デリッシュキッチン・白ごはん.com 等の URL を想定
- 食材・栄養情報はアプリでは管理しない（外部サイトで確認）
- 将来拡張: 手動で `calories_per_serving` などを追加して栄養計算に対応できる設計にしておく

---

## マイグレーション戦略

### Flyway 採用予定 (Phase 5+)

ファイル構成:

```
backend/conf/db/migration/
├── V1__create_users_table.sql      (Phase 4)
├── V2__create_recipes_table.sql    (Phase 5)
├── V3__create_ingredients_table.sql (Phase 5)
├── V4__seed_preset_recipes.sql     (Phase 5)
└── ...
```

### Phase 4 では

- `Users` テーブルのみ作成
- Flyway setup は Phase 5 で実施
- 初期段階は `sbt run` 時に手動で SQL を実行するか、Play Evolutions を使用

---

## 正規化戦略

### 正規形

- **Users**: 第 3 正規形
- **Recipes**: 第 3 正規形 (user_id FK は依存関係を示す)
- **ShoppingListItems**: 第 3 正規形

---

## インデックス計画

| テーブル | カラム | 理由 |
|---------|--------|------|
| `users` | `email` | ログイン時の高速検索 |
| `recipes` | `user_id` | ユーザーのレシピ一覧取得 |
| `recipes` | `category` | カテゴリ別レシピ検索 |
| `shopping_list_items` | `user_id` | ユーザーの買い物リスト取得 |
| `shopping_list_items` | `is_checked` | 未チェックのみ表示する絞り込み |

---

## バックアップ・復旧戦略

- **開発環境**: Docker volume で管理、push/pull で共有
- **本番環境**: PostgreSQL の自動バックアップ設定 (Phase 8+)

---

### ShoppingListItems (Phase 6 - 買い物リスト)

#### 目的
ユーザーの買い物リストを管理する。プリセット食材とユーザー追加食材を同一テーブルで管理。

#### スキーマ

| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| `id` | `BIGSERIAL` | PRIMARY KEY | アイテムの一意識別子 |
| `user_id` | `BIGINT` | FOREIGN KEY (users.id) NOT NULL | 所有ユーザー |
| `name` | `VARCHAR(255)` | NOT NULL | 食材名 (例: "醤油", "鶏もも肉") |
| `quantity` | `VARCHAR(100)` | NULL | 分量 (自由記述。例: "200g", "2個", "適量") |
| `is_checked` | `BOOLEAN` | NOT NULL DEFAULT FALSE | 購入済みフラグ |
| `is_preset` | `BOOLEAN` | NOT NULL DEFAULT FALSE | TRUE = アプリ提供のプリセット食材 |
| `sort_order` | `INTEGER` | NOT NULL DEFAULT 0 | 表示順 (ユーザーが並び替え可能) |
| `created_at` | `TIMESTAMP` | NOT NULL DEFAULT CURRENT_TIMESTAMP | 作成日時 |
| `updated_at` | `TIMESTAMP` | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新日時 |

#### SQL 定義

```sql
CREATE TABLE shopping_list_items (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name VARCHAR(255) NOT NULL,
  quantity VARCHAR(100),
  is_checked BOOLEAN NOT NULL DEFAULT FALSE,
  is_preset BOOLEAN NOT NULL DEFAULT FALSE,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_shopping_list_items_user_id ON shopping_list_items(user_id);
CREATE INDEX idx_shopping_list_items_is_checked ON shopping_list_items(user_id, is_checked);
```

#### 備考
- `is_preset = TRUE`: 醤油・砂糖・塩・油など基本調味料をシステムが初期データとして投入
- プリセットはユーザーアカウント作成時に自動コピーされる（各ユーザーが独立して編集可能）
- 「チェック済みを一括クリア」= `is_checked = FALSE` に一括更新（行削除はしない）
- ユーザーはプリセット行も削除・編集可能

---

## 次フェーズへの拡張予定

| フェーズ | テーブル追加 |
|---------|-------------|
| Phase 5 | Recipes (URL 登録モデル) |
| Phase 6 | Meals, MealPlans, ShoppingListItems |
| Phase 7 | (Recipes に calories_per_serving 等を追加して栄養計算に対応、未確定) |
| Phase 8 | Allergies, UserAllergies |

---

## 変更履歴

| 日時 | 内容 | 実施者 |
|------|------|--------|
| 2026-05-15 | 初版作成 (Phase 4 最小構成) | Claude |
| 2026-05-18 | Recipes を URL 登録モデルに変更、Ingredients 廃止 | Claude |
| 2026-05-18 | ShoppingListItems テーブル追加 (プリセット + ユーザー追加モデル) | Claude |
