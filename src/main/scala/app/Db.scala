package app

import java.sql.{Connection, DriverManager}
import upickle.default._

/** All database access lives here.
  *
  * We use plain JDBC (no fancy library) so it's easy to see exactly what's
  * happening: open a connection, run a query, read the results, close the
  * connection. This mirrors what `lib/db.ts` + the `postgres` package did
  * in the original Next.js app, just written out by hand.
  */
object Db {

  // Explicitly register the driver. The JAR normally does this itself via
  // META-INF/services when it's on a plain classpath, but that mechanism is
  // easy to accidentally strip when building a merged "fat" jar - calling
  // this directly makes sure it works either way.
  Class.forName("org.postgresql.Driver")

  // Most hosts (Render, Heroku, Neon, Supabase) give you a connection string
  // shaped like postgresql://user:pass@host:port/dbname - that's a normal
  // Postgres URI, but JDBC needs jdbc:postgresql://host:port/dbname plus the
  // user/password as query params instead of in the URI's user-info. We
  // detect and convert automatically, whether or not a jdbc: prefix is
  // already there, so you can paste any style of connection string in.
  private def toJdbcUrl(raw: String): String = {
    val withoutPrefix = raw.stripPrefix("jdbc:")
    val uri = new java.net.URI(withoutPrefix)
    if (uri.getUserInfo == null) {
      if (raw.startsWith("jdbc:")) raw else s"jdbc:$raw"
    } else {
      val Array(user, password) = uri.getUserInfo.split(":", 2)
      val port = if (uri.getPort > 0) uri.getPort else 5432
      val dbName = uri.getPath.stripPrefix("/")
      s"jdbc:postgresql://${uri.getHost}:$port/$dbName?user=$user&password=$password&sslmode=prefer"
    }
  }

  private def connect(): Connection = {
    val raw = sys.env.getOrElse(
      "POSTGRES_URL",
      throw new RuntimeException("POSTGRES_URL environment variable is not set")
    )
    DriverManager.getConnection(toJdbcUrl(raw))
  }

  /** Create the events table if it doesn't already exist. Call this once on startup. */
  def initSchema(): Unit = {
    val conn = connect()
    try {
      val stmt = conn.createStatement()
      stmt.execute(
        """CREATE TABLE IF NOT EXISTS events (
          |  id SERIAL PRIMARY KEY,
          |  session_id TEXT NOT NULL,
          |  event_name TEXT NOT NULL,
          |  properties JSONB,
          |  variant TEXT,
          |  created_at TIMESTAMP DEFAULT now()
          |)""".stripMargin
      )
      stmt.close()
    } finally conn.close()
  }

  /** Record one analytics event. Equivalent to the INSERT in route.ts. */
  def insertEvent(
      sessionId: String,
      eventName: String,
      properties: Map[String, String] = Map.empty,
      variant: Option[String] = None
  ): Unit = {
    val conn = connect()
    try {
      val stmt = conn.prepareStatement(
        "INSERT INTO events (session_id, event_name, properties, variant) VALUES (?, ?, ?::jsonb, ?)"
      )
      stmt.setString(1, sessionId)
      stmt.setString(2, eventName)
      stmt.setString(3, write(properties)) // upickle turns the Map into a JSON string
      variant match {
        case Some(v) => stmt.setString(4, v)
        case None    => stmt.setNull(4, java.sql.Types.VARCHAR)
      }
      stmt.executeUpdate()
      stmt.close()
    } finally conn.close()
  }

  val funnelSteps: List[String] =
    List("signup_started", "signup_completed", "email_verified", "profile_completed", "activated")

  /** How many distinct sessions reached each funnel step. Equivalent to getFunnelData(). */
  def getFunnelCounts(): Map[String, Int] = {
    val conn = connect()
    try {
      val stmt = conn.prepareStatement(
        "SELECT event_name, COUNT(DISTINCT session_id) AS users " +
          "FROM events WHERE event_name = ANY(?) GROUP BY event_name"
      )
      val sqlArray = conn.createArrayOf("text", funnelSteps.toArray)
      stmt.setArray(1, sqlArray)

      val rs = stmt.executeQuery()
      var counts = Map.empty[String, Int]
      while (rs.next()) {
        counts += (rs.getString("event_name") -> rs.getInt("users"))
      }
      rs.close()
      stmt.close()

      // fill in zero for any step nobody has reached yet, and keep funnel order
      funnelSteps.map(step => step -> counts.getOrElse(step, 0)).toMap
    } finally conn.close()
  }

  /** For each profile-form variant: how many completed the profile, how many activated.
    * Equivalent to getVariantData().
    */
  def getVariantCounts(): Map[String, (Int, Int)] = {
    val conn = connect()
    try {
      val stmt = conn.prepareStatement(
        "SELECT variant, event_name, COUNT(DISTINCT session_id) AS users " +
          "FROM events WHERE variant IS NOT NULL AND event_name IN ('profile_completed', 'activated') " +
          "GROUP BY variant, event_name"
      )
      val rs = stmt.executeQuery()

      var result = Map("short" -> (0, 0), "long" -> (0, 0))
      while (rs.next()) {
        val variant = rs.getString("variant")
        val eventName = rs.getString("event_name")
        val users = rs.getInt("users")
        if (variant == "short" || variant == "long") {
          val (completed, activated) = result(variant)
          if (eventName == "profile_completed") result += (variant -> (users, activated))
          if (eventName == "activated") result += (variant -> (completed, users))
        }
      }
      rs.close()
      stmt.close()
      result
    } finally conn.close()
  }
}
