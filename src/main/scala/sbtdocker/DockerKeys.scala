package sbtdocker

import sbt._

object DockerKeys {
  val docker = taskKey[ImageId]("Build a Docker image.")
  val dockerBuildAndPush = taskKey[Map[ImageName, ImageDigest]]("Build a Docker image and pushes it to a registry.")
  val dockerPush = taskKey[Map[ImageName, ImageDigest]]("Push a already built Docker image to a registry.")

  @deprecated("Use imageNames instead.", "1.0.0")
  val imageName = taskKey[ImageName]("Name of the built image.")

  val dockerfile = taskKey[DockerfileBase]("Definition of the Dockerfile that should be built.")
  val imageNames = taskKey[Seq[ImageName]]("Names of the built image.")
  val dockerPath = settingKey[String]("Path to the Docker binary.")
  val buildOptions = settingKey[BuildOptions]("Options for the Docker build command.")
  val platforms = settingKey[Set[Platform]]("Platform list for multi platform build.")

  val dockerBuildArguments = settingKey[Map[String, String]](
    "Set build-time arguments for Docker image. Reference the argument keys with ARG and ENV instructions in the Dockerfile."
  )
}
