# API Design - My_Kitchen

**バージョン**: 1.0  
**最終更新**: 2026-05-15  
**対象フェーズ**: Phase 4 (User Registration/Login)

---

## API Overview

### ベース URL

```
http://localhost:9000/api/v1
```

### 認証方式

**Cookie Session (Stateless)**
- サーバーが署名付きセッションクッキーを返却
- クライアント側で自動的にクッキーが送信される (credentials: 'include')
- HTTPS では `Secure`, `HttpOnly`, `SameSite=Strict` を設定

### コンテンツタイプ

```
Content-Type: application/json
```

---

## レスポンス形式

### 成功レスポンス (2xx)

基本形式: **データを直接返却** (envelope なし)

```json
// ユーザーオブジェクト
{
  "id": 1,
  "email": "user@example.com",
  "created_at": "2026-05-15T10:30:00Z"
}

// リスト
[
  { "id": 1, "email": "user1@example.com" },
  { "id": 2, "email": "user2@example.com" }
]
```

### エラーレスポンス (4xx, 5xx)

標準エラー形式:

```json
{
  "error": {
    "code": "INVALID_CREDENTIALS",
    "message": "Email or password is incorrect",
    "details": {
      "field": "email"
    },
    "timestamp": "2026-05-15T10:30:00Z"
  }
}
```

#### 必須フィールド

| フィールド | 型 | 説明 |
|----------|-----|------|
| `error.code` | String | 機械可読エラーコード |
| `error.message` | String | 人間が読めるエラーメッセージ (日本語可) |
| `error.timestamp` | ISO 8601 | エラー発生時刻 |

#### オプションフィールド

| フィールド | 型 | 説明 |
|----------|-----|------|
| `error.details` | Object | エラー詳細 (バリデーション時は field 名) |
| `error.request_id` | String | デバッグ用リクエスト ID |

---

## エラーコード体系

### 認証関連 (4xx)

| コード | HTTP Status | メッセージ | 対応 |
|-------|-------------|-----------|------|
| `INVALID_CREDENTIALS` | 401 | メールまたはパスワードが不正 | ログイン画面でエラー表示 |
| `ACCOUNT_LOCKED` | 401 | アカウントがロック (5 回失敗後) | ユーザーにリトライを促す |
| `EMAIL_ALREADY_EXISTS` | 400 | メールアドレスが既に登録済み | 登録画面でエラー表示 |
| `INVALID_EMAIL_FORMAT` | 400 | メールアドレス形式が不正 | フロント側での事前バリデーション |
| `WEAK_PASSWORD` | 400 | パスワードが弱い (8文字未満、英数字不足など) | パスワード要件を表示 |
| `SESSION_EXPIRED` | 401 | セッションの有効期限が切れた | ログイン画面へリダイレクト |
| `UNAUTHORIZED` | 401 | 認証が必要 (保護ルート) | ログイン画面へリダイレクト |

### バリデーション関連 (4xx)

| コード | HTTP Status | メッセージ | 対応 |
|-------|-------------|-----------|------|
| `VALIDATION_ERROR` | 400 | リクエストボディの検証エラー | フロントでエラー詳細を表示 |
| `MISSING_REQUIRED_FIELD` | 400 | 必須フィールドが未入力 | フロント側の事前チェック |

### サーバーエラー (5xx)

| コード | HTTP Status | メッセージ | 対応 |
|-------|-------------|-----------|------|
| `INTERNAL_SERVER_ERROR` | 500 | サーバー内部エラー | ユーザーに「しばらく待ってからリトライ」を促す |
| `DATABASE_ERROR` | 500 | データベース接続エラー | 同上 |

---

## エンドポイント仕様

### 1. ユーザー登録

#### エンドポイント

```
POST /api/v1/auth/register
```

#### リクエスト

```json
{
  "email": "user@example.com",
  "password": "SecurePass123"
}
```

#### リクエストバリデーション (フロント側)

- `email`: 必須、メールアドレス形式、255 文字以下
- `password`: 必須、8 文字以上、英数字を含む

#### 成功レスポンス (201 Created)

```json
{
  "id": 1,
  "email": "user@example.com",
  "created_at": "2026-05-15T10:30:00Z"
}
```

**ヘッダー**: `Set-Cookie: SESSION=...; HttpOnly; Secure; SameSite=Strict; Max-Age=604800`

#### エラーレスポンス

```json
{
  "error": {
    "code": "EMAIL_ALREADY_EXISTS",
    "message": "このメールアドレスは既に登録されています",
    "timestamp": "2026-05-15T10:30:00Z"
  }
}
```

#### HTTP ステータス

| ステータス | 原因 |
|----------|------|
| 201 | 登録成功、セッションクッキー発行 |
| 400 | バリデーションエラー (email 重複, password 弱い等) |
| 500 | サーバーエラー |

---

### 2. ログイン

#### エンドポイント

```
POST /api/v1/auth/login
```

#### リクエスト

```json
{
  "email": "user@example.com",
  "password": "SecurePass123"
}
```

#### 成功レスポンス (200 OK)

```json
{
  "id": 1,
  "email": "user@example.com",
  "created_at": "2026-05-15T10:30:00Z"
}
```

**ヘッダー**: `Set-Cookie: SESSION=...; HttpOnly; Secure; SameSite=Strict; Max-Age=604800`

#### エラーレスポンス

失敗時 (401):

```json
{
  "error": {
    "code": "INVALID_CREDENTIALS",
    "message": "メールアドレスまたはパスワードが正しくありません",
    "timestamp": "2026-05-15T10:30:00Z"
  }
}
```

レート制限超過時 (401):

```json
{
  "error": {
    "code": "ACCOUNT_LOCKED",
    "message": "アカウントが一時的にロックされています。5分後に再度お試しください",
    "details": {
      "locked_until": "2026-05-15T10:35:00Z"
    },
    "timestamp": "2026-05-15T10:30:00Z"
  }
}
```

#### HTTP ステータス

| ステータス | 原因 |
|----------|------|
| 200 | ログイン成功、セッションクッキー発行 |
| 401 | 認証情報が不正、またはレート制限中 |
| 500 | サーバーエラー |

---

### 3. ログアウト

#### エンドポイント

```
POST /api/v1/auth/logout
```

#### リクエスト

なし (セッションクッキーのみ)

#### 成功レスポンス (204 No Content)

ボディなし

**ヘッダー**: `Set-Cookie: SESSION=; Max-Age=0; HttpOnly; Secure; SameSite=Strict`

#### HTTP ステータス

| ステータス | 原因 |
|----------|------|
| 204 | ログアウト成功 |
| 401 | 認証なし |
| 500 | サーバーエラー |

---

### 4. 現在のユーザー取得 (保護ルート)

#### エンドポイント

```
GET /api/v1/auth/me
```

#### リクエスト

なし

#### 成功レスポンス (200 OK)

```json
{
  "id": 1,
  "email": "user@example.com",
  "created_at": "2026-05-15T10:30:00Z"
}
```

#### エラーレスポンス (401)

```json
{
  "error": {
    "code": "UNAUTHORIZED",
    "message": "認証が必要です",
    "timestamp": "2026-05-15T10:30:00Z"
  }
}
```

#### 用途

- フロント起動時にセッションが有効か確認
- 保護ルートへのアクセス前に実行

---

## HTTP ステータスコード一覧

| ステータス | 用途 | 例 |
|----------|------|-----|
| **200 OK** | リクエスト成功、ボディあり | ログイン成功、ユーザー取得 |
| **201 Created** | リソース作成成功 | ユーザー登録成功 |
| **204 No Content** | リクエスト成功、ボディなし | ログアウト |
| **400 Bad Request** | リクエスト形式エラー | バリデーション失敗、email 重複 |
| **401 Unauthorized** | 認証エラー/失敗 | ログイン失敗、セッション切れ |
| **403 Forbidden** | 権限なし | 他人のリソースへのアクセス |
| **500 Internal Server Error** | サーバーエラー | DB エラー等 |
| **503 Service Unavailable** | サーバー不可用 | メンテナンス中 |

---

## タイムスタンプ形式

### ISO 8601 形式を採用

```
2026-05-15T10:30:00Z
```

- タイムゾーン: UTC (Z で示す)
- フロント側で現地時間に変換

---

## レート制限

### ログイン API

- **制限**: 5 回連続失敗で 5 分間ロック
- **判定対象**: メールアドレス単位
- **実装**: メモリ内カウンター (phase 4)、後で Redis に移行 (Phase 6+)

### リセット条件

- 5 分経過
- 正常なログインの実行
- 管理者による手動リセット

---

## CORS 設定 (Phase 3 確定)

### 許可対象

```
Origin: http://localhost:5173
Origin: http://127.0.0.1:5173
```

### メソッド

```
GET, POST, PUT, DELETE, OPTIONS
```

### ヘッダー

```
Content-Type, Authorization
```

### クレデンシャル

```
Access-Control-Allow-Credentials: true
```

---

## セキュリティヘッダー (Phase 5+ で追加)

```
Strict-Transport-Security: max-age=31536000; includeSubDomains
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
```

---

## API バージョニング戦略

### 採用形式

```
/api/v1/auth/login
/api/v2/auth/login  (将来的に breaking change がある場合)
```

### ポリシー

- Breaking change: メジャーバージョン上げ (v1 → v2)
- 後方互換性を保つ変更: マイナーバージョン上げ不要 (v1 のまま)
- 非推奨エンドポイント: 3 フェーズ分は v1 をサポート、その後削除

---

## 変更履歴

| 日時 | 内容 | 実施者 |
|------|------|--------|
| 2026-05-15 | 初版作成 (Phase 4 認証 API) | Claude |
