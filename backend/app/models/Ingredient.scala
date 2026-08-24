package models

case class Ingredient(
  id:        Long,
  recipeId:  Long,
  name:      String,
  amount:    Option[BigDecimal],
  unit:      Option[String],
  sortOrder: Int
)

// 作成・更新時にクライアントから受け取る入力（id・recipeIdはサーバー側で採番するため持たない）
case class IngredientInput(
  name:   String,
  amount: Option[BigDecimal],
  unit:   Option[String]
)
