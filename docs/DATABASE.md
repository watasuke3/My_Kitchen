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
┌──────────────────┐         ┌──────────────────┐
│    Users         │         │   Recipes        │
├──────────────────┤         ├──────────────────┤
│ id (PK)          │◄────────│ user_id (FK)     │
│ email            │         │ id (PK)          │
│ password_hash    │         │ name             │
│ created_at       │         │ recipe_type      │  (Preset / User-Original / Derived)
│ updated_at       │         │ created_at       │
└──────────────────┘         └──────────────────┘
                                    ↓
                            ┌──────────────────┐
                            │  Ingredients     │
                            ├──────────────────┤
                            │ recipe_id (FK)   │
                            │ ingredient_name  │
                            │ quantity         │
                            │ unit             │
                            └──────────────────┘
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
ユーザー所有またはシステム提供のレシピを管理。

#### スキーマ

| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| `id` | `BIGSERIAL` | PRIMARY KEY | レシピの一意識別子 |
| `user_id` | `BIGINT` | FOREIGN KEY (users.id) | レシピの所有者 (NULL = Preset) |
| `name` | `VARCHAR(255)` | NOT NULL | レシピ名 |
| `description` | `TEXT` | NULL | 説明・作り方 |
| `recipe_type` | `VARCHAR(50)` | NOT NULL | 'PRESET', 'USER_ORIGINAL', 'DERIVED' |
| `parent_recipe_id` | `BIGINT` | FOREIGN KEY (recipes.id) | Derived の場合、元のレシピ |
| `created_at` | `TIMESTAMP` | NOT NULL DEFAULT CURRENT_TIMESTAMP | 作成日時 |
| `updated_at` | `TIMESTAMP` | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新日時 |

#### SQL 定義

```sql
CREATE TABLE recipes (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  recipe_type VARCHAR(50) NOT NULL CHECK (recipe_type IN ('PRESET', 'USER_ORIGINAL', 'DERIVED')),
  parent_recipe_id BIGINT REFERENCES recipes(id) ON DELETE SET NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_recipes_user_id ON recipes(user_id);
CREATE INDEX idx_recipes_recipe_type ON recipes(recipe_type);
```

#### 備考
- `user_id = NULL`: システム提供の Preset レシピ
- `recipe_type = 'DERIVED'`: `parent_recipe_id` を参照
- Preset レシピはハードコードまたは JSON ファイルから初期データとして投入

---

### Ingredients (Phase 5+ - 具材・食材管理)

#### 目的
各レシピに含まれる食材と数量を記録。

#### スキーマ

| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| `id` | `BIGSERIAL` | PRIMARY KEY | 具材エントリーの一意識別子 |
| `recipe_id` | `BIGINT` | FOREIGN KEY (recipes.id) | 属するレシピ |
| `ingredient_name` | `VARCHAR(255)` | NOT NULL | 食材名 (例: "人参", "鶏肉") |
| `quantity` | `NUMERIC(10,2)` | NOT NULL | 数量 |
| `unit` | `VARCHAR(50)` | NOT NULL | 単位 (例: "g", "個", "ml") |
| `nutrition_per_unit` | `JSONB` | NULL | 単位あたりの栄養価 {kcal, protein, fat, carbs} |

#### SQL 定義

```sql
CREATE TABLE ingredients (
  id BIGSERIAL PRIMARY KEY,
  recipe_id BIGINT NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  ingredient_name VARCHAR(255) NOT NULL,
  quantity NUMERIC(10,2) NOT NULL,
  unit VARCHAR(50) NOT NULL,
  nutrition_per_unit JSONB
);

CREATE INDEX idx_ingredients_recipe_id ON ingredients(recipe_id);
```

#### 備考
- `nutrition_per_unit`: 初期段階ではハードコード、後で外部データソースと連携
- 栄養計算: quantity × nutrition_per_unit で算出 (Phase 7)

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
- **Ingredients**: 第 3 正規形

### 非正規化検討

- **ShoppingList** (Phase 6): 複数レシピの具材を集計するため、計算値をキャッシュ検討

---

## インデックス計画

| テーブル | カラム | 理由 |
|---------|--------|------|
| `users` | `email` | ログイン時の高速検索 |
| `recipes` | `user_id` | ユーザーのレシピ一覧取得 |
| `recipes` | `recipe_type` | PRESET / USER_ORIGINAL 分類検索 |
| `ingredients` | `recipe_id` | レシピの具材一覧取得 |

---

## バックアップ・復旧戦略

- **開発環境**: Docker volume で管理、push/pull で共有
- **本番環境**: PostgreSQL の自動バックアップ設定 (Phase 8+)

---

## 次フェーズへの拡張予定

| フェーズ | テーブル追加 |
|---------|-------------|
| Phase 5 | Recipes, Ingredients |
| Phase 6 | Meals, MealPlans, ShoppingLists |
| Phase 7 | (Ingredients に nutrition 詳細化) |
| Phase 8 | Allergies, UserAllergies |

---

## 変更履歴

| 日時 | 内容 | 実施者 |
|------|------|--------|
| 2026-05-15 | 初版作成 (Phase 4 最小構成) | Claude |
