package repositories

import models.User
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile.api._

import javax.inject.{Inject, Singleton}
import java.time.OffsetDateTime
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val db = dbConfigProvider.get[slick.jdbc.JdbcProfile].db

  private class UsersTable(tag: Tag) extends Table[(Long, String, String, OffsetDateTime)](tag, "users") {
    def id        = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def email     = column[String]("email")
    def password  = column[String]("password")
    def createdAt = column[OffsetDateTime]("created_at")
    def *         = (id, email, password, createdAt)
  }

  private val users = TableQuery[UsersTable]

  implicit val offsetDateTimeMapper: BaseColumnType[OffsetDateTime] =
    MappedColumnType.base[OffsetDateTime, java.sql.Timestamp](
      odt => java.sql.Timestamp.from(odt.toInstant),
      ts  => ts.toInstant.atOffset(java.time.ZoneOffset.UTC)
    )

  def findByEmail(email: String): Future[Option[User]] =
    db.run(users.filter(_.email === email).result.headOption)
      .map(_.map { case (id, e, pw, ca) => User(id, e, pw, ca) })

  def create(email: String, hashedPassword: String): Future[User] = {
    val now = OffsetDateTime.now()
    val action = (users.map(u => (u.email, u.password, u.createdAt))
      returning users.map(_.id)
      += (email, hashedPassword, now))
    db.run(action).map(id => User(id, email, hashedPassword, now))
  }
}
