package repositories

import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile.api._

import javax.inject.{Inject, Singleton}
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SessionRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val db = dbConfigProvider.get[slick.jdbc.JdbcProfile].db

  implicit val offsetDateTimeMapper: BaseColumnType[OffsetDateTime] =
    MappedColumnType.base[OffsetDateTime, java.sql.Timestamp](
      odt => java.sql.Timestamp.from(odt.toInstant),
      ts  => ts.toInstant.atOffset(java.time.ZoneOffset.UTC)
    )

  private class SessionsTable(tag: Tag) extends Table[(String, Long, OffsetDateTime, OffsetDateTime)](tag, "sessions") {
    def id        = column[String]("id", O.PrimaryKey)
    def userId    = column[Long]("user_id")
    def expiresAt = column[OffsetDateTime]("expires_at")(offsetDateTimeMapper)
    def createdAt = column[OffsetDateTime]("created_at")(offsetDateTimeMapper)
    def *         = (id, userId, expiresAt, createdAt)
  }

  private val sessions = TableQuery[SessionsTable]

  def create(userId: Long): Future[String] = {
    val id        = UUID.randomUUID().toString.replace("-", "")
    val now       = OffsetDateTime.now()
    val expiresAt = now.plusDays(7)
    db.run(sessions += (id, userId, expiresAt, now)).map(_ => id)
  }

  def findValidSession(sessionId: String): Future[Option[Long]] = {
    val now = OffsetDateTime.now()
    db.run(
      sessions
        .filter(s => s.id === sessionId && s.expiresAt > now)
        .map(_.userId)
        .result.headOption
    )
  }

  def delete(sessionId: String): Future[Int] =
    db.run(sessions.filter(_.id === sessionId).delete)
}
