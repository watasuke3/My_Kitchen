# --- !Ups

-- 調理時間はレシピ作成時に未確定でもよく、後から設定できるようにNULLを許容する
ALTER TABLE recipes ALTER COLUMN cook_time_minutes DROP NOT NULL;
ALTER TABLE recipes ALTER COLUMN cook_time_minutes DROP DEFAULT;

CREATE TABLE ingredients (
  id         BIGSERIAL     PRIMARY KEY,
  recipe_id  BIGINT        NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  name       VARCHAR(255)  NOT NULL,
  amount     NUMERIC,
  unit       VARCHAR(50),
  sort_order INT           NOT NULL DEFAULT 0
);

CREATE INDEX idx_ingredients_recipe_id ON ingredients(recipe_id);

-- ログイン試行回数のDBカウンタは play-guard (インメモリのトークンバケット) への
-- 一本化に伴い不要になったため削除する
DROP TABLE IF EXISTS login_attempts;

# --- !Downs

CREATE TABLE login_attempts (
  email       VARCHAR(255) NOT NULL,
  failed_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  PRIMARY KEY (email, failed_at)
);

DROP TABLE IF EXISTS ingredients;

ALTER TABLE recipes ALTER COLUMN cook_time_minutes SET DEFAULT 30;
UPDATE recipes SET cook_time_minutes = 30 WHERE cook_time_minutes IS NULL;
ALTER TABLE recipes ALTER COLUMN cook_time_minutes SET NOT NULL;
