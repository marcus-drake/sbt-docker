import sbtrelease._
import ReleaseStateTransformations._
import ReleaseKeys._

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  releaseStepTask(scalafmtCheckAll),
  runTest,
  releaseStepInputTask(scripted),
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  publishArtifacts,
  setNextVersion,
  commitNextVersion,
  pushChanges
)
