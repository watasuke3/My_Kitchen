name := """backend"""
organization := "com.example"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.18"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test
libraryDependencies += "org.scalamock"           %% "scalamock"           % "6.0.0" % Test

// Database
libraryDependencies += "org.playframework" %% "play-slick"            % "6.1.1"
libraryDependencies += "org.playframework" %% "play-slick-evolutions"  % "6.1.1"
libraryDependencies += "org.postgresql"     %  "postgresql"            % "42.7.3"

// Password hashing
libraryDependencies += "com.github.t3hnar" %% "scala-bcrypt" % "4.3.0"

// Rate limiting (login brute-force protection)
libraryDependencies += "com.digitaltangible" %% "play-guard" % "3.0.0"

// JSON
libraryDependencies += "com.typesafe.play" %% "play-json" % "2.10.6"

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "com.example.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "com.example.binders._"
