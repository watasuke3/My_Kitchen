package repositories

import models.Recipe
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile.api._

import javax.inject.{Inject, Singleton}
import java.time.OffsetDateTime
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RecipeRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val db = dbConfigProvider.get[slick.jdbc.JdbcProfile].db

  implicit val offsetDateTimeMapper: BaseColumnType[OffsetDateTime] =
    MappedColumnType.base[OffsetDateTime, java.sql.Timestamp](
      odt => java.sql.Timestamp.from(odt.toInstant),
      ts  => ts.toInstant.atOffset(java.time.ZoneOffset.UTC)
    )

  // Slick のテーブル定義: DB のカラムと Scala の型を対応づける
  private type RecipeRow = (Long, Long, String, Option[String], String, Int, Int, OffsetDateTime, OffsetDateTime)

  private class RecipesTable(tag: Tag) extends Table[RecipeRow](tag, "recipes") {
    def id              = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId          = column[Long]("user_id")
    def title           = column[String]("title")
    def description     = column[Option[String]]("description")
    def category        = column[String]("category")
    def servings        = column[Int]("servings")
    def cookTimeMinutes = column[Int]("cook_time_minutes")
    def createdAt       = column[OffsetDateTime]("created_at")(offsetDateTimeMapper)
    def updatedAt       = column[OffsetDateTime]("updated_at")(offsetDateTimeMapper)
    def * = (id, userId, title, description, category, servings, cookTimeMinutes, createdAt, updatedAt)
  }

  private val recipes = TableQuery[RecipesTable]

  private def toModel(row: RecipeRow): Recipe =
    Recipe(row._1, row._2, row._3, row._4, row._5, row._6, row._7, row._8, row._9)

  def findAllByUser(userId: Long): Future[Seq[Recipe]] =
    db.run(recipes.filter(_.userId === userId).sortBy(_.createdAt.desc).result)
      .map(_.map(toModel))

  def findById(id: Long): Future[Option[Recipe]] =
    db.run(recipes.filter(_.id === id).result.headOption)
      .map(_.map(toModel))

  def create(
    userId: Long, title: String, description: Option[String],
    category: String, servings: Int, cookTimeMinutes: Int
  ): Future[Recipe] = {
    val now = OffsetDateTime.now()
    val action = (recipes.map(r => (r.userId, r.title, r.description, r.category, r.servings, r.cookTimeMinutes, r.createdAt, r.updatedAt))
      returning recipes.map(_.id)
      += (userId, title, description, category, servings, cookTimeMinutes, now, now))
    db.run(action).map(id => Recipe(id, userId, title, description, category, servings, cookTimeMinutes, now, now))
  }

  def update(
    id: Long, title: String, description: Option[String],
    category: String, servings: Int, cookTimeMinutes: Int
  ): Future[Int] = {
    val now = OffsetDateTime.now()
    db.run(
      recipes.filter(_.id === id)
        .map(r => (r.title, r.description, r.category, r.servings, r.cookTimeMinutes, r.updatedAt))
        .update((title, description, category, servings, cookTimeMinutes, now))
    )
  }

  def delete(id: Long): Future[Int] =
    db.run(recipes.filter(_.id === id).delete)
}
