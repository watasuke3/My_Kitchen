package repositories

import com.google.inject.ImplementedBy
import models.MealPlanRecipe
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile.api._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[MealPlanRecipeRepositoryImpl])
trait MealPlanRecipeRepository {
  def findByMealPlans(mealPlanIds: Seq[Long]): Future[Seq[MealPlanRecipe]]
  def replaceForMealPlan(mealPlanId: Long, recipeIds: Seq[Long]): Future[Unit]
}

@Singleton
class MealPlanRecipeRepositoryImpl @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
    extends MealPlanRecipeRepository {

  private val db = dbConfigProvider.get[slick.jdbc.JdbcProfile].db

  private type MealPlanRecipeRow = (Long, Long, Long, Int)

  private class MealPlanRecipesTable(tag: Tag) extends Table[MealPlanRecipeRow](tag, "meal_plan_recipes") {
    def id         = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def mealPlanId = column[Long]("meal_plan_id")
    def recipeId   = column[Long]("recipe_id")
    def sortOrder  = column[Int]("sort_order")
    def * = (id, mealPlanId, recipeId, sortOrder)
  }

  private val mealPlanRecipes = TableQuery[MealPlanRecipesTable]

  private def toModel(row: MealPlanRecipeRow): MealPlanRecipe =
    MealPlanRecipe(row._1, row._2, row._3, row._4)

  def findByMealPlans(mealPlanIds: Seq[Long]): Future[Seq[MealPlanRecipe]] =
    if (mealPlanIds.isEmpty) Future.successful(Seq.empty)
    else db.run(mealPlanRecipes.filter(_.mealPlanId inSet mealPlanIds).sortBy(_.sortOrder.asc).result)
      .map(_.map(toModel))

  // 枠に割り当てるレシピを丸ごと入れ替える（ingredients.replaceForRecipe と同じパターン）
  def replaceForMealPlan(mealPlanId: Long, recipeIds: Seq[Long]): Future[Unit] = {
    val insertAction = mealPlanRecipes.map(r => (r.mealPlanId, r.recipeId, r.sortOrder)) ++=
      recipeIds.zipWithIndex.map { case (recipeId, idx) => (mealPlanId, recipeId, idx) }

    val action = for {
      _ <- mealPlanRecipes.filter(_.mealPlanId === mealPlanId).delete
      _ <- insertAction
    } yield ()

    db.run(action.transactionally)
  }
}
