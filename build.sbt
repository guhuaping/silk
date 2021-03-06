import java.io.File

sonatypeProfileName in Global := "org.xerial"
description := "A framework for simplifying SQL pipelines"

packSettings
packMain := Map("silk" -> "xerial.silk.cui.SilkMain")
packExclude := Seq("silk")
packResourceDir += new File("silk-server/src/main/webapp") -> ""
packExtraClasspath := Map("silk" -> Seq("${PROG_HOME}"))

resolvers in Global += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

val commonSettings = Seq(
  scalaVersion in Global := "2.11.7",
  organization := "org.xerial.silk",
  crossPaths := false,
  scalacOptions in Compile := Seq("-language:experimental.macros", "-deprecation", "-feature")
)

lazy val root = Project(id = "silk", base = file(".")).settings(
  publish := {}
).aggregate(silkMacros, silkCore, silkFrame, silkCui, silkExamples, silkServer)

lazy val silkMacros =
  Project(id = "silk-macros", base = file("silk-macros"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scalap" % scalaVersion.value,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "org.xerial" % "xerial-lens" % "3.3.8"
    )
  )

lazy val silkCore =
  Project(id = "silk-core", base = file("silk-core"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-compiler" % scalaVersion.value,
      "org.scala-lang.modules" %% "scala-xml" % "1.0.5",
      "com.github.nscala-time" %% "nscala-time" % "2.2.0",
      "org.ow2.asm" % "asm-all" % "4.1",
      "com.esotericsoftware.kryo" % "kryo" % "2.20" exclude("org.ow2.asm", "asm"),
      "com.github.nscala-time" %% "nscala-time" % "2.2.0",
      "com.flyberrycapital" %% "scala-slack" % "0.3.0",
      "org.scalatest" %% "scalatest" % "2.2.4" % "test"
    )
  )
  .dependsOn(silkMacros)

lazy val silkFrame =
  Project(id = "silk-frame", base = file("silk-frame"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.xerial" % "sqlite-jdbc" % "3.8.11.1",
      "org.xerial.msgframe" % "msgframe-core" % "0.1.0-SNAPSHOT",
      "com.treasuredata.client" % "td-client" % "0.6.0" excludeAll (
        ExclusionRule(organization = "org.eclipse.jetty")
        ),
      "com.treasuredata" % "td-jdbc" % "0.5.1"
    )
  )
  .dependsOn(silkCore % "test->test;compile->compile", silkWorkflow)

lazy val silkWorkflow =
  Project(id = "silk-workflow", base = file("silk-workflow"))
  .settings(commonSettings)
  .dependsOn(silkCore % "test->test;compile->compile")


lazy val silkCui = Project(id = "silk-cui", base = file("silk-cui"))
                   .settings(commonSettings)
                   .dependsOn(silkCore % "test->test;compile->compile", silkServer, silkFrame)

lazy val silkExamples = Project(id = "silk-examples", base = file("silk-examples"))
                        .settings(commonSettings)
                        .dependsOn(silkCore % "test->test;compile->compile", silkFrame)

lazy val skinnyMicroVersion = "0.9.+"

lazy val silkServer = Project(id = "silk-server", base = file("silk-server"))
                      .settings(commonSettings)
                      .settings(servletSettings)
                      .settings(
                        description := "silk development server",
                        libraryDependencies ++= Seq(
                          // micro Web framework
                          "org.skinny-framework" %% "skinny-micro" % skinnyMicroVersion,
                          // jackson integration
                          "org.skinny-framework" %% "skinny-micro-jackson" % skinnyMicroVersion,
                          "org.skinny-framework" %% "skinny-micro-jackson-xml" % skinnyMicroVersion,
                          // json4s integration
                          "org.skinny-framework" %% "skinny-micro-json4s" % skinnyMicroVersion,
                          // Scalate integration
                          "org.skinny-framework" %% "skinny-micro-scalate" % skinnyMicroVersion,
                          // Standalone Web server (Jetty 9.2 / Servlet 3.1)
                          "org.skinny-framework" %% "skinny-micro-server" % skinnyMicroVersion,
                          "org.eclipse.jetty" % "jetty-webapp" % "9.2.13.v20150730" % "container"
                        ),
                        // for Scalate
                        dependencyOverrides := Set("org.scala-lang" % "scala-compiler" % scalaVersion.value)
                      )
                      .dependsOn(silkCore % "test->test;compile->compile")

pomExtra in Global := {
  <url>http://xerial.org/silk</url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenOses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <scm>
      <connection>scm:git:github.com/xerial/silk.git</connection>
      <developerConnection>scm:git:git@github.com:xerial/silk.git</developerConnection>
      <url>github.com/xerial/silk.git</url>
    </scm>
    <properties>
      <scala.version>
        {scalaVersion.value}
      </scala.version>
      <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <developers>
      <developer>
        <id>leo</id>
        <name>Taro L. Saito</name>
        <url>http://xerial.org/leo</url>
      </developer>
    </developers>
}

