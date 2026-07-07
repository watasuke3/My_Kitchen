package services

import models.Recipe
import repositories.RecipeRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

sealed trait RecipeError
case object RecipeNotFound  extends RecipeError
case object RecipeForbidden extends RecipeError

@Singleton
class RecipeService @Inject()(recipeRepo: RecipeRepository)(implicit ec: ExecutionContext) {

  def list(userId: Long): Future[Seq[Recipe]] =
    recipeRepo.findAllByUser(userId)

  def get(id: Long, userId: Long): Future[Either[RecipeError, Recipe]] =
    recipeRepo.findById(id).map {
      case None                         => Left(RecipeNotFound)
      case Some(r) if r.userId != userId => Left(RecipeForbidden)
      case Some(r)                       => Right(r)
    }

  def create(
    userId: Long, title: String, description: Option[String],
    category: String, servings: Int, cookTimeMinutes: Int
  ): Future[Recipe] =
    recipeRepo.create(userId, title, description, category, servings, cookTimeMinutes)

  def update(
    id: Long, userId: Long,
    title: String, description: Option[String],
    category: String, servings: Int, cookTimeMinutes: Int
  ): Future[Either[RecipeError, Recipe]] =
    recipeRepo.findById(id).flatMap {
      case None                         => Future.successful(Left(RecipeNotFound))
      case Some(r) if r.userId != userId => Future.successful(Left(RecipeForbidden))
      case Some(_) =>
        recipeRepo.update(id, title, description, category, servings, cookTimeMinutes).flatMap { _ =>
          recipeRepo.findById(id).map {
            case Some(updated) => Right(updated)
            case None          => Left(RecipeNotFound)
          }
        }
    }

  def delete(id: Long, userId: Long): Future[Either[RecipeError, Unit]] =
    recipeRepo.findById(id).flatMap {
      case None                         => Future.successful(Left(RecipeNotFound))
      case Some(r) if r.userId != userId => Future.successful(Left(RecipeForbidden))
      case Some(_) => recipeRepo.delete(id).map(_ => Right(()))
    }
}
