package controllers

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import models.{Ingredient, Recipe}
import org.scalamock.scalatest.MockFactory
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Cookie
import play.api.test.Helpers._
import play.api.test._
import services._

import java.time.OffsetDateTime
import scala.concurrent.{ExecutionContext, Future}

class RecipeControllerSpec extends PlaySpec with MockFactory {

  implicit val system: ActorSystem  = ActorSystem("RecipeControllerSpec")
  implicit val mat:    Materializer = Materializer(system)
  implicit val ec:     ExecutionContext = ExecutionContext.global

  private def sampleRecipe(id: Long = 1L, userId: Long = 10L): Recipe = {
    val now = OffsetDateTime.now()
    Recipe(id, userId, "肉じゃが", None, "和食", 2, Some(30), now, now)
  }

  private def controllerWith(authService: AuthService, recipeService: RecipeService): RecipeController =
    new RecipeController(stubControllerComponents(), authService, recipeService)

  // Action[JsValue] を直接 apply する場合、withBody で型を JsValue に揃えることで
  // parse.json の再実行（Content-Typeヘッダー等の余計な考慮）を避けられる
  private def jsonRequest(method: String, uri: String, body: JsValue): FakeRequest[JsValue] =
    FakeRequest(method, uri).withBody(body)

  "RecipeController.list" should {

    "return Unauthorized when there is no session cookie" in {
      val authService   = mock[AuthService]
      val recipeService = mock[RecipeService]
      val controller    = controllerWith(authService, recipeService)

      val result = controller.list().apply(FakeRequest(GET, "/api/recipes"))

      status(result) mustBe UNAUTHORIZED
    }

    "return Unauthorized when the session is invalid" in {
      val authService   = mock[AuthService]
      val recipeService = mock[RecipeService]
      val controller    = controllerWith(authService, recipeService)

      (authService.validateSession _).expects("bad-session").returning(Future.successful(None))

      val request = FakeRequest(GET, "/api/recipes").withCookies(Cookie("SESSION_ID", "bad-session"))
      val result  = controller.list().apply(request)

      status(result) mustBe UNAUTHORIZED
    }

    "return the recipes for the authenticated user" in {
      val authService   = mock[AuthService]
      val recipeService = mock[RecipeService]
      val controller    = controllerWith(authService, recipeService)
      val recipe        = sampleRecipe()

      (authService.validateSession _).expects("good-session").returning(Future.successful(Some(recipe.userId)))
      (recipeService.list _).expects(recipe.userId)
        .returning(Future.successful(Seq(RecipeWithIngredients(recipe, Seq.empty))))

      val request = FakeRequest(GET, "/api/recipes").withCookies(Cookie("SESSION_ID", "good-session"))
      val result  = controller.list().apply(request)

      status(result) mustBe OK
      (contentAsJson(result) \ 0 \ "title").as[String] mustBe "肉じゃが"
    }
  }

  "RecipeController.create" should {

    "return BadRequest when title is missing" in {
      val authService   = mock[AuthService]
      val recipeService = mock[RecipeService]
      val controller    = controllerWith(authService, recipeService)

      (authService.validateSession _).expects("good-session").returning(Future.successful(Some(10L)))

      val request = jsonRequest(POST, "/api/recipes", Json.obj("description" -> "説明のみ"))
        .withCookies(Cookie("SESSION_ID", "good-session"))
      val result = controller.create().apply(request)

      status(result) mustBe BAD_REQUEST
    }

    "create the recipe and return Created" in {
      val authService   = mock[AuthService]
      val recipeService = mock[RecipeService]
      val controller    = controllerWith(authService, recipeService)
      val recipe        = sampleRecipe()
      val ingredient    = Ingredient(1L, recipe.id, "じゃがいも", Some(BigDecimal(200)), Some("g"), 0)

      (authService.validateSession _).expects("good-session").returning(Future.successful(Some(recipe.userId)))
      (recipeService.create _)
        .expects(recipe.userId, "肉じゃが", None, "和食", 2, Some(30), Seq(models.IngredientInput("じゃがいも", Some(BigDecimal(200)), Some("g"))))
        .returning(Future.successful(RecipeWithIngredients(recipe, Seq(ingredient))))

      val body = Json.obj(
        "title"           -> "肉じゃが",
        "category"        -> "和食",
        "servings"        -> 2,
        "cookTimeMinutes" -> 30,
        "ingredients"     -> Json.arr(Json.obj("name" -> "じゃがいも", "amount" -> 200, "unit" -> "g"))
      )
      val request = jsonRequest(POST, "/api/recipes", body)
        .withCookies(Cookie("SESSION_ID", "good-session"))
      val result = controller.create().apply(request)

      status(result) mustBe CREATED
      (contentAsJson(result) \ "title").as[String] mustBe "肉じゃが"
    }
  }

  "RecipeController.show" should {

    "return NotFound when the recipe does not exist" in {
      val authService   = mock[AuthService]
      val recipeService = mock[RecipeService]
      val controller    = controllerWith(authService, recipeService)

      (authService.validateSession _).expects("good-session").returning(Future.successful(Some(10L)))
      (recipeService.get _).expects(1L, 10L).returning(Future.successful(Left(RecipeNotFound)))

      val request = FakeRequest(GET, "/api/recipes/1").withCookies(Cookie("SESSION_ID", "good-session"))
      val result  = controller.show(1L).apply(request)

      status(result) mustBe NOT_FOUND
    }

    "return Forbidden when another user's recipe is requested" in {
      val authService   = mock[AuthService]
      val recipeService = mock[RecipeService]
      val controller    = controllerWith(authService, recipeService)

      (authService.validateSession _).expects("good-session").returning(Future.successful(Some(999L)))
      (recipeService.get _).expects(1L, 999L).returning(Future.successful(Left(RecipeForbidden)))

      val request = FakeRequest(GET, "/api/recipes/1").withCookies(Cookie("SESSION_ID", "good-session"))
      val result  = controller.show(1L).apply(request)

      status(result) mustBe FORBIDDEN
    }

    "return the recipe when the owner requests it" in {
      val authService   = mock[AuthService]
      val recipeService = mock[RecipeService]
      val controller    = controllerWith(authService, recipeService)
      val recipe        = sampleRecipe()

      (authService.validateSession _).expects("good-session").returning(Future.successful(Some(recipe.userId)))
      (recipeService.get _).expects(recipe.id, recipe.userId)
        .returning(Future.successful(Right(RecipeWithIngredients(recipe, Seq.empty))))

      val request = FakeRequest(GET, s"/api/recipes/${recipe.id}").withCookies(Cookie("SESSION_ID", "good-session"))
      val result  = controller.show(recipe.id).apply(request)

      status(result) mustBe OK
      (contentAsJson(result) \ "id").as[Long] mustBe recipe.id
    }
  }

  "RecipeController.update" should {

    "return NotFound when updating a recipe that does not exist" in {
      val authService   = mock[AuthService]
      val recipeService = mock[RecipeService]
      val controller    = controllerWith(authService, recipeService)

      (authService.validateSession _).expects("good-session").returning(Future.successful(Some(10L)))
      (recipeService.update _)
        .expects(1L, 10L, "新しい名前", None, "その他", 2, None, Seq.empty)
        .returning(Future.successful(Left(RecipeNotFound)))

      val request = jsonRequest(PUT, "/api/recipes/1", Json.obj("title" -> "新しい名前"))
        .withCookies(Cookie("SESSION_ID", "good-session"))
      val result = controller.update(1L).apply(request)

      status(result) mustBe NOT_FOUND
    }
  }

  "RecipeController.delete" should {

    "return NoContent when the owner deletes the recipe" in {
      val authService   = mock[AuthService]
      val recipeService = mock[RecipeService]
      val controller    = controllerWith(authService, recipeService)

      (authService.validateSession _).expects("good-session").returning(Future.successful(Some(10L)))
      (recipeService.delete _).expects(1L, 10L).returning(Future.successful(Right(())))

      val request = FakeRequest(DELETE, "/api/recipes/1").withCookies(Cookie("SESSION_ID", "good-session"))
      val result  = controller.delete(1L).apply(request)

      status(result) mustBe NO_CONTENT
    }

    "return Forbidden when another user tries to delete it" in {
      val authService   = mock[AuthService]
      val recipeService = mock[RecipeService]
      val controller    = controllerWith(authService, recipeService)

      (authService.validateSession _).expects("good-session").returning(Future.successful(Some(999L)))
      (recipeService.delete _).expects(1L, 999L).returning(Future.successful(Left(RecipeForbidden)))

      val request = FakeRequest(DELETE, "/api/recipes/1").withCookies(Cookie("SESSION_ID", "good-session"))
      val result  = controller.delete(1L).apply(request)

      status(result) mustBe FORBIDDEN
    }
  }
}
