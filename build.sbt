name := "sbt-docker"
organization := "se.marcuslonnberg"
organizationHomepage := Some(url("https://github.com/marcuslonnberg"))

lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    scriptedLaunchOpts := { scriptedLaunchOpts.value ++
      Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false
  )

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.19" % "test",
  "org.apache.commons" % "commons-text" % "1.12.0"
)

scalacOptions := Seq("-deprecation", "-unchecked", "-feature")

licenses := Seq("MIT License" -> url("https://github.com/marcuslonnberg/sbt-docker/blob/master/LICENSE"))
homepage := Some(url("https://github.com/marcuslonnberg/sbt-docker"))
scmInfo := Some(ScmInfo(url("https://github.com/marcuslonnberg/sbt-docker"), "scm:git:git://github.com:marcuslonnberg/sbt-docker.git"))

publishMavenStyle := true
publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

pomIncludeRepository := { _ => false}

pomExtra := {
  <developers>
    <developer>
      <id>marcuslonnberg</id>
      <name>Marcus Lönnberg</name>
      <url>http://marcuslonnberg.se</url>
    </developer>
  </developers>
}
