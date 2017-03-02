package sbtdocker

import sbt.Keys.target
import sbt._
import sbtdocker.DockerKeys._
import staging.DefaultDockerfileProcessor

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
    dockerCopy := {
      val log = Keys.streams.value.log
      val dockerPath = (DockerKeys.dockerPath in docker).value
      val dockerMachinePath = (DockerKeys.dockerMachinePath in docker).value
      val dockerMachineName = (DockerKeys.dockerMachineName in docker).value
      val imageNames = (DockerKeys.imageNames in docker).value

      DockerCopy(dockerPath, dockerMachinePath, dockerMachineName, imageNames, log)
    },
    dockerBuildAndPush <<= (docker, dockerPush) { (build, push) =>
      build.flatMap { id =>
        push.map(_ => id)
      }
    },
    dockerBuildAndCopy <<= (docker, dockerCopy) { (build, copy) =>
      build.flatMap { id =>
        copy.map(_ => id)
      }
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
    dockerMachineName in docker := "default",
    dockerMachinePath in docker := sys.env.get("DOCKER_MACHINE").filter(_.nonEmpty).getOrElse("docker-machine"),
    buildOptions in docker := BuildOptions()
  )

  def autoPackageJavaApplicationSettings(fromImage: String,
                                         exposedPorts: Seq[Int],
                                         exposedVolumes: Seq[String],
                                         username: Option[String]) = Seq(
    docker <<= docker.dependsOn(Keys.`package`.in(Compile, Keys.packageBin)),
    Keys.mainClass in docker <<= Keys.mainClass in docker or Keys.mainClass.in(Compile, Keys.packageBin),
    dockerfile in docker <<= (Keys.managedClasspath in Compile, Keys.artifactPath.in(Compile, Keys.packageBin), Keys.mainClass in docker) map {
      case (_, _, None) =>
        sys.error("Either there are no main class or there exist several. " +
          "One can be set with 'mainClass in docker := Some(\"package.MainClass\")'.")
      case (classpath, artifact, Some(mainClass)) =>
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
  )
}
