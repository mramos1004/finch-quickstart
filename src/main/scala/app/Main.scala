package app

import app.errors._
import app.routes.greetingAPI
import app.util.{ config, rateLimiter, timeoutFilter }
import com.twitter.finagle.Http
import com.twitter.finagle.util.DefaultTimer
import com.twitter.util.{ Timer, Await }

/**
 * Greeting service server.
 */
object Main extends App { // Load port and start server
  val port = config.getInt("app.port")
  val _ = Await.ready(Http.serve(s":$port", Backend.api))
}

/**
 * Greeting service API.
 */
object Backend { // Init backend API
  implicit val timer: Timer = DefaultTimer.twitter
  val rateFilter = rateLimiter()
  val api = errorFilter andThen rateFilter andThen timeoutFilter() andThen greetingAPI
}