package services

import com.github.t3hnar.bcrypt._
import models.User
import org.scalamock.scalatest.MockFactory
import org.scalatestplus.play.PlaySpec
import repositories.{SessionRepository, UserRepository}

import java.time.OffsetDateTime
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class AuthServiceSpec extends PlaySpec with MockFactory {

  implicit val ec: ExecutionContext = ExecutionContext.global

  private def await[A](f: Future[A]): A = Await.result(f, 3.seconds)

  private def sampleUser(email: String, hashedPassword: String): User =
    User(1L, email, hashedPassword, OffsetDateTime.now())

  "AuthService.register" should {

    "reject a password shorter than 8 characters" in {
      val userRepo    = mock[UserRepository]
      val sessionRepo = mock[SessionRepository]
      val service     = new AuthService(userRepo, sessionRepo)

      await(service.register("new@example.com", "ab1")) mustBe a[Left[_, _]]
    }

    "reject a password without both a letter and a digit" in {
      val userRepo    = mock[UserRepository]
      val sessionRepo = mock[SessionRepository]
      val service     = new AuthService(userRepo, sessionRepo)

      await(service.register("new@example.com", "onlyletters")) mustBe a[Left[_, _]]
      await(service.register("new@example.com", "12345678")) mustBe a[Left[_, _]]
    }

    "return EmailAlreadyExists when the email is already registered" in {
      val userRepo    = mock[UserRepository]
      val sessionRepo = mock[SessionRepository]
      val service     = new AuthService(userRepo, sessionRepo)
      val existing    = sampleUser("taken@example.com", "hashed")

      (userRepo.findByEmail _).expects("taken@example.com").returning(Future.successful(Some(existing)))

      await(service.register("taken@example.com", "password1")) mustBe Left(EmailAlreadyExists)
    }

    "hash the password, create the user, and create a session on success" in {
      val userRepo    = mock[UserRepository]
      val sessionRepo = mock[SessionRepository]
      val service     = new AuthService(userRepo, sessionRepo)
      val created     = sampleUser("new@example.com", "hashed-value")

      (userRepo.findByEmail _).expects("new@example.com").returning(Future.successful(None))
      (userRepo.create _)
        .expects(where { (email: String, hash: String) =>
          email == "new@example.com" && "password1".isBcryptedSafeBounded(hash).getOrElse(false)
        })
        .returning(Future.successful(created))
      (sessionRepo.create _).expects(created.id).returning(Future.successful("session-abc"))

      await(service.register("new@example.com", "password1")) mustBe Right("session-abc")
    }
  }

  "AuthService.login" should {

    "return InvalidCredentials when the email is not registered" in {
      val userRepo    = mock[UserRepository]
      val sessionRepo = mock[SessionRepository]
      val service     = new AuthService(userRepo, sessionRepo)

      (userRepo.findByEmail _).expects("nobody@example.com").returning(Future.successful(None))

      await(service.login("nobody@example.com", "password1")) mustBe Left(InvalidCredentials)
    }

    "return InvalidCredentials when the password does not match" in {
      val userRepo     = mock[UserRepository]
      val sessionRepo  = mock[SessionRepository]
      val service      = new AuthService(userRepo, sessionRepo)
      val correctHash   = "password1".bcryptSafeBounded.get
      val existing      = sampleUser("user@example.com", correctHash)

      (userRepo.findByEmail _).expects("user@example.com").returning(Future.successful(Some(existing)))

      await(service.login("user@example.com", "wrong-password")) mustBe Left(InvalidCredentials)
    }

    "create a session when the password matches" in {
      val userRepo    = mock[UserRepository]
      val sessionRepo = mock[SessionRepository]
      val service     = new AuthService(userRepo, sessionRepo)
      val hash        = "password1".bcryptSafeBounded.get
      val existing    = sampleUser("user@example.com", hash)

      (userRepo.findByEmail _).expects("user@example.com").returning(Future.successful(Some(existing)))
      (sessionRepo.create _).expects(existing.id).returning(Future.successful("session-xyz"))

      await(service.login("user@example.com", "password1")) mustBe Right("session-xyz")
    }
  }

  "AuthService.logout" should {
    "delete the session" in {
      val userRepo    = mock[UserRepository]
      val sessionRepo = mock[SessionRepository]
      val service     = new AuthService(userRepo, sessionRepo)

      (sessionRepo.delete _).expects("session-abc").returning(Future.successful(1))

      await(service.logout("session-abc")) mustBe (())
    }
  }

  "AuthService.validateSession" should {

    "return the userId for a valid session" in {
      val userRepo    = mock[UserRepository]
      val sessionRepo = mock[SessionRepository]
      val service     = new AuthService(userRepo, sessionRepo)

      (sessionRepo.findValidSession _).expects("session-abc").returning(Future.successful(Some(1L)))

      await(service.validateSession("session-abc")) mustBe Some(1L)
    }

    "return None for an expired or unknown session" in {
      val userRepo    = mock[UserRepository]
      val sessionRepo = mock[SessionRepository]
      val service     = new AuthService(userRepo, sessionRepo)

      (sessionRepo.findValidSession _).expects("unknown").returning(Future.successful(None))

      await(service.validateSession("unknown")) mustBe None
    }
  }
}
