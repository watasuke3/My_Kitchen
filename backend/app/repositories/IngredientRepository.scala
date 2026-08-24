package repositories

import com.google.inject.ImplementedBy
import models.Ingredient
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile.api._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[IngredientRepositoryImpl])
trait IngredientRepository {
  def findByRecipe(recipeId: Long): Future[Seq[Ingredient]]
  def findByRecipes(recipeIds: Seq[Long]): Future[Seq[Ingredient]]
  def replaceForRecipe(recipeId: Long, inputs: Seq[(String, Option[BigDecimal], Option[String])]): Future[Unit]
}

@Singleton
class IngredientRepositoryImpl @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
    extends IngredientRepository {

  private val db = dbConfigProvider.get[slick.jdbc.JdbcProfile].db

  private type IngredientRow = (Long, Long, String, Option[BigDecimal], Option[String], Int)

  private class IngredientsTable(tag: Tag) extends Table[IngredientRow](tag, "ingredients") {
    def id        = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def recipeId  = column[Long]("recipe_id")
    def name      = column[String]("name")
    def amount    = column[Option[BigDecimal]]("amount")
    def unit      = column[Option[String]]("unit")
    def sortOrder = column[Int]("sort_order")
    def * = (id, recipeId, name, amount, unit, sortOrder)
  }

  private val ingredients = TableQuery[IngredientsTable]

  private def toModel(row: IngredientRow): Ingredient =
    Ingredient(row._1, row._2, row._3, row._4, row._5, row._6)

  def findByRecipe(recipeId: Long): Future[Seq[Ingredient]] =
    db.run(ingredients.filter(_.recipeId === recipeId).sortBy(_.sortOrder.asc).result)
      .map(_.map(toModel))

  def findByRecipes(recipeIds: Seq[Long]): Future[Seq[Ingredient]] =
    db.run(ingredients.filter(_.recipeId inSet recipeIds).sortBy(_.sortOrder.asc).result)
      .map(_.map(toModel))

  // レシピの具材を丸ごと入れ替える（作成・更新時に使用。入力順を sort_order として保存する）
  def replaceForRecipe(recipeId: Long, inputs: Seq[(String, Option[BigDecimal], Option[String])]): Future[Unit] = {
    val insertAction = ingredients.map(i => (i.recipeId, i.name, i.amount, i.unit, i.sortOrder)) ++=
      inputs.zipWithIndex.map { case ((name, amount, unit), idx) => (recipeId, name, amount, unit, idx) }

    val action = for {
      _ <- ingredients.filter(_.recipeId === recipeId).delete
      _ <- insertAction
    } yield ()

    db.run(action.transactionally)
  }
}
