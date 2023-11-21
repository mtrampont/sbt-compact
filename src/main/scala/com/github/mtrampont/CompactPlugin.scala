package com.github.mtrampont

import sbt.*
import sbt.Keys.*
import sbt.nio.file.FileTreeView

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object CompactPlugin extends AutoPlugin {
 override def trigger: PluginTrigger = allRequirements

 override def requires: Plugins = plugins.JvmPlugin

 object autoImport {
	 val compact = taskKey[File]("Compact all source into one file.")
	 val compactFileName = settingKey[String]("Name for the compacted source file.")
 }

 import autoImport._

 lazy val defaultSettings: Seq[Setting[_]] = Seq(
	 compactFileName := "Player.scala",
	 compact := compactDefault.value
 )

  override def projectSettings: Seq[Setting[_]] =
    defaultSettings


	val compactDefault = Def.task{
		compactSources((Compile / scalaSource).value, target.value / "compact" / compactFileName.value)
	}

	def compactSources(base: File, target: File): File = {
		val scalaGlob = Glob(base, "**/*.scala")
		val scalaFilesWithPackage =
			FileTreeView.nio.list(scalaGlob)
				.map{ case (path, attr) =>
					val fileSource = IO.readLines(path.toFile)
					val packageReg = """\s*package\s+(\w[\w.]+)\s*""".r
					fileSource.iterator.zipWithIndex
						.collectFirst {
							case (packageReg(pack), idx) => pack.split('.') -> fileSource.patch(idx, Nil, 1)
						}.getOrElse(Array[String]() -> fileSource)
				}
		val scalaTree = ScalaTree("", mutable.Queue.empty[Seq[String]], ArrayBuffer.empty[ScalaTree])
		scalaFilesWithPackage
			.foreach { case (packs, source) =>
				val treeToUpdate = packs.foldLeft(scalaTree) { case (upd, pack) =>
					upd.children.find(_.pack == pack)
						.getOrElse{
							val newTree =
								ScalaTree(
									pack = pack,
									sources = mutable.Queue.empty[Seq[String]],
									children = ArrayBuffer.empty[ScalaTree]
								)
							upd.children.append(newTree)
							newTree
						}
				}
				treeToUpdate.sources.enqueue(source)
			}

		val source =
			scalaTree.biFold(mutable.Buffer[String]())(
				{ case (src, (pack, sources)) =>
					if(pack.nonEmpty) src.append(s"object $pack {\n")
					sources.foreach{ lines =>
						src.append(lines.mkString("\n"))
						src.append("\n")
					}
					//TODO fix indentation
					src
				},
				{ case (src, (pack, sources)) =>
					if(pack.nonEmpty) src.append("}\n")
					src
				}
			).mkString

		IO.createDirectory(target.toPath.getParent.toFile)
		IO.write(
			file = target,
			content = source
		)
		target
	}

	case class ScalaTree(pack: String, sources: mutable.Queue[Seq[String]], children: ArrayBuffer[ScalaTree]){

		def fold[B](z: B)(f: (B, (String, mutable.Queue[Seq[String]])) => B): B = {
			children.foldLeft(f(z, (pack, sources))){ case (res, subtree) =>
				subtree.fold(res)(f)
			}
		}
		//TODO make it tailrec (store the children nodes still to process in an additional queue parameter)

		def biFold[B](z: B)(
			before: (B, (String, mutable.Queue[Seq[String]])) => B,
			after : (B, (String, mutable.Queue[Seq[String]])) => B
		): B = {
			after(
				children.foldLeft(before(z, (pack, sources))) { case (res, subtree) =>
					subtree.biFold(res)(before, after)
				},
				(pack, sources)
			)
		}
	}
}

