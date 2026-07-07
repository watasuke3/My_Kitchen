package controllers

import play.api.libs.json._
import play.api.mvc._
import services.{AccountLocked, AuthService, EmailAlreadyExists, InvalidCredentials, UnexpectedError}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthController @Inject()(
  cc: ControllerComponents,
  authService: AuthService
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  private val SessionCookieName = "SESSION_ID"

  private def sessionCookie(id: String): Cookie =
    Cookie(
      name     = SessionCookieName,
      value    = id,
      maxAge   = Some(7 * 24 * 60 * 60),
      httpOnly = true,
      secure   = false, // 開発環境: false、本番は true
      sameSite = Some(Cookie.SameSite.Strict)
    )

  def register(): Action[JsValue] = Action.async(parse.json) { request =>
    val emailOpt    = (request.body \ "email").asOpt[String]
    val passwordOpt = (request.body \ "password").asOpt[String]

    (emailOpt, passwordOpt) match {
      case (Some(email), Some(password)) =>
        authService.register(email.trim.toLowerCase, password).map {
          case Right(sessionId) =>
            Created(Json.obj("message" -> "登録完了"))
              .withCookies(sessionCookie(sessionId))
          case Left(EmailAlreadyExists) =>
            Conflict(Json.obj("error" -> "このメールアドレスは既に登録されています"))
          case Left(UnexpectedError(msg)) =>
            BadRequest(Json.obj("error" -> msg))
          case Left(_) =>
            InternalServerError(Json.obj("error" -> "予期しないエラー"))
        }
      case _ =>
        Future.successful(BadRequest(Json.obj("error" -> "email と password は必須です")))
    }
  }

  def login(): Action[JsValue] = Action.async(parse.json) { request =>
    val emailOpt    = (request.body \ "email").asOpt[String]
    val passwordOpt = (request.body \ "password").asOpt[String]

    (emailOpt, passwordOpt) match {
      case (Some(email), Some(password)) =>
        authService.login(email.trim.toLowerCase, password).map {
          case Right(sessionId) =>
            Ok(Json.obj("message" -> "ログイン成功"))
              .withCookies(sessionCookie(sessionId))
          case Left(InvalidCredentials) =>
            Unauthorized(Json.obj("error" -> "メールアドレスまたはパスワードが正しくありません"))
          case Left(AccountLocked) =>
            TooManyRequests(Json.obj("error" -> "ログイン試行回数超過。5分後に再試行してください"))
          case Left(_) =>
            InternalServerError(Json.obj("error" -> "予期しないエラー"))
        }
      case _ =>
        Future.successful(BadRequest(Json.obj("error" -> "email と password は必須です")))
    }
  }

  def logout(): Action[AnyContent] = Action.async { request =>
    request.cookies.get(SessionCookieName) match {
      case None =>
        Future.successful(Ok(Json.obj("message" -> "ログアウト済み")))
      case Some(cookie) =>
        authService.logout(cookie.value).map { _ =>
          Ok(Json.obj("message" -> "ログアウト完了"))
            .discardingCookies(DiscardingCookie(SessionCookieName))
        }
    }
  }

  def me(): Action[AnyContent] = Action.async { request =>
    request.cookies.get(SessionCookieName) match {
      case None =>
        Future.successful(Unauthorized(Json.obj("error" -> "未ログイン")))
      case Some(cookie) =>
        authService.validateSession(cookie.value).map {
          case None         => Unauthorized(Json.obj("error" -> "セッション切れ"))
          case Some(userId) => Ok(Json.obj("userId" -> userId))
        }
    }
  }
}
