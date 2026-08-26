package services

import models.{MealPlan, MealPlanRecipe, Recipe}
import org.scalamock.scalatest.MockFactory
import org.scalatestplus.play.PlaySpec
import repositories.{MealPlanRecipeRepository, MealPlanRepository, RecipeRepository}

import java.time.{LocalDate, OffsetDateTime}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class MealPlanServiceSpec extends PlaySpec with MockFactory {

  implicit val ec: ExecutionContext = ExecutionContext.global

  private def await[A](f: Future[A]): A = Await.result(f, 3.seconds)

  private def sampleMealPlan(id: Long = 1L, userId: Long = 10L, status: String = "planned"): MealPlan = {
    val now = OffsetDateTime.now()
    MealPlan(id, userId, LocalDate.of(2026, 8, 31), "dinner", status, now, now)
  }

  private def sampleRecipe(id: Long, userId: Long = 10L): Recipe = {
    val now = OffsetDateTime.now()
    Recipe(id, userId, s"レシピ$id", None, "和食", 2, Some(30), now, now)
  }

  "MealPlanService.assign" should {

    "create a new meal plan slot and attach the given recipes when none exists yet" in {
      val mealPlanRepo       = mock[MealPlanRepository]
      val mealPlanRecipeRepo = mock[MealPlanRecipeRepository]
      val recipeRepo         = mock[RecipeRepository]
      val service            = new MealPlanService(mealPlanRepo, mealPlanRecipeRepo, recipeRepo)
      val mealPlan           = sampleMealPlan()
      val recipe             = sampleRecipe(1L, mealPlan.userId)

      inSequence {
        (mealPlanRepo.findByUserDateAndType _).expects(mealPlan.userId, mealPlan.date, mealPlan.mealType)
          .returning(Future.successful(None))
        (mealPlanRepo.create _).expects(mealPlan.userId, mealPlan.date, mealPlan.mealType)
          .returning(Future.successful(mealPlan))
        (recipeRepo.findByIds _).expects(Seq(1L)).returning(Future.successful(Seq(recipe)))
        (mealPlanRecipeRepo.replaceForMealPlan _).expects(mealPlan.id, Seq(1L)).returning(Future.successful(()))
      }

      val result = await(service.assign(mealPlan.userId, mealPlan.date, mealPlan.mealType, Seq(1L)))

      result mustBe Right(MealPlanWithRecipes(mealPlan, Seq(recipe)))
    }

    "return RecipeNotOwned when a recipe belongs to another user" in {
      val mealPlanRepo       = mock[MealPlanRepository]
      val mealPlanRecipeRepo = mock[MealPlanRecipeRepository]
      val recipeRepo         = mock[RecipeRepository]
      val service            = new MealPlanService(mealPlanRepo, mealPlanRecipeRepo, recipeRepo)
      val mealPlan           = sampleMealPlan()
      val othersRecipe       = sampleRecipe(1L, userId = 999L)

      inSequence {
        (mealPlanRepo.findByUserDateAndType _).expects(mealPlan.userId, mealPlan.date, mealPlan.mealType)
          .returning(Future.successful(None))
        (mealPlanRepo.create _).expects(mealPlan.userId, mealPlan.date, mealPlan.mealType)
          .returning(Future.successful(mealPlan))
        (recipeRepo.findByIds _).expects(Seq(1L)).returning(Future.successful(Seq(othersRecipe)))
      }

      await(service.assign(mealPlan.userId, mealPlan.date, mealPlan.mealType, Seq(1L))) mustBe Left(RecipeNotOwned)
    }
  }

  "MealPlanService.updateStatus" should {

    "return MealPlanNotFound when the slot does not exist" in {
      val mealPlanRepo       = mock[MealPlanRepository]
      val mealPlanRecipeRepo = mock[MealPlanRecipeRepository]
      val recipeRepo         = mock[RecipeRepository]
      val service            = new MealPlanService(mealPlanRepo, mealPlanRecipeRepo, recipeRepo)

      (mealPlanRepo.findById _).expects(1L).returning(Future.successful(None))

      await(service.updateStatus(1L, 10L, "eaten")) mustBe Left(MealPlanNotFound)
    }

    "return MealPlanForbidden when another user's slot is updated" in {
      val mealPlanRepo       = mock[MealPlanRepository]
      val mealPlanRecipeRepo = mock[MealPlanRecipeRepository]
      val recipeRepo         = mock[RecipeRepository]
      val service            = new MealPlanService(mealPlanRepo, mealPlanRecipeRepo, recipeRepo)
      val mealPlan           = sampleMealPlan(userId = 10L)

      (mealPlanRepo.findById _).expects(mealPlan.id).returning(Future.successful(Some(mealPlan)))

      await(service.updateStatus(mealPlan.id, 999L, "eaten")) mustBe Left(MealPlanForbidden)
    }

    "update the status and return the slot with its recipes when the owner updates it" in {
      val mealPlanRepo       = mock[MealPlanRepository]
      val mealPlanRecipeRepo = mock[MealPlanRecipeRepository]
      val recipeRepo         = mock[RecipeRepository]
      val service            = new MealPlanService(mealPlanRepo, mealPlanRecipeRepo, recipeRepo)
      val mealPlan           = sampleMealPlan()
      val updated            = mealPlan.copy(status = "eaten")
      val recipe             = sampleRecipe(1L, mealPlan.userId)

      inSequence {
        (mealPlanRepo.findById _).expects(mealPlan.id).returning(Future.successful(Some(mealPlan)))
        (mealPlanRepo.updateStatus _).expects(mealPlan.id, "eaten").returning(Future.successful(1))
        (mealPlanRecipeRepo.findByMealPlans _).expects(Seq(mealPlan.id))
          .returning(Future.successful(Seq(MealPlanRecipe(1L, mealPlan.id, 1L, 0))))
        (recipeRepo.findByIds _).expects(Seq(1L)).returning(Future.successful(Seq(recipe)))
      }

      await(service.updateStatus(mealPlan.id, mealPlan.userId, "eaten")) mustBe
        Right(MealPlanWithRecipes(updated, Seq(recipe)))
    }
  }

  "MealPlanService.unassign" should {

    "delete the slot when the owner requests it" in {
      val mealPlanRepo       = mock[MealPlanRepository]
      val mealPlanRecipeRepo = mock[MealPlanRecipeRepository]
      val recipeRepo         = mock[RecipeRepository]
      val service            = new MealPlanService(mealPlanRepo, mealPlanRecipeRepo, recipeRepo)
      val mealPlan           = sampleMealPlan()

      (mealPlanRepo.findById _).expects(mealPlan.id).returning(Future.successful(Some(mealPlan)))
      (mealPlanRepo.delete _).expects(mealPlan.id).returning(Future.successful(1))

      await(service.unassign(mealPlan.id, mealPlan.userId)) mustBe Right(())
    }

    "return MealPlanForbidden when another user tries to delete it" in {
      val mealPlanRepo       = mock[MealPlanRepository]
      val mealPlanRecipeRepo = mock[MealPlanRecipeRepository]
      val recipeRepo         = mock[RecipeRepository]
      val service            = new MealPlanService(mealPlanRepo, mealPlanRecipeRepo, recipeRepo)
      val mealPlan           = sampleMealPlan(userId = 10L)

      (mealPlanRepo.findById _).expects(mealPlan.id).returning(Future.successful(Some(mealPlan)))

      await(service.unassign(mealPlan.id, 999L)) mustBe Left(MealPlanForbidden)
    }
  }

  "MealPlanService.listRange" should {
    "attach recipes to their meal plan slot" in {
      val mealPlanRepo       = mock[MealPlanRepository]
      val mealPlanRecipeRepo = mock[MealPlanRecipeRepository]
      val recipeRepo         = mock[RecipeRepository]
      val service            = new MealPlanService(mealPlanRepo, mealPlanRecipeRepo, recipeRepo)
      val mp                 = sampleMealPlan()
      val recipe             = sampleRecipe(1L, mp.userId)
      val from               = LocalDate.of(2026, 8, 24)
      val to                 = LocalDate.of(2026, 8, 30)

      (mealPlanRepo.findByUserAndDateRange _).expects(mp.userId, from, to).returning(Future.successful(Seq(mp)))
      (mealPlanRecipeRepo.findByMealPlans _).expects(Seq(mp.id))
        .returning(Future.successful(Seq(MealPlanRecipe(1L, mp.id, 1L, 0))))
      (recipeRepo.findByIds _).expects(Seq(1L)).returning(Future.successful(Seq(recipe)))

      await(service.listRange(mp.userId, from, to)) mustBe Seq(MealPlanWithRecipes(mp, Seq(recipe)))
    }
  }
}
