package controllers

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import models.{MealPlan, Recipe}
import org.scalamock.scalatest.MockFactory
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Cookie
import play.api.test.Helpers._
import play.api.test._
import services._

import java.time.{LocalDate, OffsetDateTime}
import scala.concurrent.{ExecutionContext, Future}

class MealPlanControllerSpec extends PlaySpec with MockFactory {

  implicit val system: ActorSystem      = ActorSystem("MealPlanControllerSpec")
  implicit val mat:    Materializer     = Materializer(system)
  implicit val ec:     ExecutionContext = ExecutionContext.global

  private def sampleMealPlan(id: Long = 1L, userId: Long = 10L, status: String = "planned"): MealPlan = {
    val now = OffsetDateTime.now()
    MealPlan(id, userId, LocalDate.of(2026, 8, 31), "dinner", status, now, now)
  }

  private def sampleRecipe(id: Long = 1L, userId: Long = 10L): Recipe = {
    val now = OffsetDateTime.now()
    Recipe(id, userId, "肉じゃが", None, "和食", 2, Some(30), now, now)
  }

  private def controllerWith(authService: AuthService, mealPlanService: MealPlanService): MealPlanController =
    new MealPlanController(stubControllerComponents(), authService, mealPlanService)

  private def jsonRequest(method: String, uri: String, body: JsValue): FakeRequest[JsValue] =
    FakeRequest(method, uri).withBody(body)

  "MealPlanController.list" should {

    "return Unauthorized when there is no session cookie" in {
      val authService     = mock[AuthService]
      val mealPlanService = mock[MealPlanService]
      val controller      = controllerWith(authService, mealPlanService)

      val result = controller.list("2026-08-24", "2026-08-30")
        .apply(FakeRequest(GET, "/api/v1/meal-plans?from=2026-08-24&to=2026-08-30"))

      status(result) mustBe UNAUTHORIZED
    }

    "return BadRequest when the date format is invalid" in {
      val authService     = mock[AuthService]
      val mealPlanService = mock[MealPlanService]
      val controller      = controllerWith(authService, mealPlanService)

      (authService.validateSession _).expects("good-session").returning(Future.successful(Some(10L)))

      val request = FakeRequest(GET, "/api/v1/meal-plans?from=notadate&to=2026-08-30")
        .withCookies(Cookie("SESSION_ID", "good-session"))
      val result = controller.list("notadate", "2026-08-30").apply(request)

      status(result) mustBe BAD_REQUEST
    }

    "return the meal plans for the given range" in {
      val authService     = mock[AuthService]
      val mealPlanService = mock[MealPlanService]
      val controller      = controllerWith(authService, mealPlanService)
      val mealPlan        = sampleMealPlan()
      val recipe          = sampleRecipe()
      val from            = LocalDate.of(2026, 8, 24)
      val to              = LocalDate.of(2026, 8, 30)

      (authService.validateSession _).expects("good-session").returning(Future.successful(Some(10L)))
      (mealPlanService.listRange _).expects(10L, from, to)
        .returning(Future.successful(Seq(MealPlanWithRecipes(mealPlan, Seq(recipe)))))

      val request = FakeRequest(GET, "/api/v1/meal-plans?from=2026-08-24&to=2026-08-30")
        .withCookies(Cookie("SESSION_ID", "good-session"))
      val result = controller.list("2026-08-24", "2026-08-30").apply(request)

      status(result) mustBe OK
      (contentAsJson(result) \ 0 \ "mealType").as[String] mustBe "dinner"
    }
  }

  "MealPlanController.assign" should {

    "return BadRequest when mealType is invalid" in {
      val authService     = mock[AuthService]
      val mealPlanService = mock[MealPlanService]
      val controller      = controllerWith(authService, mealPlanService)

      (authService.validateSession _).expects("good-session").returning(Future.successful(Some(10L)))

      val body = Json.obj("date" -> "2026-08-31", "mealType" -> "brunch", "recipeIds" -> Json.arr(1))
      val request = jsonRequest(PUT, "/api/v1/meal-plans", body).withCookies(Cookie("SESSION_ID", "good-session"))
      val result  = controller.assign().apply(request)

      status(result) mustBe BAD_REQUEST
    }

    "assign recipes to the slot and return it" in {
      val authService     = mock[AuthService]
      val mealPlanService = mock[MealPlanService]
      val controller      = controllerWith(authService, mealPlanService)
      val mealPlan        = sampleMealPlan()
      val recipe          = sampleRecipe()

      (authService.validateSession _).expects("good-session").returning(Future.successful(Some(10L)))
      (mealPlanService.assign _).expects(10L, mealPlan.date, "dinner", Seq(1L))
        .returning(Future.successful(Right(MealPlanWithRecipes(mealPlan, Seq(recipe)))))

      val body = Json.obj("date" -> "2026-08-31", "mealType" -> "dinner", "recipeIds" -> Json.arr(1))
      val request = jsonRequest(PUT, "/api/v1/meal-plans", body).withCookies(Cookie("SESSION_ID", "good-session"))
      val result  = controller.assign().apply(request)

      status(result) mustBe OK
      (contentAsJson(result) \ "mealType").as[String] mustBe "dinner"
    }
  }

  "MealPlanController.updateStatus" should {

    "return BadRequest when status is invalid" in {
      val authService     = mock[AuthService]
      val mealPlanService = mock[MealPlanService]
      val controller      = controllerWith(authService, mealPlanService)

      (authService.validateSession _).expects("good-session").returning(Future.successful(Some(10L)))

      val body    = Json.obj("status" -> "unknown")
      val request = jsonRequest(PATCH, "/api/v1/meal-plans/1/status", body).withCookies(Cookie("SESSION_ID", "good-session"))
      val result  = controller.updateStatus(1L).apply(request)

      status(result) mustBe BAD_REQUEST
    }

    "return NotFound when the slot does not exist" in {
      val authService     = mock[AuthService]
      val mealPlanService = mock[MealPlanService]
      val controller      = controllerWith(authService, mealPlanService)

      (authService.validateSession _).expects("good-session").returning(Future.successful(Some(10L)))
      (mealPlanService.updateStatus _).expects(1L, 10L, "eaten").returning(Future.successful(Left(MealPlanNotFound)))

      val body    = Json.obj("status" -> "eaten")
      val request = jsonRequest(PATCH, "/api/v1/meal-plans/1/status", body).withCookies(Cookie("SESSION_ID", "good-session"))
      val result  = controller.updateStatus(1L).apply(request)

      status(result) mustBe NOT_FOUND
    }
  }

  "MealPlanController.delete" should {

    "return NoContent when the owner deletes the slot" in {
      val authService     = mock[AuthService]
      val mealPlanService = mock[MealPlanService]
      val controller      = controllerWith(authService, mealPlanService)

      (authService.validateSession _).expects("good-session").returning(Future.successful(Some(10L)))
      (mealPlanService.unassign _).expects(1L, 10L).returning(Future.successful(Right(())))

      val request = FakeRequest(DELETE, "/api/v1/meal-plans/1").withCookies(Cookie("SESSION_ID", "good-session"))
      val result  = controller.delete(1L).apply(request)

      status(result) mustBe NO_CONTENT
    }

    "return Forbidden when another user tries to delete it" in {
      val authService     = mock[AuthService]
      val mealPlanService = mock[MealPlanService]
      val controller      = controllerWith(authService, mealPlanService)

      (authService.validateSession _).expects("good-session").returning(Future.successful(Some(999L)))
      (mealPlanService.unassign _).expects(1L, 999L).returning(Future.successful(Left(MealPlanForbidden)))

      val request = FakeRequest(DELETE, "/api/v1/meal-plans/1").withCookies(Cookie("SESSION_ID", "good-session"))
      val result  = controller.delete(1L).apply(request)

      status(result) mustBe FORBIDDEN
    }
  }
}
