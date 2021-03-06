package avrohugger
package format
package specific

import treehugger.forest._
import definitions._
import treehuggerDSL._

import java.io.{
  File,
  BufferedWriter,
  FileWriter,
  FileNotFoundException,
  IOException
}

import org.apache.avro.Schema
import org.apache.avro.compiler.specific.SpecificCompiler

import scala.collection.JavaConversions._

// Java required for generating Enums (Specific API requires Java enums)
object SpecificJavaTreehugger {

  def asJavaCodeString(
    classStore: ClassStore,
    namespace: Option[String],
    schema: Schema) = {
      
    def registerType(schema: Schema, classStore: ClassStore): Unit = {
      val classSymbol = RootClass.newClass(schema.getName)
      classStore.accept(schema, classSymbol)
    }

    def writeJavaTempFile(
      namespace: Option[String],
      schema: Schema, outDir: String): Unit = {
	    // Uses Avro's SpecificCompiler, which only compiles from files, thus we 
      // write the schema to a temp file so we can compile a Java enum from it.
	    val tempSchemaFile = File.createTempFile(schema.getName, ".avsc")
	    tempSchemaFile.deleteOnExit()
	    val out = new BufferedWriter(new FileWriter(tempSchemaFile))
	    out.write(schema.toString)
	    out.close()

	    val folderPath = {
	      if (namespace.isDefined) new File(outDir)
	      else new File(outDir) 
	    }
	    try { 
	      SpecificCompiler.compileSchema(tempSchemaFile, folderPath)
	    }     
	    catch {
	      case ex: FileNotFoundException =>
          sys.error("File not found:" + ex)
	      case ex: IOException =>
          sys.error("There was a problem using the file: " + ex)
	    }
	  }

    def deleteTemps(path: String) = {
      val penultimateFile = new File(path.split('/').take(2).mkString("/"))
      def getFiles(f: File): Set[File] = {
        Option(f.listFiles)
          .map(a => a.toSet)
          .getOrElse(Set.empty)
      }
      def getRecursively(f: File): Set[File] = {
        val files = getFiles(f)
        val subDirectories = files.filter(path => path.isDirectory)
        subDirectories.flatMap(getRecursively) ++ files + penultimateFile
      }
      def sortByDepth(f1: File, f2: File) = {
        def countLevels(f: File) = f.getAbsolutePath.count(c => c == '/')
        countLevels(f1) > countLevels(f2)
      }
      val filesToDelete = getRecursively(penultimateFile)
      val sortedFilesToDelete = filesToDelete.toList.sortWith(sortByDepth)
      sortedFilesToDelete.foreach(file => {
        if (getFiles(file).isEmpty) file.deleteOnExit
      })
    }

    registerType(schema, classStore)

    // Avro's SpecificCompiler only writes files, but we need a string
    // so write the Java file and read
    val outDir = "target/"
    writeJavaTempFile(namespace, schema, outDir)
    val tempPath = outDir + schema.getFullName.replace('.','/') + ".java"
    val tempFile = new File(tempPath)
    val fileContents = scala.io.Source.fromFile(tempPath)
    val codeString = fileContents.mkString
    fileContents.close
    deleteTemps(tempPath)
    codeString
  
  }
}
