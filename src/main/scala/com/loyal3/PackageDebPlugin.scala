package com.loyal3

import sbt._
import sbt.LoggedOutput
import sbt.Project.Initialize
import sbt.Keys._

object PackageDebPlugin extends Plugin {
  val packageDebContents = TaskKey[Traversable[(File, File)]]("package-deb-sources", "Content from which to assemble the package.")
  val packageDebOutputDirectory = SettingKey[File]("package-deb-output-directory", "Directory into which package should be built.")
  val packageDebExplodedDirectory = SettingKey[File]("package-deb-exploded-directory", "Directory into which sources should be copied before packaging.")
  val packageDebPackageName = SettingKey[String]("package-deb-package-name", "Name of the package to report to dpkg on install.")
  val packageDebPackageFileName = TaskKey[String]("package-deb-package-file-name", "File name of the created package.")
  val packageDebAdditionalOptions = SettingKey[Traversable[String]]("package-deb-packaging-options", "Additional options to pass to FPM when creating the package.")

  val packageDeb = TaskKey[File]("package-deb",
    "Constructs a .deb package according to the specifications defined by [" +
      "'package-deb-sources', " +
      "'package-deb-output-directory', " +
      "'package-deb-exploded-directory', " +
      "'package-deb-package-name', " +
      "'package-deb-package-file-name', " +
      "'package-deb-additional-options']")

  protected def packageDebTask: Initialize[Task[File]] =
    (packageDebContents,
      packageDebOutputDirectory,
      packageDebExplodedDirectory,
      packageDebPackageName,
      packageDebPackageFileName,
      packageDebAdditionalOptions,
      managedClasspath in Compile,
      baseDirectory,
      streams) map {

      (contents,
       outputDirectory,
       explodedDirectory,
       packageName,
       packageFileName,
       additionalSwitches,
       moduleCompileClasspath,
       moduleBaseDirectory,
       streams) => {

        streams.log.info("Starting .deb package construction.")
        streams.log.debug(
          "Using the following package specification: \n"
            + "\tcontents: " + contents + "\n"
            + "\toutput directory: " + outputDirectory + "\n"
            + "\texploded directory: " + explodedDirectory + "\n"
            + "\tpackaging options: " + additionalSwitches)

        streams.log.info("Creating exploded package directory: " + explodedDirectory.absolutePath)
        IO.createDirectory(explodedDirectory)

        val targetFiles = contents.map(_._2)

        if (targetFiles.exists(file => file.isAbsolute)) {
          throw new IllegalArgumentException("All target files must be specified with relative paths.")
        }

        val pathsToCopy = contents.map {
          case (sourceFile, targetFile) => {
            (sourceFile, explodedDirectory / targetFile.getPath)
          }
        }

        streams.log.info("Copying package contents: \n")
        pathsToCopy.foreach {
          case (sourceFile, targetFile) => {
            streams.log.info("\t" + sourceFile.absolutePath + " -> " + targetFile.absolutePath + "\n")
            if (sourceFile.isDirectory) {
              IO.copyDirectory(sourceFile, targetFile, overwrite = true)
            } else {
              IO.copy(Seq(Pair(sourceFile, targetFile)), overwrite = true)
            }
          }
        }

        streams.log.info("Creating package directory: " + outputDirectory.absolutePath)
        IO.createDirectory(outputDirectory)
        val packageFilePath = outputDirectory / packageFileName


        streams.log.info("Creating .deb package at: " + packageFilePath.absolutePath)
        val classpathString = moduleCompileClasspath
          .files
          .map(_.absolutePath)
          .reduce(classpathAccumulator)

        val classpath = Seq("-cp", classpathString)

        val programStartupSwitches = Seq(
          "org.jruby.Main",
          "-r", classpathResource("fpm/package/dir.rb").getPath,
          "-S", "fpm")

        val defaultSwitches = Seq(
          "-a", "all",
          "-n", packageName,
          "-p", packageFilePath.absolutePath
        )

        val mandatorySwitches = Seq(
          "-C", explodedDirectory.absolutePath,
          "-s", "dir",
          "-t", "deb",
          "--deb-user", "0",
          "--deb-group", "0",
          ".")

        val arguments = programStartupSwitches ++ defaultSwitches ++ additionalSwitches ++ mandatorySwitches

        streams.log.debug("Forking a java process with output going to SBT logger.")
        streams.log.debug("\t-> classpath: " + classpath)
        streams.log.debug("\t-> arguments: " + arguments)

        Fork.java(
          None,
          classpath ++ arguments,
          LoggedOutput(streams.log))

        streams.log.info("Finished .deb package construction.")

        packageFilePath
      }
    }

  val gemJarsResolver = "Gemjars Repository" at "http://deux.gemjars.org"

  lazy val packageDebDependencies = Seq(jruby, fpm, arrPm)

  val jruby = "org.jruby" % "jruby-complete" % "1.7.3"
  val fpm = "org.rubygems" % "fpm" % "0.4.26"
  val arrPm = "org.rubygems" % "arr-pm" % "0.0.8"

  val packageDebSettings: Seq[Setting[_]] = Seq(
    resolvers += gemJarsResolver,

    libraryDependencies ++= packageDebDependencies,

    packageDebContents := Seq(),
    packageDebOutputDirectory <<= (target) {
      t => t / "package"
    },
    packageDebExplodedDirectory <<= (target) {
      t => t / "exploded-package"
    },
    packageDebPackageName := "undefined",
    packageDebPackageFileName := "package.deb",
    packageDebAdditionalOptions := Seq(),

    packageDeb <<= packageDebTask
  )

  private def classpathAccumulator: (String, String) => String = {
    (accumulator: String, classpathDependency: String) =>
      accumulator + ":" + classpathDependency
  }

  private def classpathResource(relativePath: String) = {
    this.getClass.getClassLoader.getResource(relativePath)
  }
}


