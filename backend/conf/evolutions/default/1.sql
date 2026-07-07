# --- !Ups

CREATE TABLE users (
  id         BIGSERIAL    PRIMARY KEY,
  email      VARCHAR(255) NOT NULL UNIQUE,
  password   VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE sessions (
  id         VARCHAR(64)  PRIMARY KEY,
  user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  expires_at TIMESTAMPTZ  NOT NULL,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sessions_user_id   ON sessions(user_id);
CREATE INDEX idx_sessions_expires_at ON sessions(expires_at);

-- ログイン失敗カウンタ (レート制限用)
CREATE TABLE login_attempts (
  email       VARCHAR(255) NOT NULL,
  failed_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  PRIMARY KEY (email, failed_at)
);

# --- !Downs

DROP TABLE IF EXISTS login_attempts;
DROP TABLE IF EXISTS sessions;
DROP TABLE IF EXISTS users;
