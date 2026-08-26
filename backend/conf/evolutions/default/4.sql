# --- !Ups

CREATE TABLE meal_plans (
  id         BIGSERIAL    PRIMARY KEY,
  user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  date       DATE         NOT NULL,
  meal_type  VARCHAR(20)  NOT NULL,
  status     VARCHAR(20)  NOT NULL DEFAULT 'planned',
  created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, date, meal_type)
);

CREATE INDEX idx_meal_plans_user_id_date ON meal_plans(user_id, date);

CREATE TABLE meal_plan_recipes (
  id            BIGSERIAL PRIMARY KEY,
  meal_plan_id  BIGINT    NOT NULL REFERENCES meal_plans(id) ON DELETE CASCADE,
  recipe_id     BIGINT    NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  sort_order    INT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_meal_plan_recipes_meal_plan_id ON meal_plan_recipes(meal_plan_id);
CREATE INDEX idx_meal_plan_recipes_recipe_id ON meal_plan_recipes(recipe_id);

# --- !Downs

DROP TABLE IF EXISTS meal_plan_recipes;
DROP TABLE IF EXISTS meal_plans;
