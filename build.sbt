import sbt._

name := "sbt-compact"

organization := "com.github.mtrampont"

version := "0.1.0"

scalaVersion := "2.12.18"

sbtPlugin := true

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.17" % Test

//TODO use scalafix
