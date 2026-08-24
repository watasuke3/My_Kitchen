# Phase 6: カレンダー・献立管理 / 買い物リスト DBスキーマ設計

## 背景・目的

CLAUDE.mdのPhase 6要件（ウィークリープラン、食事実績記録、買い物リスト）を実現するためのDBスキーマを設計する。Phase 4・5で確立した命名規則・設計パターン（`users`, `recipes`, `ingredients`）を踏襲する。

## スコープ

本設計はDBスキーマ（テーブル定義）のみを対象とする。カレンダーUI・重複回避ロジックの実装計画は別途 `writing-plans` で扱う。

## 設計方針

### 1. 献立の「予定」と「実績」は1テーブルに同居させる

`meal_plans` テーブルに `status` カラム（`planned` / `eaten` / `skipped`）を持たせ、予定と実績を分離しない。

理由:
- Phase 6の要件では「予定」と「実績」は同じレコードのライフサイクル（未実施 → 食べた/食べなかった）として扱われ、独立した履歴として過去の予定内容を保持する必要はない
- テーブル分離するとJOINが増え、カレンダーUIの表示・更新ロジックが複雑になる
- 将来的な栄養集計（Phase 7）でも `status = 'eaten'` のみを対象にすればよく、単純

### 2. 1つの「日付×食事区分」の枠に複数レシピを割り当て可能にする

`meal_plans`（1枠を表す親レコード）と `meal_plan_recipes`（枠とレシピの中間テーブル）に分離する。

理由:
- 実際の食事は主菜・副菜・汁物のように複数レシピで構成されることが多い
- 後からの手戻り（1レシピ限定→複数対応への変更）を避けられる
- `ingredients` テーブルと同様、`sort_order` で表示順を管理するパターンを踏襲する

### 3. 買い物リストのプリセットはDBに持たせない

「基本食材のプリセット」はフロントエンド（React）側の定数配列として保持し、専用のDBテーブルは作らない。

理由:
- Phase 6の要件はユーザーごとのプリセットカスタマイズを求めていない
- プリセットは全ユーザー共通の静的な参照データであり、DB化するとAPI・CRUD実装が余分に必要になる（YAGNI）

### 4. 買い物リストは1ユーザー1本のフラットなリストとして持つ

`shopping_list_items` テーブルのみを新設し、リストをグルーピングする専用コンテナテーブルは作らない。

理由:
- 「複数端末同期」の要件があるためDB保存は必須（`localStorage`では満たせない）
- ユーザーごとに複数の買い物リストを使い分ける想定は要件にない

### 5. 命名規則: 主キーは `id`、外部キーは `テーブル名_id`

既存テーブル（`users`, `recipes`, `ingredients`, `sessions`）と同じ命名規則を踏襲する。主キーは自テーブルの行を指すため文脈上自明であり、外部キーは参照先テーブルを明示する必要があるため使い分ける。

## テーブル定義

### `meal_plans`

献立の「予定」と「実績」を表す。1行が「ある日のある食事区分（朝食/昼食/夕食）」に対応する。

```sql
CREATE TABLE meal_plans (
  id         BIGSERIAL    PRIMARY KEY,
  user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  date       DATE         NOT NULL,
  meal_type  VARCHAR(20)  NOT NULL,  -- 'breakfast' / 'lunch' / 'dinner'
  status     VARCHAR(20)  NOT NULL DEFAULT 'planned',  -- 'planned' / 'eaten' / 'skipped'
  created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, date, meal_type)
);

CREATE INDEX idx_meal_plans_user_id_date ON meal_plans(user_id, date);
```

### `meal_plan_recipes`

`meal_plans` の1枠に紐づくレシピを表す中間テーブル。

```sql
CREATE TABLE meal_plan_recipes (
  id            BIGSERIAL PRIMARY KEY,
  meal_plan_id  BIGINT    NOT NULL REFERENCES meal_plans(id) ON DELETE CASCADE,
  recipe_id     BIGINT    NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  sort_order    INT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_meal_plan_recipes_meal_plan_id ON meal_plan_recipes(meal_plan_id);
CREATE INDEX idx_meal_plan_recipes_recipe_id ON meal_plan_recipes(recipe_id);
```

### `shopping_list_items`

買い物リストの品目。ユーザーごとにフラットな1本のリストとして持つ。プリセットはDBに持たず、フロント側の定数から追加される。

```sql
CREATE TABLE shopping_list_items (
  id         BIGSERIAL     PRIMARY KEY,
  user_id    BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name       VARCHAR(255)  NOT NULL,
  quantity   VARCHAR(50),
  is_checked BOOLEAN       NOT NULL DEFAULT FALSE,
  sort_order INT           NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_shopping_list_items_user_id ON shopping_list_items(user_id);
```

## 対象外（今回の設計に含まない）

- カレンダーUIのコンポーネント設計
- 重複回避ロジック（直近1〜2週間の献立除外）の実装詳細
- 「食べた」ボタン押下時のPhase 7フィードバックとの連携
- 買い物リストの一括クリア・複数端末同期のAPI設計（テーブル構造のみ確定し、API設計は実装計画側で扱う）
