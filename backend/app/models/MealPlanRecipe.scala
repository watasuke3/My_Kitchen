package models

case class MealPlanRecipe(
  id:         Long,
  mealPlanId: Long,
  recipeId:   Long,
  sortOrder:  Int
)
