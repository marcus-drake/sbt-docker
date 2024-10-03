package sbtdocker

object BuildOptions {

  object Remove {

    sealed trait Option

    case object Always extends Option

    case object OnSuccess extends Option

    case object Never extends Option

  }

  object Pull {

    sealed trait Option

    case object Always extends Option

    case object IfMissing extends Option

  }

}

/**
  * Options for when building a Docker image.
  * @param cache Use cache when building the image.
  * @param removeIntermediateContainers Remove intermediate containers after a build.
  * @param pullBaseImage Always attempts to pull a newer version of the base image.
  * @param platforms Allows cross platform builds. Make sure that you already set docker's buildx up on the machine that uses this feature
  * @param additionalArguments Provide any other arguments to the `docker build` task, see reference at https://docs.docker.com/engine/reference/commandline/build/#options. For example `Seq("--add-host", "127.0.0.1:12345", "--compress")`.
  */
final case class BuildOptions(
  cache: Boolean = true,
  removeIntermediateContainers: BuildOptions.Remove.Option = BuildOptions.Remove.OnSuccess,
  pullBaseImage: BuildOptions.Pull.Option = BuildOptions.Pull.IfMissing,
  platforms: Seq[String] = Seq.empty,
  additionalArguments: Seq[String] = Seq.empty
)

sealed abstract class Platform(val value: String) {}

object Platform {
  final case object LinuxAmd64 extends Platform("linux/amd64")
  final case object LinuxArm64 extends Platform("linux/arm64")
}

/**
  * Id of an Docker image.
  * @param id Id as a hexadecimal digit string.
  */
final case class ImageId(id: String) extends AnyVal {
  override def toString = id
}

/**
  * The image digest, the format of the digest is `algorithm:hex-string`
  * @param algorithm The algorithm used to produce the digest.
  * @param digest A digest of the image, as a string.
  */
final case class ImageDigest(algorithm: String, digest: String) {
  override def toString = s"$algorithm:$digest"
}

object ImageName {

  /**
    * Parse a [[sbtdocker.ImageName]] from a string.
    */
  def apply(name: String): ImageName = {
    val (registry, rest) = name.split("/", 3).toList match {
      case host :: x :: xs if host.contains(".") || host.contains(":") || host == "localhost" =>
        (Some(host), x :: xs)
      case xs =>
        (None, xs)
    }

    val (namespace, repoAndTag) = rest match {
      case n :: r :: Nil =>
        (Some(n), r)
      case r :: Nil =>
        (None, r)
      case _ =>
        throw new IllegalArgumentException(s"Invalid image name: '$name'")
    }

    val (repo, tag) = repoAndTag.split(":", 2) match {
      case Array(r, t) =>
        (r, Some(t))
      case Array(r) =>
        (r, None)
    }

    ImageName(registry, namespace, repo, tag)
  }
}

/**
  * Name of a Docker image.
  * Format: [registry/][namespace/]repository[:tag]
  * Examples: `docker-registry.example.com/scala:2.11` or `example/scala:2.11`
  * @param repository Name of the repository.
  * @param registry Host and optionally port of the registry, example `docker-registry.example.com:5000`.
  * @param namespace Namespace name.
  * @param tag Tag, for example a version number.
  */
final case class ImageName(
  registry: Option[String] = None,
  namespace: Option[String] = None,
  repository: String,
  tag: Option[String] = None
) {

  override def toString = {
    val registryString = registry.fold("")(_ + "/")
    val namespaceString = namespace.fold("")(_ + "/")
    val tagString = tag.fold("")(":" + _)
    registryString + namespaceString + repository + tagString
  }

  @deprecated("Use toString instead.", "0.4.0")
  def name = toString
}
