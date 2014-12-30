package sbtdocker.mutable

import sbtdocker.{StagedArchive, StageFile, DockerfileLike, Instruction}

/**
 * Mutable Dockerfile.
 *
 * @example {{{
 *  val jarFile: File
 *
 *  new Dockerfile {
 *    from("dockerfile/java")
 *    add(jarFile, "/srv/app.jar")
 *    workDir("/srv")
 *    cmd("java", "-jar", "app.jar")
 *  }
 *  }}}
 *
 * @param instructions Ordered sequence of Dockerfile instructions.
 * @param stagedFiles Files and directories that should be copied to the stage directory.
 */
case class Dockerfile(var instructions: Seq[Instruction] = Seq.empty,
                      var stagedFiles: Seq[StageFile] = Seq.empty,
                      var stagedArchives: Seq[StagedArchive] = Seq.empty) extends DockerfileLike {
  type T = Dockerfile

  def addInstruction(instruction: Instruction) = {
    instructions :+= instruction
    this
  }

  def stageFile(file: StageFile) = {
    stagedFiles :+= file
    this
  }

  def stageFiles(files: TraversableOnce[StageFile]) = {
    stagedFiles ++= files
    this
  }

  def stageArchive(archive: StagedArchive) = {
    stagedArchives :+= archive
    this
  }
}
