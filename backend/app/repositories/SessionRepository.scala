package repositories

import com.google.inject.ImplementedBy
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile.api._

import javax.inject.{Inject, Singleton}
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

// トレイトに切り出すことで AuthService のテストで ScalaMock による
// モック差し替えができるようにする（具象クラスはDB接続を持ちコンストラクタで即座に初期化されるためモック不可）
@ImplementedBy(classOf[SessionRepositoryImpl])
trait SessionRepository {
  def create(userId: Long): Future[String]
  def findValidSession(sessionId: String): Future[Option[Long]]
  def delete(sessionId: String): Future[Int]
}

@Singleton
class SessionRepositoryImpl @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
    extends SessionRepository {

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
