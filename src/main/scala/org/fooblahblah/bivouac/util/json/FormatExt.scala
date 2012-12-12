package org.fooblahblah.bivouac.util.json

import play.api.libs.json.JsPath
import play.api.libs.json.Reads
import play.api.libs.json.OFormat
import play.api.libs.json.Format
import play.api.libs.json.Writes
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsNull

object FormatExt {

  // Helps with scenarios for Option reads which cannot normally be parsed if a null is passed
  //  e.g. Option[Long] will blow up when null is specified.

  def nullableOptionalFormat[A](path:JsPath)(implicit f: Format[A]): OFormat[Option[A]] =
    OFormat(nullableOptionalReads(path)(f), Writes.optional(path)(f))

  def nullableOptionalReads[A](path:JsPath)(implicit reads: Reads[A]): Reads[Option[A]] =
    Reads[Option[A]](json => path.asSingleJsResult(json).fold(_ => JsSuccess(None), {
      case JsNull => JsSuccess(None)
      case a => reads.reads(a).repath(path).map(Some(_))
    }))

  case class NullableOptional(path: JsPath) {
    def readNullableOpt[T](implicit r: Reads[T]) = nullableOptionalReads(path)
    def formatNullableOpt[T](implicit f: Format[T]): OFormat[Option[T]] = nullableOptionalFormat[T](path)(f)
  }

  implicit def nullableOptional(path: JsPath) = NullableOptional(path)

}