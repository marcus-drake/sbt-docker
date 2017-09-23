package sbtdocker

import sbt.Keys.target
import sbt._
import sbtdocker.DockerKeys._
import sbtdocker.staging.DefaultDockerfileProcessor

object DockerSettings {
  lazy val baseDockerSettings = Seq(
    docker := {
      val log = Keys.streams.value.log
      val dockerPath = (DockerKeys.dockerPath in docker).value
      val buildOptions = (DockerKeys.buildOptions in docker).value
      val stageDir = (target in docker).value
      val dockerfile = (DockerKeys.dockerfile in docker).value
      val imageNames = (DockerKeys.imageNames in docker).value
      DockerBuild(dockerfile, DefaultDockerfileProcessor, imageNames, buildOptions, stageDir, dockerPath, log)
    },
    dockerPush := {
      val log = Keys.streams.value.log
      val dockerPath = (DockerKeys.dockerPath in docker).value
      val imageNames = (DockerKeys.imageNames in docker).value

      DockerPush(dockerPath, imageNames, log)
    },
    dockerBuildAndPush := {
      docker.value match {
        case id =>
          dockerPush.value
          id
      }
    },
    dockerCreate := {
      val log = Keys.streams.value.log
      val dockerPath = (DockerKeys.dockerPath in docker).value
      val createOptions = (DockerKeys.createOptions in docker).value
      DockerCreate(dockerPath, createOptions, log)
    },
    dockerStart := {
      val log = Keys.streams.value.log
      val dockerPath = (DockerKeys.dockerPath in docker).value
      val startOptions = (DockerKeys.startOptions in docker).value
      DockerStart(dockerPath, startOptions, log)
    },
    dockerStop := {
      val log = Keys.streams.value.log
      val dockerPath = (DockerKeys.dockerPath in docker).value
      val stopOptions = (DockerKeys.stopOptions in docker).value
      DockerStop(dockerPath, stopOptions, log)
    },
    dockerRm := {
      val log = Keys.streams.value.log
      val dockerPath = (DockerKeys.dockerPath in docker).value
      val rmOptions = (DockerKeys.rmOptions in docker).value
      DockerRm(dockerPath, rmOptions, log)
    },
    dockerPort := {
      val log = Keys.streams.value.log
      val dockerPath = (DockerKeys.dockerPath in docker).value
      val portOptions = (DockerKeys.portOptions in docker).value
      DockerPort(dockerPath, portOptions, log)
    },
    dockerfile in docker := {
      sys.error(
        """A Dockerfile is not defined. Please define one with `dockerfile in docker`
          |
          |Example:
          |dockerfile in docker := new Dockerfile {
          | from("ubuntu")
          | ...
          |}
        """.stripMargin)
    },
    createOptions in docker := {
      sys.error(
        """Docker create settings not defined. Please define with `createOptions in docker`
          |
          |Example:
          |createOptions in docker := new CreateOptions {
          |  image("mysql")
          |  expose(22)
          |  port("0.0.0.0", 3306, 3306)
          |  port("0.0.0.0:9022:22")
          |  env("MYSQL_RANDOM_ROOT_PASSWORD" -> "true")
          |}
        """.stripMargin
      )
    },
    startOptions in docker := new StartOptions,
    stopOptions in docker := new StopOptions,
    rmOptions in docker := new RmOptions,
    target in docker := target.value / "docker",
    imageName in docker := {
      val organisation = Option(Keys.organization.value).filter(_.nonEmpty)
      val name = Keys.normalizedName.value
      ImageName(namespace = organisation, repository = name)
    },
    imageNames in docker := {
      Seq((imageName in docker).value)
    },
    dockerPath in docker := sys.env.get("DOCKER").filter(_.nonEmpty).getOrElse("docker"),
    buildOptions in docker := BuildOptions()
  )

  def autoPackageJavaApplicationSettings(
    fromImage: String,
    exposedPorts: Seq[Int],
    exposedVolumes: Seq[String],
    username: Option[String]
  ) = Seq(
    docker := {
      docker.dependsOn(Keys.`package`.in(Compile, Keys.packageBin)).value
    },
    Keys.mainClass in docker := {
      (Keys.mainClass in docker or Keys.mainClass.in(Compile, Keys.packageBin)).value
    },
    dockerfile in docker := {
      val maybeMainClass = Keys.mainClass.in(docker).value
      maybeMainClass match {
        case None =>
          sys.error("Either there are no main class or there exist several. " +
            "One can be set with 'mainClass in docker := Some(\"package.MainClass\")'.")

        case Some(mainClass) =>
          val classpath = Keys.managedClasspath.in(Compile).value
          val artifact = Keys.artifactPath.in(Compile, Keys.packageBin).value

          val appPath = "/app"
          val libsPath = s"$appPath/libs/"
          val artifactPath = s"$appPath/${artifact.name}"

          val dockerfile = Dockerfile()
          dockerfile.from(fromImage)

          val libPaths = classpath.files.map { libFile =>
            val toPath = file(libsPath) / libFile.name
            dockerfile.stageFile(libFile, toPath)
            toPath
          }
          val classpathString = s"${libPaths.mkString(":")}:$artifactPath"

          dockerfile.entryPoint("java", "-cp", classpathString, mainClass)

          dockerfile.expose(exposedPorts: _*)
          dockerfile.volume(exposedVolumes: _*)
          username.foreach(dockerfile.user)

          dockerfile.addRaw(libsPath, libsPath)
          dockerfile.add(artifact, artifactPath)

          dockerfile
      }
    }
  )
}
