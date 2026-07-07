package models

import java.time.OffsetDateTime

case class User(
  id: Long,
  email: String,
  password: String,
  createdAt: OffsetDateTime
)
