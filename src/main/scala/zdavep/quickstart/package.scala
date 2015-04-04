package zdavep

import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finagle.httpx.Status
import com.twitter.util.Future
import io.finch.{Endpoint => _, _}
import io.finch.json._
import io.finch.request._
import io.finch.response._
import io.finch.route._

package object quickstart {

  // A HTTP response helper that adds some useful security headers:
  // https://www.owasp.org/index.php/List_of_useful_HTTP_headers
  private[this] def _respondWith(status: Status)(fn: ResponseBuilder => HttpResponse) = fn(
    ResponseBuilder(status).withHeaders("X-Content-Type-Options" -> "nosniff",
      "Strict-Transport-Security" -> "max-age=631138519; includeSubDomains",
      "X-Frame-Options" -> "deny", "X-XSS-Protection" -> "1; mode=block",
      "Cache-Control" -> "max-age=0, no-cache, no-store"))

  // A HTTP response helper that adds some useful security headers:
  // https://www.owasp.org/index.php/List_of_useful_HTTP_headers
  private[this] def respondWith(status: Status)(fn: ResponseBuilder => HttpResponse) =
    _respondWith(status)(fn).toFuture

  // Greeting reader
  private[this] def readGreeting(name: String) = new Service[HttpRequest, String] {
    def buildGreeting(maybeTitle: Option[String]): String = maybeTitle match {
      case Some(title) => s"Hello, $title $name"
      case None => s"Hello, $name"
    }
    def apply(req: HttpRequest): Future[String] = (paramOption("title") ~> buildGreeting)(req)
  }

  // Greeting responder
  private[this] val greetingResponder = new Service[String, HttpResponse] {
    def apply(greeting: String): Future[HttpResponse] = respondWith(Status.Ok) { response =>
      response(Json.obj("status" -> "success", "greeting" -> greeting))
    }
  }

  // Service combinator
  private[this] def greetingService(name: String) = readGreeting(name) ! greetingResponder

  // Greeting endpoint that takes a name parameter
  private[this] def greetingEp1(version: String) =
    Get / "quickstart" / "api" / `version` / "greeting" / string /> greetingService _

  // Greeting endpoint that uses a default name parameter
  private[this] def greetingEp2(version: String) =
    Get / "quickstart" / "api" / `version` / "greeting" /> greetingService("World")

  // Public greeting endpoint combinator
  def quickstartEndpoints(version: String): Endpoint[HttpRequest, HttpResponse] =
    greetingEp1(version) | greetingEp2(version)

  // Handle errors when a requested route was not found.
  private[this] val handleRouteNotFound: PartialFunction[Throwable, HttpResponse] = {
    case e: RouteNotFound => _respondWith(Status.NotFound) { response =>
      response(Json.obj("error" -> Option(e.getMessage).getOrElse("Route Not Found")))
    }
  }

  // Combine all exception handlers.
  private[this]
  val allExceptions = handleRouteNotFound orElse PartialFunction[Throwable, HttpResponse] {
    case t: Throwable => _respondWith(Status.InternalServerError) { response =>
      response(Json.obj("error" -> Option(t.getMessage).getOrElse("Internal Server Error")))
    }
  }

  /**
   * A Finagle filter that converts exceptions to http responses.
   */
  val handleExceptions = new SimpleFilter[HttpRequest, HttpResponse] {
    def apply(req: HttpRequest, service: Service[HttpRequest, HttpResponse]): Future[HttpResponse] =
     service(req).handle(allExceptions)
  }

}
