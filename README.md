# mypoca scala project

mypoca landing page tracking with scala

- `/` - landing page
- `/onboarding` - a 4-step signup flow (signup -> verify -> profile -> activated),
  with a 50/50 "short form vs long form" experiment
- `/dashboard` - shows the funnel drop-off and the short-vs-long conversion comparison

## project layout

```
build.sbt
project/plugins.sbt        # sbt-assembly, to build a runnable jar
src/main/scala/app/
  Db.scala                 # all SQL lives here
  Templates.scala          # all HTML lives here (plain functions returning strings)
  Main.scala                # routes (like page.tsx/route.ts, but all in one object)
Dockerfile
```

## running it locally

need a Postgres database - using [Neon](https://neon.tech).

```bash
export POSTGRES_URL="postgresql://user:password@host:5432/dbname"
sbt run
```

Then open http://localhost:8080

## building a deployable jar

```bash
sbt assembly
java -jar target/scala-2.13/activation-lab.jar
```
