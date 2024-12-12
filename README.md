sbt-docker
==========

sbt-docker is an [sbt][sbt] plugin that builds and pushes [Docker][docker] images for your project.

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/se.marcuslonnberg/sbt-docker/badge.svg)](https://maven-badges.herokuapp.com/maven-central/se.marcuslonnberg/sbt-docker)

Requirements
------------

* sbt
* Docker

Setup
-----

Add sbt-docker as a dependency in `project/plugins.sbt`:

```text
addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.11.0")
```

### Getting started

Below are some documentation on the sbt tasks and settings in the plugin.

This blog post gives a good introduction to the basics of sbt-docker: [Dockerizing your Scala apps with sbt-docker][dockerizing-scala-apps]

Also, take a look at the [example projects](examples).

Usage
-----

Start by enabling the plugin in your `build.sbt` file:
```scala
enablePlugins(DockerPlugin)
```

This sets up some settings with default values and adds tasks such as `docker` which builds a Docker image.
The only required setting that is left to define is `docker / dockerfile`.

### Artifacts

If you want your Dockerfile to contain one or several artifacts (such as JAR files) that your
project generates, then you must make the `docker` task depend on the tasks that generate them.
It could for example be with the `package` task or with tasks from plugins such as
[sbt-assembly][sbt-assembly].

### Defining a Dockerfile

In order to produce a Docker image a Dockerfile must be defined.
It should be defined at the `docker / dockerfile` key.
There is a mutable and an immutable Dockerfile class available, both provides a DSL which resembles
the plain text [Dockerfile] format.
The mutable class is default and is used in the following examples.

Example with the [sbt-assembly][sbt-assembly] plugin:
```scala
docker / dockerfile := {
  // The assembly task generates a fat JAR file
  val artifact: File = assembly.value
  val artifactTargetPath = s"/app/${artifact.name}"

  new Dockerfile {
    from("openjdk:8-jre")
    add(artifact, artifactTargetPath)
    entryPoint("java", "-jar", artifactTargetPath)
  }
}
```

Example with [sbt-native-packager][sbt-native-packager]:
```scala
enablePlugins(sbtdocker.DockerPlugin, JavaAppPackaging)

docker / dockerfile := {
  val appDir: File = stage.value
  val targetDir = "/app"

  new Dockerfile {
    from("openjdk:8-jre")
    entryPoint(s"$targetDir/bin/${executableScriptName.value}")
    copy(appDir, targetDir, chown = "daemon:daemon")
  }
}
```

Example with the sbt `package` task.
```scala
docker / dockerfile := {
  val jarFile: File = (Compile / packageBin / sbt.Keys.`package`).value
  val classpath = (Compile / managedClasspath).value
  val mainclass = (Compile / packageBin / mainClass).value.getOrElse(sys.error("Expected exactly one main class"))
  val jarTarget = s"/app/${jarFile.getName}"
  // Make a colon separated classpath with the JAR file
  val classpathString = classpath.files.map("/app/" + _.getName)
    .mkString(":") + ":" + jarTarget
  new Dockerfile {
    // Base image
    from("openjdk:8-jre")
    // Add all files on the classpath
    add(classpath.files, "/app/")
    // Add the JAR file
    add(jarFile, jarTarget)
    // On launch run Java with the classpath and the main class
    entryPoint("java", "-cp", classpathString, mainclass)
  }
}
```

Example with a Dockerfile in the filesystem.
```scala
docker / dockerfile := NativeDockerfile(file("subdirectory") / "Dockerfile")
```

Have a look at [DockerfileExamples](examples/DockerfileExamples.scala) for different ways of defining a Dockerfile.

#### Missing Dockerfile instructions

Dockerfile instructions that are missing from the sbt-docker DSL can still be used by calling the `.customInstruction(instructionName, arguments)` method.
Example:
```scala
new Dockerfile {
  customInstruction("FROM", "openjdk AS stage1")
  run("build")

  customInstruction("FROM", "openjdk AS stage2")
  customInstruction("COPY", "--from=stage1 /path/to/file /path/to/file")
  customInstruction("STOPSIGNAL", "SIGQUIT")
  
  entryPoint("application")
}
```

### Building an image

To build an image use the `docker` task.
Simply run `sbt docker` from your prompt or `docker` in the sbt console.

### Pushing an image

An image that have already been built can be pushed with the `dockerPush` task.
To both build and push an image use the `dockerBuildAndPush` task.

The `docker / imageNames` key is used to determine which image names to push.

### Custom image names

You can specify the names / tags you want your image to get after a successful build with the `docker / imageNames` key of type `Seq[sbtdocker.ImageName]`.

Example:
```scala
docker / imageNames := Seq(
  // Sets the latest tag
  ImageName(s"${organization.value}/${name.value}:latest"),

  // Sets a name with a tag that contains the project version
  ImageName(
    namespace = Some(organization.value),
    repository = name.value,
    tag = Some("v" + version.value)
  )
)
```

### Build options

Use the key `docker / buildOptions` to set build options.

#### cross-platform
The `platforms` parameter enables the cross-platform build.
With valuing this parameter the docker image will build using `buildx` command and the host environment should already been set up for.

⚠️ For using the cross builds you need QEMU binaries
```shell
docker run --privileged --rm tonistiigi/binfmt --install all
```

#### Example:
```scala
docker / buildOptions := BuildOptions(
  cache = false,
  removeIntermediateContainers = BuildOptions.Remove.Always,
  pullBaseImage = BuildOptions.Pull.Always,
  platforms = List("linux/arm64/v8"),
  additionalArguments = Seq("--add-host", "127.0.0.1:12345", "--compress")
)
```

### Build arguments

Use the key `docker / dockerBuildArguments` to set build arguments.

Example:
```scala
docker / dockerBuildArguments := Map(
  "KEY" -> "value",
  "CREDENTIALS" -> sys.env("CREDENTIALS")
)

docker / dockerfile := {
  new Dockerfile {
    // ...
    arg("KEY")
    arg("CREDENTIALS")
    env("KEY" -> "$KEY", "CREDENTIALS" -> "$CREDENTIALS")
    // ...
  }
}
```

### BuildKit support

Images can be built with [BuildKit](https://docs.docker.com/develop/develop-images/build_enhancements/) by enabling it in the daemon configuration or by passing the environment variable `DOCKER_BUILDKIT=1` to sbt.

[docker]: https://www.docker.com/
[dockerfile]: https://docs.docker.com/engine/reference/builder/
[dockerizing-scala-apps]: https://velvia.github.io/Docker-Scala-Sbt/
[sbt]: http://www.scala-sbt.org/
[sbt-assembly]: https://github.com/sbt/sbt-assembly
[sbt-native-packager]: https://github.com/sbt/sbt-native-packager
