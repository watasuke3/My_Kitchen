package repositories

import com.google.inject.ImplementedBy
import models.User
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile.api._

import javax.inject.{Inject, Singleton}
import java.time.OffsetDateTime
import scala.concurrent.{ExecutionContext, Future}

// トレイトに切り出すことで AuthService のテストで ScalaMock による
// モック差し替えができるようにする（具象クラスはDB接続を持ちコンストラクタで即座に初期化されるためモック不可）
@ImplementedBy(classOf[UserRepositoryImpl])
trait UserRepository {
  def findByEmail(email: String): Future[Option[User]]
  def create(email: String, hashedPassword: String): Future[User]
}

@Singleton
class UserRepositoryImpl @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
    extends UserRepository {

  private val db = dbConfigProvider.get[slick.jdbc.JdbcProfile].db

  implicit val offsetDateTimeMapper: BaseColumnType[OffsetDateTime] =
    MappedColumnType.base[OffsetDateTime, java.sql.Timestamp](
      odt => java.sql.Timestamp.from(odt.toInstant),
      ts  => ts.toInstant.atOffset(java.time.ZoneOffset.UTC)
    )

  private class UsersTable(tag: Tag) extends Table[(Long, String, String, OffsetDateTime)](tag, "users") {
    def id        = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def email     = column[String]("email")
    def password  = column[String]("password")
    def createdAt = column[OffsetDateTime]("created_at")(offsetDateTimeMapper)
    def *         = (id, email, password, createdAt)
  }

  private val users = TableQuery[UsersTable]

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
