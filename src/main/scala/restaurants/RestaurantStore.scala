package restaurants

import java.util.regex.Pattern
import org.bson.conversions.Bson
import org.mongodb.scala.{Document, MongoCollection}
import org.mongodb.scala.model.{Accumulators => Acc, Aggregates => Agg, Filters => F, Indexes, Sorts, Updates}

case class NewRestaurant(
    id: String,
    name: String,
    cuisine: String,
    borough: String,
    building: String,
    street: String,
    zipcode: String
)

class RestaurantNotFound(id: String)
    extends Exception(s"No restaurant found with restaurant_id '$id'.")

/** Operations the command-line application needs from a restaurant store. */
trait RestaurantStore {
  def add(restaurant: NewRestaurant): Unit
  def search(filter: Bson, limit: Int = 20): Seq[Document]
  def updateField(id: String, field: String, value: String): Unit
  def delete(id: String): Unit
}

/** MongoDB-backed implementation. The collection stays private (encapsulation). */
class MongoRestaurantStore(private val collection: MongoCollection[Document]) extends RestaurantStore {
  import Database.await

  override def add(restaurant: NewRestaurant): Unit = {
    val doc = Document(
      "restaurant_id" -> restaurant.id,
      "name" -> restaurant.name,
      "cuisine" -> restaurant.cuisine,
      "borough" -> restaurant.borough,
      "address" -> Document(
        "building" -> restaurant.building,
        "street" -> restaurant.street,
        "zipcode" -> restaurant.zipcode
      ),
      "grades" -> Seq.empty[Document]
    )
    await(collection.insertOne(doc).toFuture())
  }

  override def search(filter: Bson, limit: Int = 20): Seq[Document] =
    await(collection.find(filter).limit(limit).toFuture()).toSeq

  def byName(term: String): Seq[Document] =
    search(F.regex("name", "(?i).*" + Pattern.quote(term) + ".*"))

  def byCuisineAndBorough(cuisine: String, borough: String): Seq[Document] =
    search(F.and(F.equal("cuisine", cuisine), F.equal("borough", borough)))

  def byZip(zipcode: String): Seq[Document] = search(F.equal("address.zipcode", zipcode))

  def byMinimumScore(score: Int): Seq[Document] =
    search(F.elemMatch("grades", F.gte("score", score)))

  override def updateField(id: String, field: String, value: String): Unit = {
    val update = field.toLowerCase match {
      case "name" | "cuisine" | "borough" => Updates.set(field.toLowerCase, value)
      case "zipcode" => Updates.set("address.zipcode", value)
      case other => throw new IllegalArgumentException(
        s"Cannot update '$other'. Choose name, cuisine, borough, or zipcode."
      )
    }
    val result = await(collection.updateOne(F.equal("restaurant_id", id), update).toFuture())
    if (result.getMatchedCount == 0) throw new RestaurantNotFound(id)
  }

  override def delete(id: String): Unit = {
    val result = await(collection.deleteOne(F.equal("restaurant_id", id)).toFuture())
    if (result.getDeletedCount == 0) throw new RestaurantNotFound(id)
  }

  /** Create four indexes; repeated calls are safe because names/keys are stable. */
  def ensureIndexes(): Unit = {
    await(collection.createIndex(Indexes.ascending("name")).toFuture())
    await(collection.createIndex(Indexes.ascending("address.zipcode")).toFuture())
    await(collection.createIndex(Indexes.ascending("restaurant_id")).toFuture())
    // Both fields are equality fields in the cuisine + borough search, so either order is valid under ESR.
    await(collection.createIndex(Indexes.compoundIndex(
      Indexes.ascending("borough"), Indexes.ascending("cuisine")
    )).toFuture())
  }

  def listIndexes(): Seq[Document] = await(collection.listIndexes().toFuture()).toSeq

  def dropIndex(name: String): Unit = {
    require(name.trim.nonEmpty, "Index name cannot be empty.")
    require(name.trim != "_id_", "MongoDB's required _id_ index cannot be dropped.")
    await(collection.dropIndex(name.trim).toFuture())
  }

  private def indexKey(field: String, direction: Int): Bson = direction match {
    case 1 => Indexes.ascending(field)
    case -1 => Indexes.descending(field)
    case other => throw new IllegalArgumentException(
      s"Index direction must be 1 (ascending) or -1 (descending), not $other."
    )
  }

  def createSingleFieldIndex(field: String, direction: Int): String = {
    require(field.trim.nonEmpty, "Field name cannot be empty.")
    await(collection.createIndex(indexKey(field.trim, direction)).toFuture())
  }

  def createCompoundIndex(
      firstField: String,
      firstDirection: Int,
      firstRole: String,
      secondField: String,
      secondDirection: Int,
      secondRole: String
  ): String = {
    val first = firstField.trim
    val second = secondField.trim
    require(first.nonEmpty && second.nonEmpty, "Both field names are required.")
    require(first != second, "Choose two different fields for a compound index.")
    val roleOrder = Map("E" -> 1, "S" -> 2, "R" -> 3)
    val firstRank = roleOrder.getOrElse(firstRole.toUpperCase,
      throw new IllegalArgumentException("Role must be E (equality), S (sort), or R (range)."))
    val secondRank = roleOrder.getOrElse(secondRole.toUpperCase,
      throw new IllegalArgumentException("Role must be E (equality), S (sort), or R (range)."))
    require(firstRank <= secondRank,
      "Compound index fields must be entered in ESR order: Equality, then Sort, then Range.")
    await(collection.createIndex(Indexes.compoundIndex(
      indexKey(first, firstDirection), indexKey(second, secondDirection)
    )).toFuture())
  }

  def restaurantCount: Long = await(collection.countDocuments().toFuture())

  private def aggregate(stages: Seq[Bson]): Seq[Document] =
    await(collection.aggregate(stages).toFuture()).toSeq

  def restaurantsByCuisine(): Seq[Document] = aggregate(Seq(
    Agg.group("$cuisine", Acc.sum("count", 1)),
    Agg.sort(Sorts.descending("count")),
    Agg.limit(10)
  ))

  def restaurantsByBorough(): Seq[Document] = aggregate(Seq(
    Agg.group("$borough", Acc.sum("count", 1)),
    Agg.sort(Sorts.descending("count"))
  ))

  def averageScoreByCuisine(): Seq[Document] = aggregate(Seq(
    Agg.unwind("$grades"),
    Agg.group("$cuisine", Acc.avg("averageScore", "$grades.score"), Acc.sum("gradeCount", 1)),
    Agg.`match`(F.gte("gradeCount", 100)),
    Agg.sort(Sorts.descending("averageScore")),
    Agg.limit(10)
  ))

  def gradeDistribution(): Seq[Document] = aggregate(Seq(
    Agg.unwind("$grades"),
    Agg.group("$grades.grade", Acc.sum("count", 1)),
    Agg.sort(Sorts.descending("count"))
  ))
}
