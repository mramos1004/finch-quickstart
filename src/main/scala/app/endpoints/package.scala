package app

import app.models.Greeting
import app.services._
import app.util.config
import io.circe.{ Json, Encoder }
import io.finch._
import io.finch.circe._

/**
 * Greeting service routes.
 */
package object endpoints {

  // Logging
  private val log = org.slf4j.LoggerFactory.getLogger(getClass)

  // Base path
  private val health = Json.fromString("ok")
  private val context = config.getString("app.context")
  private val path = context :: "api" :: "v1" :: "greeting"

  // Route for rendering multiple greetings
  private val multiGreetingEp = get(path :: string :: int :: paramOption("title")) {
    (name: String, count: Int, title: Option[String]) => greetings(name, count, title).map(Ok)
  }

  // Route for rendering a single greeting with a provided name
  private val greetingByNameEp: Endpoint[Greeting] = get(path :: string :: paramOption("title")) {
    (name: String, title: Option[String]) => greeting(name, title).map(Ok)
  }

  // Route for rendering a single, default greeting
  private val greetingEp: Endpoint[Greeting] = get(path)(greeting("World", None).map(Ok))

  // Route for system status
  private val statusEp = get(context)(Ok(health)) | head(context)(Ok(health))

  // Endpoints
  private val combined = multiGreetingEp :+: greetingEp :+: greetingByNameEp :+: statusEp

  // Convert domain errors to JSON
  implicit val encodeException: Encoder[Exception] = Encoder.instance { e =>
    Json.obj(
      "type" -> Json.fromString(e.getClass.getSimpleName),
      "error" -> Json.fromString(Option(e.getMessage).getOrElse("Internal Server Error"))
    )
  }

  /**
   * Greeting API
   */
  val greetingAPI = combined.handle {
    case e: IllegalArgumentException =>
      log.error("Bad request from client", e)
      BadRequest(e)
    case t: Throwable =>
      log.error("Unexpected exception", t)
      InternalServerError(new Exception(t.getCause))
  }.toServiceAs[Application.Json]
}
