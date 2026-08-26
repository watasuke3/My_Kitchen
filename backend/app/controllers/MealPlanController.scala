package controllers

import models.{MealPlan, Recipe}
import play.api.libs.json._
import play.api.mvc._
import services._

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

@Singleton
class MealPlanController @Inject()(
  cc:              ControllerComponents,
  authService:     AuthService,
  mealPlanService: MealPlanService
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  private val SessionCookieName = "SESSION_ID"

  private def withAuth[A](request: Request[A])(f: Long => Future[Result]): Future[Result] =
    request.cookies.get(SessionCookieName) match {
      case None => Future.successful(Unauthorized(Json.obj("error" -> "未ログイン")))
      case Some(cookie) =>
        authService.validateSession(cookie.value).flatMap {
          case None         => Future.successful(Unauthorized(Json.obj("error" -> "セッション切れ")))
          case Some(userId) => f(userId)
        }
    }

  private def parseDate(raw: String): Try[LocalDate] =
    Try(LocalDate.parse(raw))

  private def recipeJson(r: Recipe): JsObject = Json.obj(
    "id"    -> r.id,
    "title" -> r.title
  )

  private def toJson(mw: MealPlanWithRecipes): JsObject = {
    val mp = mw.mealPlan
    Json.obj(
      "id"        -> mp.id,
      "userId"    -> mp.userId,
      "date"      -> mp.date.toString,
      "mealType"  -> mp.mealType,
      "status"    -> mp.status,
      "createdAt" -> mp.createdAt.toString,
      "updatedAt" -> mp.updatedAt.toString,
      "recipes"   -> mw.recipes.map(recipeJson)
    )
  }

  private def errorResult(err: MealPlanError): Result = err match {
    case MealPlanNotFound  => NotFound(Json.obj("error" -> "献立枠が見つかりません"))
    case MealPlanForbidden => Forbidden(Json.obj("error" -> "アクセス権限がありません"))
    case RecipeNotOwned    => BadRequest(Json.obj("error" -> "指定されたレシピが見つからないか、自分のレシピではありません"))
  }

  def list(from: String, to: String): Action[AnyContent] = Action.async { request =>
    withAuth(request) { userId =>
      (parseDate(from), parseDate(to)) match {
        case (Success(f), Success(t)) =>
          mealPlanService.listRange(userId, f, t).map(rs => Ok(Json.toJson(rs.map(toJson))))
        case _ =>
          Future.successful(BadRequest(Json.obj("error" -> "from/to は YYYY-MM-DD 形式で指定してください")))
      }
    }
  }

  def assign(): Action[JsValue] = Action.async(parse.json) { request =>
    withAuth(request) { userId =>
      val dateOpt      = (request.body \ "date").asOpt[String].flatMap(d => parseDate(d).toOption)
      val mealTypeOpt  = (request.body \ "mealType").asOpt[String].filter(MealPlan.MealTypes.contains)
      val recipeIds    = (request.body \ "recipeIds").asOpt[Seq[Long]].getOrElse(Seq.empty)

      (dateOpt, mealTypeOpt) match {
        case (Some(date), Some(mealType)) =>
          mealPlanService.assign(userId, date, mealType, recipeIds).map {
            case Right(mw)  => Ok(toJson(mw))
            case Left(err)  => errorResult(err)
          }
        case _ =>
          Future.successful(BadRequest(Json.obj("error" -> "date は YYYY-MM-DD、mealType は breakfast/lunch/dinner を指定してください")))
      }
    }
  }

  def updateStatus(id: Long): Action[JsValue] = Action.async(parse.json) { request =>
    withAuth(request) { userId =>
      (request.body \ "status").asOpt[String].filter(MealPlan.Statuses.contains) match {
        case None =>
          Future.successful(BadRequest(Json.obj("error" -> "status は planned/eaten/skipped を指定してください")))
        case Some(status) =>
          mealPlanService.updateStatus(id, userId, status).map {
            case Right(mw) => Ok(toJson(mw))
            case Left(err) => errorResult(err)
          }
      }
    }
  }

  def delete(id: Long): Action[AnyContent] = Action.async { request =>
    withAuth(request) { userId =>
      mealPlanService.unassign(id, userId).map {
        case Right(_)  => NoContent
        case Left(err) => errorResult(err)
      }
    }
  }
}
