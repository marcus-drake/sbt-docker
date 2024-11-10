package sbtdocker

import scala.annotation.nowarn

import sbt.Keys.target
import sbt._
import sbtdocker.DockerKeys._
import sbtdocker.staging.DefaultDockerfileProcessor

object DockerSettings {
  @nowarn("msg=value imageName in object DockerKeys is deprecated")
  lazy val baseDockerSettings = Seq(
    docker := {
      val log = Keys.streams.value.log
      val dockerPath = (docker / DockerKeys.dockerPath).value
      val buildOptions = (docker / DockerKeys.buildOptions).value
      val stageDir = (docker / target).value
      val dockerfile = (docker / DockerKeys.dockerfile).value
      val imageNames = (docker / DockerKeys.imageNames).value
      val buildArguments = (docker / DockerKeys.dockerBuildArguments).value
      DockerBuild(dockerfile, DefaultDockerfileProcessor, imageNames, buildOptions, buildArguments, stageDir, dockerPath, log)
    },
    dockerPush := {
      val log = Keys.streams.value.log
      val dockerPath = (docker / DockerKeys.dockerPath).value
      val imageNames = (docker / DockerKeys.imageNames).value

      DockerPush(dockerPath, imageNames, log)
    },
    dockerBuildAndPush := Def.taskDyn {
      docker.value
      Def.task {
        dockerPush.value
      }
    }.value,
    docker / dockerfile := {
      sys.error("""A Dockerfile is not defined. Please define one with `docker / dockerfile`
        |
        |Example:
        |docker / dockerfile := new Dockerfile {
        | from("ubuntu")
        | ...
        |}
        """.stripMargin)
    },
    docker / target := target.value / "docker",
    docker / imageName := {
      val organisation = Option(Keys.organization.value).filter(_.nonEmpty)
      val name = Keys.normalizedName.value
      ImageName(namespace = organisation, repository = name)
    },
    docker / imageNames := {
      Seq((docker / imageName).value)
    },
    docker / dockerPath := sys.env.get("DOCKER").filter(_.nonEmpty).getOrElse("docker"),
    docker / buildOptions := BuildOptions(),
    docker / dockerBuildArguments := Map.empty
  )

  def autoPackageJavaApplicationSettings(
    fromImage: String,
    exposedPorts: Seq[Int],
    exposedVolumes: Seq[String],
    username: Option[String]
  ) = Seq(
    docker := {
      docker.dependsOn(Compile / Keys.packageBin / Keys.`package`).value
    },
    docker / Keys.mainClass := {
      (docker / Keys.mainClass).or(Compile / Keys.packageBin / Keys.mainClass).value
    },
    docker / dockerfile := {
      val maybeMainClass = (docker / Keys.mainClass).value
      maybeMainClass match {
        case None =>
          sys.error(
            "Either there are no main class or there exist several. " +
              "One can be set with 'docker / mainClass := Some(\"package.MainClass\")'."
          )

        case Some(mainClass) =>
          val classpath = (Compile / Keys.managedClasspath).value
          val artifact = (Compile / Keys.packageBin / Keys.artifactPath).value

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
