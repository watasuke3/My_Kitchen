package services

import models.{Ingredient, IngredientInput, Recipe}
import org.scalamock.scalatest.MockFactory
import org.scalatestplus.play.PlaySpec
import repositories.{IngredientRepository, RecipeRepository}

import java.time.OffsetDateTime
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class RecipeServiceSpec extends PlaySpec with MockFactory {

  implicit val ec: ExecutionContext = ExecutionContext.global

  private def await[A](f: Future[A]): A = Await.result(f, 3.seconds)

  private def sampleRecipe(id: Long = 1L, userId: Long = 10L): Recipe = {
    val now = OffsetDateTime.now()
    Recipe(id, userId, "肉じゃが", None, "和食", 2, Some(30), now, now)
  }

  "RecipeService.get" should {

    "return the recipe with its ingredients when the owner requests it" in {
      val recipeRepo     = mock[RecipeRepository]
      val ingredientRepo = mock[IngredientRepository]
      val service        = new RecipeService(recipeRepo, ingredientRepo)
      val recipe         = sampleRecipe()
      val ingredients    = Seq(Ingredient(1L, recipe.id, "じゃがいも", Some(BigDecimal(200)), Some("g"), 0))

      (recipeRepo.findById _).expects(recipe.id).returning(Future.successful(Some(recipe)))
      (ingredientRepo.findByRecipe _).expects(recipe.id).returning(Future.successful(ingredients))

      await(service.get(recipe.id, recipe.userId)) mustBe Right(RecipeWithIngredients(recipe, ingredients))
    }

    "return RecipeNotFound when the recipe does not exist" in {
      val recipeRepo     = mock[RecipeRepository]
      val ingredientRepo = mock[IngredientRepository]
      val service        = new RecipeService(recipeRepo, ingredientRepo)

      (recipeRepo.findById _).expects(99L).returning(Future.successful(None))

      await(service.get(99L, 10L)) mustBe Left(RecipeNotFound)
    }

    "return RecipeForbidden when another user's recipe is requested" in {
      val recipeRepo     = mock[RecipeRepository]
      val ingredientRepo = mock[IngredientRepository]
      val service        = new RecipeService(recipeRepo, ingredientRepo)
      val recipe         = sampleRecipe(userId = 10L)

      (recipeRepo.findById _).expects(recipe.id).returning(Future.successful(Some(recipe)))

      await(service.get(recipe.id, 999L)) mustBe Left(RecipeForbidden)
    }
  }

  "RecipeService.create" should {
    "persist the recipe and replace its ingredients" in {
      val recipeRepo       = mock[RecipeRepository]
      val ingredientRepo   = mock[IngredientRepository]
      val service          = new RecipeService(recipeRepo, ingredientRepo)
      val recipe           = sampleRecipe()
      val inputs           = Seq(IngredientInput("じゃがいも", Some(BigDecimal(200)), Some("g")))
      val savedIngredients = Seq(Ingredient(1L, recipe.id, "じゃがいも", Some(BigDecimal(200)), Some("g"), 0))

      (recipeRepo.create _)
        .expects(recipe.userId, recipe.title, recipe.description, recipe.category, recipe.servings, recipe.cookTimeMinutes)
        .returning(Future.successful(recipe))
      (ingredientRepo.replaceForRecipe _)
        .expects(recipe.id, Seq(("じゃがいも", Some(BigDecimal(200)), Some("g"))))
        .returning(Future.successful(()))
      (ingredientRepo.findByRecipe _).expects(recipe.id).returning(Future.successful(savedIngredients))

      val result = await(service.create(
        recipe.userId, recipe.title, recipe.description, recipe.category, recipe.servings, recipe.cookTimeMinutes, inputs
      ))

      result mustBe RecipeWithIngredients(recipe, savedIngredients)
    }
  }

  "RecipeService.update" should {

    "return RecipeNotFound when updating a recipe that does not exist" in {
      val recipeRepo     = mock[RecipeRepository]
      val ingredientRepo = mock[IngredientRepository]
      val service        = new RecipeService(recipeRepo, ingredientRepo)

      (recipeRepo.findById _).expects(1L).returning(Future.successful(None))

      await(service.update(1L, 10L, "新しい名前", None, "その他", 2, None, Seq.empty)) mustBe Left(RecipeNotFound)
    }

    "return RecipeForbidden when updating another user's recipe" in {
      val recipeRepo     = mock[RecipeRepository]
      val ingredientRepo = mock[IngredientRepository]
      val service        = new RecipeService(recipeRepo, ingredientRepo)
      val recipe         = sampleRecipe(userId = 10L)

      (recipeRepo.findById _).expects(recipe.id).returning(Future.successful(Some(recipe)))

      await(service.update(recipe.id, 999L, "新しい名前", None, "その他", 2, None, Seq.empty)) mustBe Left(RecipeForbidden)
    }

    "update the recipe and replace its ingredients when the owner updates it" in {
      val recipeRepo       = mock[RecipeRepository]
      val ingredientRepo   = mock[IngredientRepository]
      val service          = new RecipeService(recipeRepo, ingredientRepo)
      val recipe           = sampleRecipe()
      val updated          = recipe.copy(title = "肉じゃが（更新）")
      val inputs           = Seq(IngredientInput("にんじん", Some(BigDecimal(100)), Some("g")))
      val savedIngredients = Seq(Ingredient(2L, recipe.id, "にんじん", Some(BigDecimal(100)), Some("g"), 0))

      inSequence {
        (recipeRepo.findById _).expects(recipe.id).returning(Future.successful(Some(recipe)))
        (recipeRepo.update _)
          .expects(recipe.id, updated.title, updated.description, updated.category, updated.servings, updated.cookTimeMinutes)
          .returning(Future.successful(1))
        (ingredientRepo.replaceForRecipe _)
          .expects(recipe.id, Seq(("にんじん", Some(BigDecimal(100)), Some("g"))))
          .returning(Future.successful(()))
        (recipeRepo.findById _).expects(recipe.id).returning(Future.successful(Some(updated)))
        (ingredientRepo.findByRecipe _).expects(recipe.id).returning(Future.successful(savedIngredients))
      }

      val result = await(service.update(
        recipe.id, recipe.userId, updated.title, updated.description, updated.category, updated.servings, updated.cookTimeMinutes, inputs
      ))

      result mustBe Right(RecipeWithIngredients(updated, savedIngredients))
    }
  }

  "RecipeService.delete" should {

    "delete the recipe when the owner requests it" in {
      val recipeRepo     = mock[RecipeRepository]
      val ingredientRepo = mock[IngredientRepository]
      val service        = new RecipeService(recipeRepo, ingredientRepo)
      val recipe         = sampleRecipe()

      (recipeRepo.findById _).expects(recipe.id).returning(Future.successful(Some(recipe)))
      (recipeRepo.delete _).expects(recipe.id).returning(Future.successful(1))

      await(service.delete(recipe.id, recipe.userId)) mustBe Right(())
    }

    "return RecipeForbidden when another user tries to delete it" in {
      val recipeRepo     = mock[RecipeRepository]
      val ingredientRepo = mock[IngredientRepository]
      val service        = new RecipeService(recipeRepo, ingredientRepo)
      val recipe         = sampleRecipe(userId = 10L)

      (recipeRepo.findById _).expects(recipe.id).returning(Future.successful(Some(recipe)))

      await(service.delete(recipe.id, 999L)) mustBe Left(RecipeForbidden)
    }
  }

  "RecipeService.list" should {
    "group ingredients under their recipe" in {
      val recipeRepo     = mock[RecipeRepository]
      val ingredientRepo = mock[IngredientRepository]
      val service        = new RecipeService(recipeRepo, ingredientRepo)
      val r1   = sampleRecipe(id = 1L)
      val r2   = sampleRecipe(id = 2L)
      val ing1 = Ingredient(1L, 1L, "じゃがいも", Some(BigDecimal(200)), Some("g"), 0)

      (recipeRepo.findAllByUser _).expects(r1.userId).returning(Future.successful(Seq(r1, r2)))
      (ingredientRepo.findByRecipes _).expects(Seq(1L, 2L)).returning(Future.successful(Seq(ing1)))

      val result = await(service.list(r1.userId))

      result mustBe Seq(RecipeWithIngredients(r1, Seq(ing1)), RecipeWithIngredients(r2, Seq.empty))
    }
  }
}
