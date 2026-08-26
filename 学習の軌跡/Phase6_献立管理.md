# Phase 6: 献立管理（カレンダー・実績記録）実装

## 実装した機能

| エンドポイント | メソッド | 説明 |
|--------------|---------|------|
| /api/v1/meal-plans?from=&to= | GET | 期間内の献立枠一覧（割当レシピ・ステータス込み） |
| /api/v1/meal-plans | PUT | 日付×食事区分の枠にレシピを割り当て（存在しなければ新規作成） |
| /api/v1/meal-plans/:id/status | PATCH | 実績ステータス（planned/eaten/skipped）を更新 |
| /api/v1/meal-plans/:id | DELETE | 枠を削除（中間テーブルはON DELETE CASCADEで自動削除） |

---

## Scala の学習ポイント

### 1. `LocalDate` と Slick の `MappedColumnType`

PostgreSQL の `DATE` 型は Slick 標準では直接 `java.time.LocalDate` に対応していないため、
`OffsetDateTime` と同様に `java.sql.Date` との相互変換を `MappedColumnType.base` で定義する。

```scala
implicit val localDateMapper: BaseColumnType[LocalDate] =
  MappedColumnType.base[LocalDate, java.sql.Date](
    ld => java.sql.Date.valueOf(ld),
    d  => d.toLocalDate
  )
```

### 2. find-or-create（upsert）パターン

`meal_plans` は `UNIQUE(user_id, date, meal_type)` を持つため、「その枠が既にあれば使う、なければ作る」という
find-or-create を `MealPlanService.assign` で実装した。DBのUNIQUE制約とアプリ側のロジックの両方で
重複行の発生を防いでいる（制約だけに頼ると、同時リクエスト時の競合はエラーで検知できるが、
通常のCRUDフローでは事前にfindすることでエラーを起こさず自然にupsertできる）。

### 3. PATCH による部分更新

レシピの `PUT`（全項目送信して丸ごと置き換え）と異なり、「食べた/食べなかった」の記録は
`status` フィールドだけを更新したい操作のため `PATCH /meal-plans/:id/status` として
専用エンドポイントを分けた。PUTで全フィールドを要求すると、フロントが毎回既存の日付・食事区分・
レシピ一覧を送り直す必要が生じ、意図（ステータス変更）と手段（全置き換え）がずれてしまうため。

---

## 設計判断

- 献立の「予定」と「実績」は別テーブルに分けず、`status` カラムで表現した
  （理由: `docs/superpowers/specs/2026-08-24-phase6-meal-plan-schema-design.md` 参照）
- 重複回避ロジック（直近1〜2週間の献立を自動提案から除外）は Phase 8 で本実装するため、
  本フェーズでは対象外とした
