/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package xerial.silk.cui

import java.io.File
import java.net.URL
import java.util.jar.JarFile

import xerial.core.log.Logger
import xerial.silk.core.ClassBox
import xerial.silk.core.util.Path
import Path._

import scala.annotation.tailrec


/**
 * ClassFinder finds a full class name from its partial class name
 *
 * @author Taro L. Saito
 */
object ClassFinder extends Logger {

  def findClass(clName: String, classLoader: => ClassLoader = Thread.currentThread.getContextClassLoader): Option[String] = {

    val cname = {
      val pos = clName.lastIndexOf(".")
      if (pos == -1)
        clName
      else
        clName.substring(pos + 1)
    }

    import scala.collection.JavaConversions._
    val classPathEntries = sys.props.getOrElse("java.class.path", "")
      .split(File.pathSeparator)
      .map { e => new File(e).toURI.toURL } ++
      ClassBox.classPathEntries(classLoader)

    trace(s"classpath entries:\n${classPathEntries.mkString("\n")}")

    val isFullPath = clName.lastIndexOf(".") != -1
    val clPath = s"${clName.replaceAll("\\.", "/")}.class"
    val clFile = s"${cname}.class"

    def removeExt(s: String) = s.replaceAll("\\.class$", "")

    def findTargetClassFile(resource: URL): Option[String] = {
      if (ClassBox.isJarFile(resource)) {
        // Find the target class from a jar file
        val path = resource.getPath
        val jarPath = path.replaceAll("%20", " ")
        val jarFilePath = jarPath.replace("file:", "")
        val jar = new JarFile(jarFilePath)
        val entryName = if (isFullPath)
          Option(jar.getEntry(s"/$clPath")).map(_.getName)
        else {
          jar.entries.collectFirst {
            case e if e.getName.endsWith(clFile) =>
              e.getName
          }
        }
        entryName.map(name => removeExt(name))
      }
      else if (resource.getProtocol == "file") {
        // Find the target class from a directory
        @tailrec
        def find(lst: List[File]): Option[File] = {
          if (lst.isEmpty)
            None
          else {
            val h = lst.head
            if (h.isDirectory)
              find(h.listFiles.toList ::: lst.tail)
            else {
              val fileName = h.getName
              if (fileName.endsWith(".class") && fileName == clFile)
                Some(h)
              else
                find(lst.tail)
            }
          }
        }
        val filePath = resource.getPath
        val base = new File(filePath)
        if (isFullPath) {
          // Search the target file by directly specifying the file name
          val f = new File(filePath, clPath)
          if (f.exists())
            Some(f.relativeTo(base).getPath)
          else
            None
        }
        else {
          // Search directories recursively
          find(List(base)).map { f => f.relativeTo(base).getPath }
        }
      }
      else
        None
    }

    val targetClassName = classPathEntries.toIterator.map(findTargetClassFile).collectFirst {
      case Some(relativePathToClass) => {
        removeExt(relativePathToClass).replaceAll("\\/", ".")
      }
    }

    targetClassName
  }

}
