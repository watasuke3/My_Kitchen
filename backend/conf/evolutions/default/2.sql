# --- !Ups

CREATE TABLE recipes (
  id                BIGSERIAL    PRIMARY KEY,
  user_id           BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title             VARCHAR(255) NOT NULL,
  description       TEXT,
  category          VARCHAR(50)  NOT NULL DEFAULT 'その他',
  servings          INT          NOT NULL DEFAULT 2,
  cook_time_minutes INT          NOT NULL DEFAULT 30,
  created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recipes_user_id ON recipes(user_id);

# --- !Downs

DROP TABLE IF EXISTS recipes;
