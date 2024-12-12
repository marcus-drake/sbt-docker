package sbtdocker

import sbt._

object DockerPlugin extends AutoPlugin {

  object autoImport {
    val DockerKeys = sbtdocker.DockerKeys

    val docker = DockerKeys.docker
    val dockerfile = DockerKeys.dockerfile
    val dockerPath = DockerKeys.dockerPath

    @deprecated("Use imageNames instead.", "1.0.0")
    val imageName = DockerKeys.imageName
    val imageNames = DockerKeys.imageNames
    val buildOptions = DockerKeys.buildOptions
    val platforms = DockerKeys.platforms
    val dockerBuildArguments = DockerKeys.dockerBuildArguments

    type Dockerfile = sbtdocker.Dockerfile
    val NativeDockerfile = sbtdocker.NativeDockerfile
    type NativeDockerfile = sbtdocker.NativeDockerfile
    val ImageId = sbtdocker.ImageId
    type ImageId = sbtdocker.ImageId
    val ImageName = sbtdocker.ImageName
    type ImageName = sbtdocker.ImageName
    val BuildOptions = sbtdocker.BuildOptions
    type BuildOptions = sbtdocker.BuildOptions
    val Platfrom = sbtdocker.Platform
    type Platform = sbtdocker.Platform

    val CopyFile = sbtdocker.staging.CopyFile
    type CopyFile = sbtdocker.staging.CopyFile

    /**
      * Settings to automatically build a Docker image for a JVM application.
      * @param fromImage Base image to use. Should have a JVM on the PATH.
      * @param exposedPorts List of ports to expose.
      * @param exposedVolumes List of volumes to expose.
      * @param username Username that should run the Java process.
      */
    @deprecated("Use sbt-native-packager plugin instead.", "1.10.0")
    def dockerAutoPackageJavaApplication(
      fromImage: String = "java:8-jre",
      exposedPorts: Seq[Int] = Seq.empty,
      exposedVolumes: Seq[String] = Seq.empty,
      username: Option[String] = None
    ): Seq[sbt.Def.Setting[_]] = {
      DockerSettings.autoPackageJavaApplicationSettings(fromImage, exposedPorts, exposedVolumes, username)
    }
  }

  override def projectSettings = DockerSettings.baseDockerSettings
}
