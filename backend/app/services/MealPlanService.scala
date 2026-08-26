package services

import models.{MealPlan, Recipe}
import repositories.{MealPlanRecipeRepository, MealPlanRepository, RecipeRepository}

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

sealed trait MealPlanError
case object MealPlanNotFound  extends MealPlanError
case object MealPlanForbidden extends MealPlanError
case object RecipeNotOwned    extends MealPlanError

case class MealPlanWithRecipes(mealPlan: MealPlan, recipes: Seq[Recipe])

@Singleton
class MealPlanService @Inject()(
  mealPlanRepo:       MealPlanRepository,
  mealPlanRecipeRepo: MealPlanRecipeRepository,
  recipeRepo:         RecipeRepository
)(implicit ec: ExecutionContext) {

  private def withRecipes(mealPlan: MealPlan): Future[MealPlanWithRecipes] =
    mealPlanRecipeRepo.findByMealPlans(Seq(mealPlan.id)).flatMap { links =>
      recipeRepo.findByIds(links.map(_.recipeId)).map { recipes =>
        val byId = recipes.map(r => r.id -> r).toMap
        MealPlanWithRecipes(mealPlan, links.flatMap(l => byId.get(l.recipeId)))
      }
    }

  def listRange(userId: Long, from: LocalDate, to: LocalDate): Future[Seq[MealPlanWithRecipes]] =
    mealPlanRepo.findByUserAndDateRange(userId, from, to).flatMap { plans =>
      mealPlanRecipeRepo.findByMealPlans(plans.map(_.id)).flatMap { links =>
        recipeRepo.findByIds(links.map(_.recipeId).distinct).map { recipes =>
          val byId        = recipes.map(r => r.id -> r).toMap
          val linksByPlan = links.groupBy(_.mealPlanId)
          plans.map { p =>
            val planRecipes = linksByPlan.getOrElse(p.id, Seq.empty).flatMap(l => byId.get(l.recipeId))
            MealPlanWithRecipes(p, planRecipes)
          }
        }
      }
    }

  def assign(
    userId: Long, date: LocalDate, mealType: String, recipeIds: Seq[Long]
  ): Future[Either[MealPlanError, MealPlanWithRecipes]] =
    mealPlanRepo.findByUserDateAndType(userId, date, mealType).flatMap { existing =>
      recipeRepo.findByIds(recipeIds).flatMap { recipes =>
        if (recipes.size != recipeIds.size || recipes.exists(_.userId != userId)) {
          Future.successful(Left(RecipeNotOwned))
        } else {
          val mealPlanFuture = existing match {
            case Some(mp) => Future.successful(mp)
            case None     => mealPlanRepo.create(userId, date, mealType)
          }
          mealPlanFuture.flatMap { mealPlan =>
            mealPlanRecipeRepo.replaceForMealPlan(mealPlan.id, recipeIds)
              .map(_ => Right(MealPlanWithRecipes(mealPlan, recipes)))
          }
        }
      }
    }

  def updateStatus(id: Long, userId: Long, status: String): Future[Either[MealPlanError, MealPlanWithRecipes]] =
    mealPlanRepo.findById(id).flatMap {
      case None                             => Future.successful(Left(MealPlanNotFound))
      case Some(mp) if mp.userId != userId  => Future.successful(Left(MealPlanForbidden))
      case Some(mp) =>
        mealPlanRepo.updateStatus(id, status).flatMap { _ =>
          withRecipes(mp.copy(status = status)).map(Right(_))
        }
    }

  def unassign(id: Long, userId: Long): Future[Either[MealPlanError, Unit]] =
    mealPlanRepo.findById(id).flatMap {
      case None                            => Future.successful(Left(MealPlanNotFound))
      case Some(mp) if mp.userId != userId => Future.successful(Left(MealPlanForbidden))
      case Some(_)                         => mealPlanRepo.delete(id).map(_ => Right(()))
    }
}
