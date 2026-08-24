package controllers

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.scalamock.scalatest.MockFactory
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Cookie
import play.api.test.Helpers._
import play.api.test._
import services._

import scala.concurrent.{ExecutionContext, Future}

class AuthControllerSpec extends PlaySpec with MockFactory {

  implicit val system: ActorSystem  = ActorSystem("AuthControllerSpec")
  implicit val mat:    Materializer = Materializer(system)
  implicit val ec:     ExecutionContext = ExecutionContext.global

  private def controllerWith(authService: AuthService): AuthController =
    new AuthController(stubControllerComponents(), authService)

  // Action[JsValue] を直接 apply する場合、withBody で型を JsValue に揃えることで
  // parse.json の再実行（Content-Typeヘッダー等の余計な考慮）を避けられる
  private def jsonRequest(method: String, uri: String, body: JsValue): FakeRequest[JsValue] =
    FakeRequest(method, uri).withBody(body)

  "AuthController.register" should {

    "return BadRequest when email or password is missing" in {
      val authService = mock[AuthService]
      val controller  = controllerWith(authService)

      val request = jsonRequest(POST, "/api/auth/register", Json.obj("email" -> "a@example.com"))
      val result  = controller.register().apply(request)

      status(result) mustBe BAD_REQUEST
    }

    "return Created and set the session cookie on success" in {
      val authService = mock[AuthService]
      val controller  = controllerWith(authService)

      (authService.register _).expects("new@example.com", "password1")
        .returning(Future.successful(Right("session-abc")))

      val request = jsonRequest(POST, "/api/auth/register", Json.obj("email" -> "new@example.com", "password" -> "password1"))
      val result = controller.register().apply(request)

      status(result) mustBe CREATED
      cookies(result).get("SESSION_ID").map(_.value) mustBe Some("session-abc")
    }

    "return Conflict when the email is already registered" in {
      val authService = mock[AuthService]
      val controller  = controllerWith(authService)

      (authService.register _).expects("taken@example.com", "password1")
        .returning(Future.successful(Left(EmailAlreadyExists)))

      val request = jsonRequest(POST, "/api/auth/register", Json.obj("email" -> "taken@example.com", "password" -> "password1"))
      val result = controller.register().apply(request)

      status(result) mustBe CONFLICT
    }
  }

  "AuthController.login" should {

    "return BadRequest when email or password is missing" in {
      val authService = mock[AuthService]
      val controller  = controllerWith(authService)

      val request = jsonRequest(POST, "/api/auth/login", Json.obj("email" -> "a@example.com"))
      val result  = controller.login().apply(request)

      status(result) mustBe BAD_REQUEST
    }

    "return Ok and set the session cookie on success" in {
      val authService = mock[AuthService]
      val controller  = controllerWith(authService)

      (authService.login _).expects("user@example.com", "password1")
        .returning(Future.successful(Right("session-xyz")))

      val request = jsonRequest(POST, "/api/auth/login", Json.obj("email" -> "user@example.com", "password" -> "password1"))
      val result = controller.login().apply(request)

      status(result) mustBe OK
      cookies(result).get("SESSION_ID").map(_.value) mustBe Some("session-xyz")
    }

    "return Unauthorized on invalid credentials" in {
      val authService = mock[AuthService]
      val controller  = controllerWith(authService)

      (authService.login _).expects("user@example.com", "wrong-password")
        .returning(Future.successful(Left(InvalidCredentials)))

      val request = jsonRequest(POST, "/api/auth/login", Json.obj("email" -> "user@example.com", "password" -> "wrong-password"))
      val result = controller.login().apply(request)

      status(result) mustBe UNAUTHORIZED
    }
  }

  "AuthController.logout" should {

    "return Ok without hitting the service when there is no session cookie" in {
      val authService = mock[AuthService]
      val controller  = controllerWith(authService)

      val result = controller.logout().apply(FakeRequest(POST, "/api/auth/logout"))

      status(result) mustBe OK
    }

    "delete the session and discard the cookie" in {
      val authService = mock[AuthService]
      val controller  = controllerWith(authService)

      (authService.logout _).expects("session-abc").returning(Future.successful(()))

      val request = FakeRequest(POST, "/api/auth/logout").withCookies(Cookie("SESSION_ID", "session-abc"))
      val result  = controller.logout().apply(request)

      status(result) mustBe OK
      cookies(result).get("SESSION_ID").map(_.value) mustBe Some("")
    }
  }

  "AuthController.me" should {

    "return Unauthorized when there is no session cookie" in {
      val authService = mock[AuthService]
      val controller  = controllerWith(authService)

      val result = controller.me().apply(FakeRequest(GET, "/api/auth/me"))

      status(result) mustBe UNAUTHORIZED
    }

    "return the userId for a valid session" in {
      val authService = mock[AuthService]
      val controller  = controllerWith(authService)

      (authService.validateSession _).expects("session-abc").returning(Future.successful(Some(10L)))

      val request = FakeRequest(GET, "/api/auth/me").withCookies(Cookie("SESSION_ID", "session-abc"))
      val result  = controller.me().apply(request)

      status(result) mustBe OK
      (contentAsJson(result) \ "userId").as[Long] mustBe 10L
    }
  }
}
