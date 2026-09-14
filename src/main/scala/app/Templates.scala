package app

/** Very simple HTML "templates". No template engine - just methods that
  * return strings. Good enough to learn from; swap in scalatags later if
  * you want something more type-safe.
  */
object Templates {

  def page(title: String, body: String): String =
    s"""<!doctype html>
       |<html lang="en">
       |<head>
       |  <meta charset="utf-8">
       |  <title>$title</title>
       |  <style>
       |    body { font-family: sans-serif; padding: 2rem; color: #222; }
       |    a.button, button {
       |      display: inline-block; padding: 0.75rem 1.5rem; background: #3b82f6;
       |      color: white; border: none; border-radius: 6px; text-decoration: none;
       |      cursor: pointer; font-size: 1rem;
       |    }
       |    a.secondary { border: 1px solid #ccc; color: #333; background: none; }
       |    input { padding: 0.5rem; width: 100%; margin-bottom: 1rem; box-sizing: border-box; }
       |    table { width: 100%; border-collapse: collapse; margin-top: 1rem; }
       |    th, td { padding: 0.5rem; text-align: left; border-bottom: 1px solid #eee; }
       |    .bar-bg { background: #eee; border-radius: 4px; height: 20px; }
       |    .bar-fg { background: #3b82f6; height: 100%; border-radius: 4px; }
       |    .muted { color: #666; }
       |  </style>
       |</head>
       |<body>
       |$body
       |</body>
       |</html>
       |""".stripMargin

  def homeBody: String =
    """<h1>Activation Lab</h1>
      |<p class="muted">A mini onboarding + activation tracker.</p>
      |<div style="display:flex; gap:1rem; margin-top:1.5rem;">
      |  <a class="button" href="/onboarding">Start Onboarding</a>
      |  <a class="button secondary" href="/dashboard">View Dashboard</a>
      |</div>
      |""".stripMargin

  /** One method per step, matching the four `step === '...'` branches in the original page.tsx */
  def onboardingBody(step: String, variant: String): String = {
    val backLink = """<a href="/" class="muted" style="font-size:0.9rem;">&larr; Home</a><h1>Onboarding</h1>"""

    val content = step match {
      case "signup" =>
        """<p>Step 1: Create your account</p>
          |<form action="/onboarding/signup" method="post">
          |  <input type="email" name="email" placeholder="you@example.com" required>
          |  <button type="submit">Sign up</button>
          |</form>
          |""".stripMargin

      case "verify" =>
        """<p>Step 2: Verify your email</p>
          |<p class="muted" style="font-size:0.9rem;">(Simulated - in a real app this would be a link sent to your email)</p>
          |<form action="/onboarding/verify" method="post">
          |  <button type="submit">I verified my email</button>
          |</form>
          |""".stripMargin

      case "profile" =>
        val extraFields =
          if (variant == "long")
            """<input type="text" name="company" placeholder="Company (optional)">
              |<input type="text" name="role" placeholder="Role (optional)">
              |""".stripMargin
          else ""
        s"""<p>Step 3: Complete your profile</p>
           |<p style="font-size:0.8rem; color:#999;">Variant: $variant</p>
           |<form action="/onboarding/profile" method="post">
           |  <input type="text" name="name" placeholder="Your name" required>
           |  $extraFields
           |  <button type="submit">Save profile</button>
           |</form>
           |""".stripMargin

      case "done" =>
        """<p>&#127881; Profile complete!</p>
          |<p>Now try the core action to activate:</p>
          |<form action="/onboarding/activate" method="post">
          |  <button type="submit">Create your first project</button>
          |</form>
          |""".stripMargin

      case "activated" =>
        """<p>&#127881; You're activated! Nicely done.</p>
          |<a class="button secondary" href="/dashboard">See the dashboard</a>
          |""".stripMargin

      case _ =>
        "<p>Unknown step.</p>"
    }

    backLink + content
  }

  def dashboardBody(funnel: Map[String, Int], variants: Map[String, (Int, Int)]): String = {
    val maxUsers = math.max(funnel.values.maxOption.getOrElse(1), 1)

    val funnelRows = Db.funnelSteps.zipWithIndex.map { case (step, i) =>
      val users = funnel.getOrElse(step, 0)
      val prevUsers = if (i == 0) users else funnel.getOrElse(Db.funnelSteps(i - 1), 0)
      val conversion =
        if (i > 0 && prevUsers > 0) f"""<span class="muted"> (${(users.toDouble / prevUsers * 100)}%.1f%% from prev)</span>"""
        else ""
      val barWidth = if (maxUsers > 0) users.toDouble / maxUsers * 100 else 0.0

      s"""<div style="margin-bottom:1.25rem;">
         |  <div style="display:flex; justify-content:space-between; margin-bottom:0.25rem;">
         |    <strong>$step</strong>
         |    <span>$users users$conversion</span>
         |  </div>
         |  <div class="bar-bg"><div class="bar-fg" style="width:$barWidth%;"></div></div>
         |</div>
         |""".stripMargin
    }.mkString

    val variantRows = List("short", "long").map { v =>
      val (completed, activated) = variants.getOrElse(v, (0, 0))
      val conversion = if (completed > 0) f"${(activated.toDouble / completed * 100)}%.1f%%" else "-"
      s"""<tr>
         |  <td style="font-weight:600;">$v</td>
         |  <td>$completed</td>
         |  <td>$activated</td>
         |  <td>$conversion</td>
         |</tr>
         |""".stripMargin
    }.mkString

    s"""<h1>Activation Funnel</h1>
       |<p class="muted">Users reaching each onboarding step</p>
       |<div style="margin-top:2rem; max-width:700px;">
       |$funnelRows
       |</div>
       |
       |<h2 style="margin-top:3rem;">Profile Form Experiment</h2>
       |<p class="muted">Short form vs. long form - which activates more users?</p>
       |<table>
       |  <thead>
       |    <tr><th>Variant</th><th>Completed Profile</th><th>Activated</th><th>Conversion</th></tr>
       |  </thead>
       |  <tbody>
       |$variantRows
       |  </tbody>
       |</table>
       |""".stripMargin
  }
}
