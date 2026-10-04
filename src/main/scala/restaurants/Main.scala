package restaurants

import org.bson.BsonValue
import org.mongodb.scala.Document
import scala.io.StdIn.readLine
import scala.util.{Failure, Try}

object Main {
  private val store = new MongoRestaurantStore(Database.restaurants)
  private var running = true

  private def ask(prompt: String): String =
    Option(readLine(s"$prompt: ")).map(_.trim).getOrElse("")

  // The sample dataset can omit fields; access each value through Option.
  private def field(document: Document, key: String): String =
    document.get(key).map(render).getOrElse("N/A")

  private def render(value: BsonValue): String =
    if (value.isString) value.asString().getValue
    else if (value.isNumber) value.asNumber().toString
    else if (value.isDocument) value.asDocument().toJson
    else value.toString

  private def show(documents: Seq[Document]): Unit = {
    if (documents.isEmpty) println("No results.")
    else documents.foreach { document =>
      val address = document.get("address").filter(_.isDocument)
      val zipcode = address.flatMap(a => Option(a.asDocument().get("zipcode")))
        .map(render).getOrElse("N/A")
      println(s"${field(document, "restaurant_id")} | ${field(document, "name")} | " +
        s"${field(document, "cuisine")} | ${field(document, "borough")} | $zipcode")
    }
  }

  private def printRows(rows: Seq[Document]): Unit =
    if (rows.isEmpty) println("No results.") else rows.foreach(row => println(row.toJson()))

  private val menu =
    """|===== Restaurant Discovery & Analytics =====
       |1. Add Restaurant
       |2. Search/View Restaurants
       |3. Update Restaurant
       |4. Delete Restaurant
       |5. Restaurant Analytics
       |6. Index Information
       |7. Exit""".stripMargin

  def main(args: Array[String]): Unit = {
    try {
      store.ensureIndexes()
      val count = store.restaurantCount
      println(s"Connected to sample_restaurants.restaurants ($count documents).")
      if (count == 0) println("The collection is empty. Load the sample_restaurants dataset and check MONGO_URI.")
      while (running) {
        println(menu)
        Try(ask("Choose an option") match {
          case "1" => addRestaurant()
          case "2" => searchRestaurants()
          case "3" => updateRestaurant()
          case "4" => store.delete(ask("restaurant_id")); println("Restaurant deleted.")
          case "5" => analytics()
          case "6" => indexInformation()
          case "7" => running = false; println("Goodbye.")
          case other => println(s"Invalid option: $other")
        }) match {
          case Failure(error) => println(s"Error: ${error.getMessage}")
          case _ => ()
        }
      }
    } finally Database.close()
  }

  private def addRestaurant(): Unit = {
    val restaurant = NewRestaurant(
      ask("restaurant_id"), ask("Name"), ask("Cuisine"), ask("Borough"),
      ask("Address building"), ask("Address street"), ask("Address zipcode")
    )
    store.add(restaurant)
    println(s"Added '${restaurant.name}'.")
  }

  private def searchRestaurants(): Unit = {
    println("a) Name  b) Cuisine + borough  c) ZIP code  d) Minimum grade score")
    ask("Search type") match {
      case "a" => show(store.byName(ask("Name contains")))
      case "b" => show(store.byCuisineAndBorough(ask("Cuisine"), ask("Borough")))
      case "c" => show(store.byZip(ask("ZIP code")))
      case "d" => show(store.byMinimumScore(ask("Minimum score").toInt))
      case _ => println("Invalid search type.")
    }
  }

  private def updateRestaurant(): Unit = {
    store.updateField(ask("restaurant_id"), ask("Field (name/cuisine/borough/zipcode)"), ask("New value"))
    println("Restaurant updated.")
  }

  private def analytics(): Unit = {
    println("a) Restaurants by cuisine  b) Restaurants by borough")
    println("c) Average score by cuisine  d) Grade distribution")
    val rows = ask("Analytics type") match {
      case "a" => store.restaurantsByCuisine()
      case "b" => store.restaurantsByBorough()
      case "c" => store.averageScoreByCuisine()
      case "d" => store.gradeDistribution()
      case _ => println("Invalid analytics type."); Seq.empty
    }
    printRows(rows)
  }

  private def indexInformation(): Unit = {
    println("a) List indexes  b) Create normal index  c) Create compound index (ESR)  d) Drop an index")
    ask("Index option") match {
      case "a" =>
        println("Indexes:")
        store.listIndexes().foreach(index => println(index.toJson()))
      case "b" =>
        val fieldName = ask("Field name (example: address.zipcode)")
        val direction = askIndexDirection("Index direction")
        val name = store.createSingleFieldIndex(fieldName, direction)
        println(s"Created index '$name'.")
      case "c" =>
        println("Use ESR order: Equality fields first, then Sort fields, then Range fields.")
        val first = ask("First field (first key in compound index)")
        val firstRole = askIndexRole(s"Role for '$first'")
        val firstDirection = askIndexDirection(s"Direction for '$first'")
        val second = ask("Second field (second key in compound index)")
        val secondRole = askIndexRole(s"Role for '$second'")
        val secondDirection = askIndexDirection(s"Direction for '$second'")
        val name = store.createCompoundIndex(
          first, firstDirection, firstRole, second, secondDirection, secondRole
        )
        println(s"Created compound index '$name'.")
      case "d" => dropIndex()
      case _ => println("Invalid index option.")
    }
  }

  private def dropIndex(): Unit = {
    println("Current indexes:")
    store.listIndexes().foreach(index => println(index.toJson()))
    val name = ask("Exact index name to drop (the _id_ index is protected)")
    store.dropIndex(name)
    println(s"Dropped index '$name'.")
  }

  private def askIndexDirection(label: String): Int =
    ask(s"$label (1 = ascending, 2 = descending)") match {
      case "1" => 1
      case "2" => -1
      case other => throw new IllegalArgumentException(s"Choose 1 or 2, not '$other'.")
    }

  private def askIndexRole(label: String): String =
    ask(s"$label (E = equality, S = sort, R = range)").toUpperCase match {
      case role @ ("E" | "S" | "R") => role
      case other => throw new IllegalArgumentException(s"Choose E, S, or R, not '$other'.")
    }

}
