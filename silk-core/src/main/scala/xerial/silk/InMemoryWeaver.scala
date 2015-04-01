//--------------------------------------
//
// InMemoryExecutor.scala
// Since: 2013/12/16 4:32 PM
//
//--------------------------------------

package xerial.silk

import xerial.core.log.Logger
import scala.util.Random
import scala.io.Source
import xerial.core.util.Shell
import scala.sys.process.Process
import xerial.silk.core._
import java.nio.file._
import xerial.silk.core.ScatterSeq
import xerial.silk.core.MapSingleOp
import xerial.silk.core.ReadLine
import xerial.silk.core.SplitOp
import xerial.silk.core.SubscribeSingleOp
import xerial.silk.core.ReduceOp
import xerial.silk.core.FlatMapFilterOp
import xerial.silk.core.GroupByOp
import xerial.silk.core.FilterOp
import xerial.silk.core.FlatMapOp
import xerial.silk.core.AggregateOp
import xerial.silk.core.DistinctOp
import xerial.silk.core.NaturalJoinOp
import xerial.silk.core.FlatMapSeqOp
import xerial.silk.core.CommandOp
import xerial.silk.core.SubscribeSeqOp
import xerial.silk.core.LoadFile
import xerial.silk.core.SamplingOp
import xerial.silk.core.SizeOp
import xerial.silk.core.SortOp
import xerial.silk.core.ConcatOp
import xerial.silk.core.CommandOutputLinesOp
import xerial.silk.core.ZipWithIndexOp
import xerial.silk.core.CollectOp
import xerial.silk.core.MapOp
import xerial.silk.core.SaveObjectOp
import xerial.silk.core.ListFilesOp
import xerial.silk.core.FlatMapSeqWithOp
import xerial.silk.core.MapFilterOp
import xerial.silk.core.RawSeq
import xerial.silk.core.MapWithOp
import java.io.{File, IOException}
import java.nio.file.attribute.BasicFileAttributes


case class InMemoryWeaverConfig()

class LocalFileSink(val baseDir : String = ".silk") extends IDUtil {

  def fileFor(op:Silk[_]) : File = {
    val f = new File(s".silk/data/${op.id.prefix2}/${op.id.prefix}")
    f
  }

  def tmpFileFor(op:Silk[_]) : File = {
    val f = File.createTempFile(op.id.prefix, ".tmp", fileFor(op).getParentFile)
    f
  }

  def exists(op:Silk[_]) : Boolean = {
    fileFor(op).exists()
  }

}

/**
 * In-memory silk executor for testing purpose
 * @author Taro L. Saito
 */
class InMemoryWeaver extends Weaver with FunctionWrap with IDUtil with Logger {

  type Config = InMemoryWeaverConfig
  val config = InMemoryWeaverConfig()

  type Sink = LocalFileSink
  val sink = new LocalFileSink


  private def future[A](v: A): SilkFuture[A] = new ConcreteSilkFuture[A](v)


  override def weave[A](op: SilkSeq[A]): SilkFuture[Seq[A]] =
    future(eval(op).asInstanceOf[Seq[A]])


  override def weave[A](op: SilkSingle[A]): SilkFuture[A] = {
    future(eval(op).asInstanceOf[A])
  }

  import SilkException._

  private def eval(silk: SilkSeq[_]): Seq[_] = {
    debug(s"eval $silk")
    silk match {
      case RawSeq(id, fc, seq) => seq
      case ScatterSeq(id, fc, seq, split) => seq
      case MapOp(id, fc, in, f) => eval(in).map(f.toF1)
      case MapFilterOp(id, fc, in, f, ft) =>
        eval(in).map(f.toF1).filter(ft.toFilter)
      case MapWithOp(id, fc, in, res, f) =>
        eval(in).map(f.toAgg(_, res))
      case FlatMapFilterOp(id, fc, in, f, ft) =>
        eval(in).flatMap(f.tofMap).filter(ft.toFilter)
      case FlatMapOp(id, fc, in, f) =>
        val b = Seq.newBuilder[Any]
        val input = eval(in)
        for (e <- input) {
          val es = f.toFlatMap.apply(e)
          b ++= eval(es)
        }
        b.result()
      case FlatMapSeqOp(id, fc, in, f) => eval(in).flatMap(f.tofMap)
      case FlatMapSeqWithOp(id, fc, in, res, f) =>
        eval(in).flatMap(f.toFmapRes(_, res))
      case FilterOp(id, fc, in, f) => eval(in).filter(f.toFilter)
      case SplitOp(id, fc, in) => eval(in)
      case ConcatOp(id, fc, in) => NA
      case SortOp(id, fc, in, ord, partitioner) => eval(in).sorted(ord.asInstanceOf[Ordering[Any]])
      case GroupByOp(id, fc, in, f) => eval(in).groupBy(f.toF1).toSeq
      case SamplingOp(id, fc, in, p) => {
        val input = eval(in).toIndexedSeq
        val size = input.size
        val numSample: Int = math.min(size, math.min(1, math.floor(size.toDouble * p).toInt))
        for (i <- 0 until numSample) yield
          input(Random.nextInt(size))
      }
      case ZipWithIndexOp(id, fc, in) => eval(in).zipWithIndex
      case CollectOp(id, fc, in, pf) => eval(in).collect(pf.asInstanceOf[PartialFunction[Any, Any]])
      case DistinctOp(id, fc, in) => eval(in).distinct
      case ReadLine(id, fc, file) => Source.fromFile(file).getLines().toSeq
      case cmd@CommandOutputLinesOp(id, fc, sc, args) => {
        val pb = Shell.prepareProcessBuilder(cmd.cmdString(this), true)
        Process(pb).lineStream
      }
      case nj@NaturalJoinOp(id, fc, l, r) =>
        val left = eval(l)
        val right = eval(r)
        val (lkey, rkey) = nj.keyParameterPairs
        val b = Seq.newBuilder[(Any, Any)]
        if (left.size < right.size) {
          val leftTable = left.map(e => lkey.get(e) -> e).toMap
          for (re <- right) {
            val rk = rkey.get(re)
            if (leftTable.contains(rk))
              b += ((leftTable(rk), re))
          }
        }
        else {
          val rightTable = right.map(e => rkey.get(e) -> e).toMap
          for (le <- left) {
            val lk = lkey.get(le)
            if (rightTable.contains(lk))
              b += ((le, rightTable(lk)))
          }
        }
        b.result
      case Silk.Empty => Seq.empty
      case SaveObjectOp(id, fc, in) => eval(in)
      case SubscribeSeqOp(id, fc, in) => eval(in)
      case ListFilesOp(id, fc, pattern) =>
        val m = FileSystems.getDefault.getPathMatcher(s"glob:./${pattern}")
        val b = Seq.newBuilder[File]
        Files.walkFileTree(Paths.get("."), new SimpleFileVisitor[Path]() {
          override def visitFile(file: Path, attrs: BasicFileAttributes) = {
            if(m.matches(file))
             b += file.toFile
            FileVisitResult.CONTINUE
          }
          override def visitFileFailed(file: Path, exc: IOException) = FileVisitResult.CONTINUE
        })
        b.result()
      case ListDirsOp(id, fc, pattern) =>
        val m = FileSystems.getDefault.getPathMatcher(s"glob:./${pattern}")
        val b = Seq.newBuilder[File]
        Files.walkFileTree(Paths.get("."), new SimpleFileVisitor[Path]() {
          override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes) = {
            if(m.matches(dir))
              b += dir.toFile
            FileVisitResult.CONTINUE
          }
          override def visitFileFailed(file: Path, exc: IOException) = FileVisitResult.CONTINUE
        })
        b.result()
      case other => SilkException.error(s"unknown op:$other")
    }
  }

  private def eval(silk: SilkSingle[_]): Any = {
    debug(s"eval ${silk}")

    silk match {
      case MapSingleOp(id, fc, in, f) => f.toF1(eval(in))
      //case FlatMapOp(id, fc, in, f) => eval(in).flatMap(f.tofMap)
      //case FilterSingleOp(id, fc, in, f) => f.toFilter.apply()
      case SizeOp(id, fc, in) => eval(in).size.toLong
      case ReduceOp(id, fc, in, f) =>
        eval(in).reduce(f.asInstanceOf[(Any, Any) => Any])
      case AggregateOp(id, fc, in, z, seqop, combop) =>
        eval(in).aggregate[Any](z)(seqop.toAgg, combop.toAgg)
      case cmd@CommandOp(id, fc, sc, args, resource) =>
        Shell.exec(cmd.cmdString(this))
      case LoadFile(id, fc, file) => // nothing to do
      case SubscribeSingleOp(id, fc, in) => eval(in)
      case cmd@CommandOutputFileOp(id, fc, sc, args) => {
        if(sink.exists(cmd))
         sink.fileFor(cmd)
        else {
          val pb = Shell.prepareProcessBuilder(cmd.cmdString(this), false)
          val outFile = sink.fileFor(cmd)
          val tmpFile = sink.tmpFileFor(cmd)
          outFile.getParentFile.mkdirs()
          tmpFile.getParentFile.mkdirs()
          try {
            val ret = Process(pb).#>(tmpFile).!
            ret match {
              case 0 =>
                Files.move(tmpFile.toPath, outFile.toPath)
                outFile
              case other => SilkException.error(s"Return code: $ret")
            }
          }
          finally {
            Files.deleteIfExists(tmpFile.toPath)
          }
        }
      }
      case other => SilkException.error(s"unknown op: $other")
    }

  }

  override private[silk] def runF0[R](locality: Seq[String], f: => R): R = {
    f
  }


}