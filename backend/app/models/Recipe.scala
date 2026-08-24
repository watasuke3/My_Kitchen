package models

import java.time.OffsetDateTime

case class Recipe(
  id:              Long,
  userId:          Long,
  title:           String,
  description:     Option[String],
  category:        String,
  servings:        Int,
  cookTimeMinutes: Option[Int],
  createdAt:       OffsetDateTime,
  updatedAt:       OffsetDateTime
)
