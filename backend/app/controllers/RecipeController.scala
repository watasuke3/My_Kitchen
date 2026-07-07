package controllers

import models.Recipe
import play.api.libs.json._
import play.api.mvc._
import services.{AuthService, RecipeForbidden, RecipeNotFound, RecipeService}

import javax.inject.{Inject, Singleton}
import java.time.OffsetDateTime
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RecipeController @Inject()(
  cc:            ControllerComponents,
  authService:   AuthService,
  recipeService: RecipeService
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  private val SessionCookieName = "SESSION_ID"

  // 認証ヘルパー: セッション検証後に userId を渡す
  private def withAuth[A](request: Request[A])(f: Long => Future[Result]): Future[Result] =
    request.cookies.get(SessionCookieName) match {
      case None => Future.successful(Unauthorized(Json.obj("error" -> "未ログイン")))
      case Some(cookie) =>
        authService.validateSession(cookie.value).flatMap {
          case None         => Future.successful(Unauthorized(Json.obj("error" -> "セッション切れ")))
          case Some(userId) => f(userId)
        }
    }

  // Recipe → JSON 変換
  private def toJson(r: Recipe): JsObject = Json.obj(
    "id"              -> r.id,
    "userId"          -> r.userId,
    "title"           -> r.title,
    "description"     -> r.description,
    "category"        -> r.category,
    "servings"        -> r.servings,
    "cookTimeMinutes" -> r.cookTimeMinutes,
    "createdAt"       -> r.createdAt.toString,
    "updatedAt"       -> r.updatedAt.toString
  )

  def list(): Action[AnyContent] = Action.async { request =>
    withAuth(request) { userId =>
      recipeService.list(userId).map(rs => Ok(Json.toJson(rs.map(toJson))))
    }
  }

  def create(): Action[JsValue] = Action.async(parse.json) { request =>
    withAuth(request) { userId =>
      val titleOpt    = (request.body \ "title").asOpt[String].map(_.trim).filter(_.nonEmpty)
      val description = (request.body \ "description").asOpt[String].map(_.trim).filter(_.nonEmpty)
      val category    = (request.body \ "category").asOpt[String].getOrElse("その他")
      val servings    = (request.body \ "servings").asOpt[Int].getOrElse(2)
      val cookTime    = (request.body \ "cookTimeMinutes").asOpt[Int].getOrElse(30)

      titleOpt match {
        case None =>
          Future.successful(BadRequest(Json.obj("error" -> "title は必須です")))
        case Some(title) =>
          recipeService.create(userId, title, description, category, servings, cookTime)
            .map(r => Created(toJson(r)))
      }
    }
  }

  def show(id: Long): Action[AnyContent] = Action.async { request =>
    withAuth(request) { userId =>
      recipeService.get(id, userId).map {
        case Right(r)            => Ok(toJson(r))
        case Left(RecipeNotFound) => NotFound(Json.obj("error" -> "レシピが見つかりません"))
        case Left(RecipeForbidden) => Forbidden(Json.obj("error" -> "アクセス権限がありません"))
      }
    }
  }

  def update(id: Long): Action[JsValue] = Action.async(parse.json) { request =>
    withAuth(request) { userId =>
      val titleOpt    = (request.body \ "title").asOpt[String].map(_.trim).filter(_.nonEmpty)
      val description = (request.body \ "description").asOpt[String].map(_.trim).filter(_.nonEmpty)
      val category    = (request.body \ "category").asOpt[String].getOrElse("その他")
      val servings    = (request.body \ "servings").asOpt[Int].getOrElse(2)
      val cookTime    = (request.body \ "cookTimeMinutes").asOpt[Int].getOrElse(30)

      titleOpt match {
        case None =>
          Future.successful(BadRequest(Json.obj("error" -> "title は必須です")))
        case Some(title) =>
          recipeService.update(id, userId, title, description, category, servings, cookTime).map {
            case Right(r)              => Ok(toJson(r))
            case Left(RecipeNotFound)  => NotFound(Json.obj("error" -> "レシピが見つかりません"))
            case Left(RecipeForbidden) => Forbidden(Json.obj("error" -> "アクセス権限がありません"))
          }
      }
    }
  }

  def delete(id: Long): Action[AnyContent] = Action.async { request =>
    withAuth(request) { userId =>
      recipeService.delete(id, userId).map {
        case Right(_)              => NoContent
        case Left(RecipeNotFound)  => NotFound(Json.obj("error" -> "レシピが見つかりません"))
        case Left(RecipeForbidden) => Forbidden(Json.obj("error" -> "アクセス権限がありません"))
      }
    }
  }
}
