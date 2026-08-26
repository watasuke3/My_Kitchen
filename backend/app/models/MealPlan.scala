package models

import java.time.{LocalDate, OffsetDateTime}

case class MealPlan(
  id:        Long,
  userId:    Long,
  date:      LocalDate,
  mealType:  String,
  status:    String,
  createdAt: OffsetDateTime,
  updatedAt: OffsetDateTime
)

object MealPlan {
  val MealTypes: Set[String] = Set("breakfast", "lunch", "dinner")
  val Statuses:  Set[String] = Set("planned", "eaten", "skipped")
}
