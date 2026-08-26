package repositories

import com.google.inject.ImplementedBy
import models.MealPlan
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile.api._

import javax.inject.{Inject, Singleton}
import java.time.{LocalDate, OffsetDateTime}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[MealPlanRepositoryImpl])
trait MealPlanRepository {
  def findByUserAndDateRange(userId: Long, from: LocalDate, to: LocalDate): Future[Seq[MealPlan]]
  def findById(id: Long): Future[Option[MealPlan]]
  def findByUserDateAndType(userId: Long, date: LocalDate, mealType: String): Future[Option[MealPlan]]
  def create(userId: Long, date: LocalDate, mealType: String): Future[MealPlan]
  def updateStatus(id: Long, status: String): Future[Int]
  def delete(id: Long): Future[Int]
}

@Singleton
class MealPlanRepositoryImpl @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
    extends MealPlanRepository {

  private val db = dbConfigProvider.get[slick.jdbc.JdbcProfile].db

  implicit val offsetDateTimeMapper: BaseColumnType[OffsetDateTime] =
    MappedColumnType.base[OffsetDateTime, java.sql.Timestamp](
      odt => java.sql.Timestamp.from(odt.toInstant),
      ts  => ts.toInstant.atOffset(java.time.ZoneOffset.UTC)
    )

  // DATE 列は java.sql.Date と LocalDate を相互変換する
  implicit val localDateMapper: BaseColumnType[LocalDate] =
    MappedColumnType.base[LocalDate, java.sql.Date](
      ld => java.sql.Date.valueOf(ld),
      d  => d.toLocalDate
    )

  private type MealPlanRow = (Long, Long, LocalDate, String, String, OffsetDateTime, OffsetDateTime)

  private class MealPlansTable(tag: Tag) extends Table[MealPlanRow](tag, "meal_plans") {
    def id        = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId    = column[Long]("user_id")
    def date      = column[LocalDate]("date")(localDateMapper)
    def mealType  = column[String]("meal_type")
    def status    = column[String]("status")
    def createdAt = column[OffsetDateTime]("created_at")(offsetDateTimeMapper)
    def updatedAt = column[OffsetDateTime]("updated_at")(offsetDateTimeMapper)
    def * = (id, userId, date, mealType, status, createdAt, updatedAt)
  }

  private val mealPlans = TableQuery[MealPlansTable]

  private def toModel(row: MealPlanRow): MealPlan =
    MealPlan(row._1, row._2, row._3, row._4, row._5, row._6, row._7)

  def findByUserAndDateRange(userId: Long, from: LocalDate, to: LocalDate): Future[Seq[MealPlan]] =
    db.run(
      mealPlans
        .filter(mp => mp.userId === userId && mp.date >= from && mp.date <= to)
        .sortBy(mp => (mp.date.asc, mp.mealType.asc))
        .result
    ).map(_.map(toModel))

  def findById(id: Long): Future[Option[MealPlan]] =
    db.run(mealPlans.filter(_.id === id).result.headOption).map(_.map(toModel))

  def findByUserDateAndType(userId: Long, date: LocalDate, mealType: String): Future[Option[MealPlan]] =
    db.run(
      mealPlans.filter(mp => mp.userId === userId && mp.date === date && mp.mealType === mealType)
        .result.headOption
    ).map(_.map(toModel))

  def create(userId: Long, date: LocalDate, mealType: String): Future[MealPlan] = {
    val now = OffsetDateTime.now()
    val action = (mealPlans.map(mp => (mp.userId, mp.date, mp.mealType, mp.status, mp.createdAt, mp.updatedAt))
      returning mealPlans.map(_.id)
      += (userId, date, mealType, "planned", now, now))
    db.run(action).map(id => MealPlan(id, userId, date, mealType, "planned", now, now))
  }

  def updateStatus(id: Long, status: String): Future[Int] = {
    val now = OffsetDateTime.now()
    db.run(mealPlans.filter(_.id === id).map(mp => (mp.status, mp.updatedAt)).update((status, now)))
  }

  def delete(id: Long): Future[Int] =
    db.run(mealPlans.filter(_.id === id).delete)
}
