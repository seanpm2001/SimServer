package models.log

import
  scala.{ collection, concurrent, util => util_scala },
    collection.mutable.{ ArrayBuffer, HashMap },
    concurrent.{ Await, duration },
      duration._,
    util_scala.Random

import
  java.{ io, util => util_java },
    io.{ ByteArrayInputStream, File },
    util_java.zip.GZIPInputStream

import
  akka.{ actor, pattern, util => util_akka },
    actor.{ ActorRef, Props },
    pattern.ask,
    util_akka.Timeout

import
  play.api.libs.concurrent.Akka

import play.api.Play.current

object LoggingHandler {

  val DefaultEncoding = "ISO-8859-1"
  private val idActorMap = new HashMap[Long, ActorRef]()

  def createNewLog() : Long = {
    ensureLogDirExists()
    val id = Random.nextInt().abs
    val actor = Akka.system.actorOf(Props(new LogActor(id, closeLog(_))))
    idActorMap.put(id, actor)
    id
  }

  def log(key: Long, data: String) : String = {
    implicit val timeout = Timeout(5 seconds)
    idActorMap.get(key) flatMap {
      case x if (!x.isTerminated) => Option(Await.result(x ? prepareData(data), timeout.duration).toString + '\n')
      case _                      => None
    } getOrElse ("File already closed")
  }

  // If there's ever a desire for teachers to access logs, this could be useful.  Until then... it probably should exist.
//  def retrieveLogText(key: Long) : String = {
//    val logDir = new File(LogActor.ExpectedLogDir)
//    val arr = logDir.listFiles(new FilenameFilter() {
//      def accept(file: File, name: String): Boolean = {
//        name.toLowerCase.endsWith(s"sid$key${LogActor.LogFileExtension}")
//      }
//    })
//    arr.lastOption map {  x => val src = Source.fromFile(x); val lines = src.getLines().mkString("\n"); src.close(); lines } getOrElse
//    ("Invalid log key given: " + key)
//  }

  private[models] def closeLog(id: Long) {
    idActorMap.remove(id)
  }

  private def ensureLogDirExists() {
    val logDir = new File(LogActor.ExpectedLogDir)
    if (!logDir.exists()) logDir.mkdir()
  }

  private def prepareData(data: String, encoding: String = DefaultEncoding) : String = {
    (unGzip(data.getBytes(encoding)) flatMap (validateData(_))).
      orElse (decompressData(data, encoding) orElse Option(data) flatMap (validateData(_))).
      getOrElse ("ERROR   DATA MUST BE TEXT OR GZIP COMPRESSED" replaceAll(" ", "_"))

  }

  /* Some(data)  if valid
   * None        if invalid
   */
  private def validateData(data: String) : Option[String] = {
    if (isValid(data)) Option(data) else None
  }

  private def decompressData(data: String, encoding: String = DefaultEncoding) : Option[String] = {
    unGzip(java.net.URLDecoder.decode(data, encoding).getBytes(encoding))
  }

  private def unGzip(data: Array[Byte]) : Option[String] =
    try {

      val in = new GZIPInputStream(new ByteArrayInputStream(data))
      val buffer = new ArrayBuffer[Byte]

      while (in.available() > 0)
        buffer.append(in.read().toByte)

      in.close()
      Some(buffer.map(_.toChar).mkString.reverse dropWhile (_.toByte < 0) reverse) // Make string and trim off any nonsense at end

    }
    catch {
      case ex: Exception =>
        play.api.Logger.warn("Failed to un-GZIP log data",  ex)
        None
    }

  // I used to sass this validation, but, honestly... it's not the worst
  private def isValid(data: String) : Boolean = data.toLowerCase.matches("""(?s)^[a-z]{3}.*""")

}
