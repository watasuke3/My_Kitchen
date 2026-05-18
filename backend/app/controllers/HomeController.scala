package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._

@Singleton
class HomeController @Inject()(val controllerComponents: ControllerComponents) extends BaseController {

  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }

  def health() = Action {
    Ok(Json.obj("status" -> "ok"))
  }
}
