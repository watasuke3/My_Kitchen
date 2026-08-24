package services

import models.{Ingredient, IngredientInput, Recipe}
import repositories.{IngredientRepository, RecipeRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

sealed trait RecipeError
case object RecipeNotFound  extends RecipeError
case object RecipeForbidden extends RecipeError

case class RecipeWithIngredients(recipe: Recipe, ingredients: Seq[Ingredient])

@Singleton
class RecipeService @Inject()(
  recipeRepo:     RecipeRepository,
  ingredientRepo: IngredientRepository
)(implicit ec: ExecutionContext) {

  private def toTuples(inputs: Seq[IngredientInput]): Seq[(String, Option[BigDecimal], Option[String])] =
    inputs.map(i => (i.name, i.amount, i.unit))

  def list(userId: Long): Future[Seq[RecipeWithIngredients]] =
    recipeRepo.findAllByUser(userId).flatMap { recipes =>
      ingredientRepo.findByRecipes(recipes.map(_.id)).map { allIngredients =>
        val byRecipe = allIngredients.groupBy(_.recipeId)
        recipes.map(r => RecipeWithIngredients(r, byRecipe.getOrElse(r.id, Seq.empty)))
      }
    }

  def get(id: Long, userId: Long): Future[Either[RecipeError, RecipeWithIngredients]] =
    recipeRepo.findById(id).flatMap {
      case None                          => Future.successful(Left(RecipeNotFound))
      case Some(r) if r.userId != userId => Future.successful(Left(RecipeForbidden))
      case Some(r) =>
        ingredientRepo.findByRecipe(id).map(is => Right(RecipeWithIngredients(r, is)))
    }

  def create(
    userId: Long, title: String, description: Option[String],
    category: String, servings: Int, cookTimeMinutes: Option[Int],
    ingredients: Seq[IngredientInput]
  ): Future[RecipeWithIngredients] =
    recipeRepo.create(userId, title, description, category, servings, cookTimeMinutes).flatMap { r =>
      ingredientRepo.replaceForRecipe(r.id, toTuples(ingredients)).flatMap { _ =>
        ingredientRepo.findByRecipe(r.id).map(is => RecipeWithIngredients(r, is))
      }
    }

  def update(
    id: Long, userId: Long,
    title: String, description: Option[String],
    category: String, servings: Int, cookTimeMinutes: Option[Int],
    ingredients: Seq[IngredientInput]
  ): Future[Either[RecipeError, RecipeWithIngredients]] =
    recipeRepo.findById(id).flatMap {
      case None                          => Future.successful(Left(RecipeNotFound))
      case Some(r) if r.userId != userId => Future.successful(Left(RecipeForbidden))
      case Some(_) =>
        recipeRepo.update(id, title, description, category, servings, cookTimeMinutes).flatMap { _ =>
          ingredientRepo.replaceForRecipe(id, toTuples(ingredients)).flatMap { _ =>
            recipeRepo.findById(id).flatMap {
              case Some(updated) => ingredientRepo.findByRecipe(id).map(is => Right(RecipeWithIngredients(updated, is)))
              case None          => Future.successful(Left(RecipeNotFound))
            }
          }
        }
    }

  def delete(id: Long, userId: Long): Future[Either[RecipeError, Unit]] =
    recipeRepo.findById(id).flatMap {
      case None                          => Future.successful(Left(RecipeNotFound))
      case Some(r) if r.userId != userId => Future.successful(Left(RecipeForbidden))
      case Some(_) => recipeRepo.delete(id).map(_ => Right(()))
    }
}
