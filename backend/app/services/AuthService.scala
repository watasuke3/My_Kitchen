package services

import com.github.t3hnar.bcrypt._
import repositories.{SessionRepository, UserRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

sealed trait AuthError
case object EmailAlreadyExists  extends AuthError
case object InvalidCredentials  extends AuthError
case class  UnexpectedError(msg: String) extends AuthError

@Singleton
class AuthService @Inject()(
  userRepo: UserRepository,
  sessionRepo: SessionRepository
)(implicit ec: ExecutionContext) {

  def register(email: String, password: String): Future[Either[AuthError, String]] = {
    if (password.length < 8 || !password.exists(_.isLetter) || !password.exists(_.isDigit))
      return Future.successful(Left(UnexpectedError("パスワードは8文字以上、英字・数字を含む必要があります")))

    userRepo.findByEmail(email).flatMap {
      case Some(_) => Future.successful(Left(EmailAlreadyExists))
      case None =>
        password.bcryptSafeBounded match {
          case Failure(e) => Future.successful(Left(UnexpectedError(e.getMessage)))
          case Success(hash) =>
            userRepo.create(email, hash).flatMap { user =>
              sessionRepo.create(user.id).map(Right(_))
            }
        }
    }
  }

  def login(email: String, password: String): Future[Either[AuthError, String]] = {
    userRepo.findByEmail(email).flatMap {
      case None => Future.successful(Left(InvalidCredentials))
      case Some(user) =>
        password.isBcryptedSafeBounded(user.password) match {
          case Failure(_)     => Future.successful(Left(InvalidCredentials))
          case Success(false) => Future.successful(Left(InvalidCredentials))
          case Success(true)  => sessionRepo.create(user.id).map(Right(_))
        }
    }
  }

  def logout(sessionId: String): Future[Unit] =
    sessionRepo.delete(sessionId).map(_ => ())

  def validateSession(sessionId: String): Future[Option[Long]] =
    sessionRepo.findValidSession(sessionId)
}
