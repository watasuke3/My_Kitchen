# Authentication & Session Management - My_Kitchen

**バージョン**: 1.0  
**最終更新**: 2026-05-15  
**対象フェーズ**: Phase 4 (User Registration/Login)

---

## 認証戦略

### 採用方式: Stateless Cookie Session

**理由**:
- Play Framework のデフォルト実装
- 学習コンテキストでシンプル (サーバー状態管理不要)
- HTTPS で安全性確保
- 本番環境への拡張も容易

### 代替案の比較

| 方式 | メリット | デメリット |
|------|---------|----------|
| **Cookie Session** ✅ | シンプル、play 標準、サーバー状態不要 | トークン有効期限後の明示的なリボーク不可 |
| JWT | トークン自体に情報保持、API 向け | 手動リフレッシュトークン管理、署名検証コスト |
| Redis Session | サーバー側管理、リボーク容易 | 複雑性増加、Redis 依存 |

**結論**: Phase 4-5 は Cookie Session、Phase 6+ で必要に応じて JWT への移行検討

---

## セッションライフサイクル

### セッション有効期限

```
7 日間 (604,800 秒)
```

### タイムアウト動作

- **動作**: セッションクッキーが有効期限切れ → ログアウト状態
- **フロント対応**: `/api/v1/auth/me` が 401 を返したら自動リダイレクト

### セッション更新

- **タイミング**: 不要 (rolling cookie)
- **手動更新**: `SlidingWindowExpiration` で自動延長

### ログアウト時

- クッキー `MAX-AGE=0` で即座に無効化
- ブラウザキャッシュから削除

---

## Cookie 設定

### 署名・暗号化

**Play Framework default**:

```
play.http.session.secure = true      (HTTPS only)
play.http.session.httpOnly = true    (JavaScript アクセス禁止)
play.http.session.sameSite = "Strict" (CSRF 対策)
```

### Set-Cookie ヘッダー例

```
Set-Cookie: SESSION=<signed-token>; \
  Path=/; \
  Domain=localhost; \
  Expires=Wed, 22-May-2026 10:30:00 GMT; \
  Max-Age=604800; \
  HttpOnly; \
  Secure; \
  SameSite=Strict
```

### フロント側の送信方法

```typescript
// fetchでクッキー自動送信
fetch('/api/v1/auth/login', {
  method: 'POST',
  credentials: 'include',  // ★ 重要
  body: JSON.stringify({ email, password })
})
```

---

## パスワードハッシング

### アルゴリズム: bcrypt

**理由**:
- 遅延関数 (intentional slowness)
- salt 自動管理
- レインボーテーブル攻撃に耐性
- 標準的で信頼性高い

### 実装

#### 依存ライブラリ

**build.sbt**:
```scala
libraryDependencies += "org.mindrot" % "jbcrypt" % "0.4"
```

#### Scala 実装例

```scala
import org.mindrot.bcrypt.BCrypt

// パスワードハッシング
val password = "SecurePass123"
val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())
// 出力例: $2a$10$N9qo8uLOickgx2ZMRZoMye...

// パスワード検証
val isValid = BCrypt.checkpw("SecurePass123", hashedPassword)  // true
val isInvalid = BCrypt.checkpw("WrongPassword", hashedPassword) // false
```

### パスワードストレージ

#### Users テーブル

```sql
CREATE TABLE users (
  ...
  password_hash VARCHAR(255) NOT NULL  -- bcrypt のハッシュ値を保存
);
```

#### bcrypt 出力長

- 常に 60 文字
- VARCHAR(60) では不十分な場合もあるため、VARCHAR(255) を推奨

### パスワードポリシー

| 要件 | 仕様 | 検証タイミング |
|------|------|-------------|
| **最小長** | 8 文字以上 | フロント側（事前チェック）+ バックエンド側（最終チェック） |
| **英数字混在** | 英字(a-z, A-Z) + 数字(0-9) を各 1 個以上 | フロント側+ バックエンド側 |
| **特殊文字** | 非推奨 (MVP では不要) | - |
| **リスト照合** | 一般的な弱いパスワード (password, 123456 等) | バックエンド側で検証 |

### 検証ロジック (Scala)

```scala
def validatePassword(password: String): Either[String, Unit] = {
  if (password.length < 8) {
    Left("パスワードは8文字以上である必要があります")
  } else if (!password.matches(".*[a-zA-Z].*")) {
    Left("パスワードは英字を含む必要があります")
  } else if (!password.matches(".*[0-9].*")) {
    Left("パスワードは数字を含む必要があります")
  } else {
    Right(())
  }
}
```

---

## レート制限

### 目的

ブルートフォース攻撃を防止 (ログイン API)

### 実装仕様

#### 制限内容

- **対象API**: `POST /api/v1/auth/login`
- **制限単位**: メールアドレス (per email)
- **上限**: 5 回連続失敗
- **ペナルティ**: 5 分間ロック

#### 制限のリセット

| 条件 | 動作 |
|------|------|
| 5 分経過 | 自動リセット |
| ログイン成功 | カウンター 0 リセット |
| 管理者リセット (将来) | 手動で 0 に初期化 |

### Phase 4 実装 (メモリ内)

#### Scala 実装例

```scala
import scala.collection.mutable
import java.time.Instant

object RateLimiter {
  private val attempts = mutable.Map[String, (Int, Long)]()  // (count, unlockTime)
  private val MAX_ATTEMPTS = 5
  private val LOCK_DURATION_SECONDS = 300L

  def isLocked(email: String): Boolean = {
    attempts.get(email).exists { case (_, unlockTime) =>
      Instant.now.getEpochSecond < unlockTime
    }
  }

  def recordFailure(email: String): Unit = {
    val now = Instant.now.getEpochSecond
    attempts(email) = attempts.get(email) match {
      case Some((count, _)) if count >= MAX_ATTEMPTS => 
        (count + 1, now + LOCK_DURATION_SECONDS)
      case Some((count, _)) => 
        (count + 1, now)
      case None => 
        (1, now)
    }
  }

  def recordSuccess(email: String): Unit = {
    attempts.remove(email)
  }
}
```

#### ログイン API での使用

```scala
def login(email: String, password: String): Result = {
  if (RateLimiter.isLocked(email)) {
    // ロック状態
    Unauthorized(
      Json.obj(
        "error" -> Json.obj(
          "code" -> "ACCOUNT_LOCKED",
          "message" -> "アカウントが一時的にロックされています"
        )
      )
    )
  } else {
    // ログイン処理
    validateCredentials(email, password) match {
      case true =>
        RateLimiter.recordSuccess(email)
        Ok(userJson).withSession("sessionId" -> email)
      case false =>
        RateLimiter.recordFailure(email)
        Unauthorized(Json.obj(...))
    }
  }
}
```

### Phase 6+ 拡張予定 (Redis)

```scala
// Redis を使用したレート制限
import redis.clients.jedis.Jedis

def isLocked(email: String)(implicit redis: Jedis): Boolean = {
  redis.get(s"rate_limit:$email") != null
}

def recordFailure(email: String)(implicit redis: Jedis): Unit = {
  val key = s"rate_limit:$email"
  val count = redis.incr(key).toInt
  if (count == 1) redis.expire(key, 300)  // 5分後に自動削除
}
```

---

## セキュリティベストプラクティス

### パスワード送信時 (フロント)

✅ **HTTPS 必須**:
- ローカル開発: `localhost` (HTTP 許可)
- 本番環境: HTTPS 強制

✅ **フロント側での事前バリデーション**:
```typescript
const validatePassword = (password: string) => {
  return password.length >= 8 &&
    /[a-zA-Z]/.test(password) &&
    /[0-9]/.test(password);
};
```

❌ **避けること**:
- クライアント側でハッシング (バックエンドも検証が必要なため無意味)
- HTTP で送信
- ブラウザ localStorage に平文保存

### ハッシング検証時 (バック)

✅ **タイムアタック対策**:
```scala
// BCrypt は内部で固定時間計算を行うため安全
BCrypt.checkpw(inputPassword, storedHash)
```

✅ **ログ取扱い**:
- ログに平文パスワード・ハッシュを記録しない
- エラーメッセージは「メールアドレスまたはパスワードが正しくありません」で統一 (どちらが間違っているか推測させない)

❌ **避けること**:
- MD5, SHA-1 によるハッシング (脆弱)
- ハッシュの二重計算 (パフォーマンス低下、セキュリティ向上なし)

### セッション管理

✅ **必須設定**:
- `HttpOnly`: JavaScript アクセス禁止 (XSS 対策)
- `Secure`: HTTPS のみ送信 (中間者攻撃対策)
- `SameSite=Strict`: CSRF 対策

✅ **有効期限**:
- 妥当な期限設定 (7 日間は一般的)
- ローリングウィンドウで自動延長検討

❌ **避けること**:
- localStorage に JWT 保存 (XSS で盗難可能)
- セッションの無期限化

---

## パスワードリセット (将来実装予定)

### 実装予定フェーズ

Phase 7-8 (現在は out of scope)

### フロー (参考)

1. ユーザーが「パスワード忘れた」をクリック
2. メールアドレス入力
3. ワンタイムリンク付きメール送信 (有効期限 30 分)
4. メール内リンククリック
5. 新パスワード入力・送信
6. bcrypt で再ハッシング、DB 更新

### セキュリティ考慮

- リセットリンク: ランダム 64 文字トークン
- 有効期限: 30 分
- リンク使用済み: トークン削除
- 使用回数制限: なし (安全)

---

## デバッグ・ローカル開発

### Session の確認

**ブラウザの DevTools**:
```
Application > Cookies > localhost > SESSION
```

### ログアウト (開発用)

ブラウザのコンソール:
```javascript
document.cookie = "SESSION=; Max-Age=0";
```

### テスト用アカウント

```
Email: test@example.com
Password: TestPass123
```

初回起動時にシードデータとして投入予定

---

## コンプライアンス

### GDPR 対応 (将来)

- ユーザー削除時に関連データも削除
- データ輸出機能
- 同意管理

### CCPA 対応 (将来)

- データ収集の透明性
- ユーザーの削除要求対応

---

## 変更履歴

| 日時 | 内容 | 実施者 |
|------|------|--------|
| 2026-05-15 | 初版作成 (Phase 4 認証方式確定) | Claude |
