package app

import java.util.UUID
import scala.util.Random

/** The whole "server" in one place. Each `@cask.get` / `@cask.postForm`
  * method is one route, same idea as the App Router's page.tsx / route.ts
  * files, just collected in one file instead of spread across folders.
  *
  * Because this is a normal server-rendered app (no client-side React
  * state), each onboarding "step" is just a query-string parameter, and
  * moving to the next step is a normal form POST + redirect - no
  * `useState` or `fetch` needed.
  */
object Main extends cask.MainRoutes {

  // Bind to 0.0.0.0 and read PORT from the environment so this works
  // inside Docker / on Render, Fly.io, Railway, etc.
  override def host: String = "0.0.0.0"
  override def port: Int = sys.env.get("PORT").map(_.toInt).getOrElse(8080)

  Db.initSchema()

  // --- small cookie helpers -------------------------------------------------

  private def sessionIdOf(request: cask.Request): String =
    request.cookies.get("session_id").map(_.value).getOrElse(UUID.randomUUID().toString)

  private def variantOf(request: cask.Request): String =
    request.cookies.get("variant").map(_.value).getOrElse(if (Random.nextBoolean()) "short" else "long")

  /** Make sure the response carries a session_id and variant cookie,
    * creating them the first time we see this visitor.
    */
  private def ensureCookies(request: cask.Request): (String, String, Seq[cask.Cookie]) = {
    val hadSession = request.cookies.contains("session_id")
    val hadVariant = request.cookies.contains("variant")
    val sessionId = sessionIdOf(request)
    val variant = variantOf(request)

    val newCookies =
      (if (!hadSession) Seq(cask.Cookie("session_id", sessionId)) else Nil) ++
        (if (!hadVariant) Seq(cask.Cookie("variant", variant)) else Nil)

    (sessionId, variant, newCookies)
  }

  private def redirectTo(location: String, cookies: Seq[cask.Cookie] = Nil): cask.Response[String] =
    cask.Response("", statusCode = 303, headers = Seq("Location" -> location), cookies = cookies)

  // --- routes ----------------------------------------------------------------

  @cask.get("/")
  def home(): cask.Response[String] =
    cask.Response(Templates.page("Activation Lab", Templates.homeBody), headers = htmlHeaders)

  @cask.get("/onboarding")
  def onboarding(request: cask.Request, step: String = "signup"): cask.Response[String] = {
    val (_, variant, newCookies) = ensureCookies(request)
    cask.Response(
      Templates.page("Onboarding", Templates.onboardingBody(step, variant)),
      headers = htmlHeaders,
      cookies = newCookies
    )
  }

  @cask.postForm("/onboarding/signup")
  def signup(email: String, request: cask.Request): cask.Response[String] = {
    val (sessionId, _, newCookies) = ensureCookies(request)
    Db.insertEvent(sessionId, "signup_started", Map("email" -> email))
    Db.insertEvent(sessionId, "signup_completed", Map("email" -> email))
    redirectTo("/onboarding?step=verify", newCookies)
  }

  @cask.postForm("/onboarding/verify")
  def verify(request: cask.Request): cask.Response[String] = {
    val (sessionId, _, newCookies) = ensureCookies(request)
    Db.insertEvent(sessionId, "email_verified")
    redirectTo("/onboarding?step=profile", newCookies)
  }

  @cask.postForm("/onboarding/profile")
  def profile(name: String, request: cask.Request, company: String = "", role: String = ""): cask.Response[String] = {
    val (sessionId, variant, newCookies) = ensureCookies(request)
    val props = Map("name" -> name) ++
      (if (company.nonEmpty) Map("company" -> company) else Map.empty) ++
      (if (role.nonEmpty) Map("role" -> role) else Map.empty)
    Db.insertEvent(sessionId, "profile_completed", props, Some(variant))
    redirectTo("/onboarding?step=done", newCookies)
  }

  @cask.postForm("/onboarding/activate")
  def activate(request: cask.Request): cask.Response[String] = {
    val (sessionId, variant, newCookies) = ensureCookies(request)
    Db.insertEvent(sessionId, "activated", Map.empty, Some(variant))
    redirectTo("/onboarding?step=activated", newCookies)
  }

  @cask.get("/dashboard")
  def dashboard(): cask.Response[String] = {
    val funnel = Db.getFunnelCounts()
    val variants = Db.getVariantCounts()
    cask.Response(Templates.page("Dashboard", Templates.dashboardBody(funnel, variants)), headers = htmlHeaders)
  }

  private def htmlHeaders = Seq("Content-Type" -> "text/html; charset=utf-8")

  initialize()
}
