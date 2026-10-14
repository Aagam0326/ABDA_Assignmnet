package restaurants

import org.mongodb.scala.{Document, MongoClient, MongoCollection}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

/** Owns the MongoDB client and gives the application one shared collection. */
object Database {
  private val uri = sys.env.getOrElse("MONGO_URI", "mongodb://localhost:27017")
  private val client: MongoClient = MongoClient(uri)

  val restaurants: MongoCollection[Document] =
    client.getDatabase("sample_restaurants").getCollection("restaurants")

  /** MongoDB Scala driver operations are asynchronous; the CLI waits for results. */
  def await[A](future: Future[A]): A = Await.result(future, Duration.Inf)

  def close(): Unit = client.close()
}
