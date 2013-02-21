package models.submission

import
  scalaz.{ Scalaz, ValidationNEL},
    Scalaz.ToValidationV

import
  models.util.ParamBundle

/**
 * Created with IntelliJ IDEA.
 * User: Jason
 * Date: 10/26/12
 * Time: 12:16 PM
 */

// Unless I wanna go all `shapeless` on this thing's ass, there's not really a good way to enforce having a `validate` method... --JAB
private[submission] trait Parser {
  protected type Target
  protected type ConsTuple <: Product
  protected type FailType = String
  protected type Output   = ValidationNEL[FailType, Target]
}

private[submission] trait FromBundleParser extends Parser {
  protected type ByteMapInput = Map[String, Array[Byte]]
  def byteFetch(key: String)(implicit params: ByteMapInput) = // Converts keys to `Validation`s
    params.get(key) map (_.successNel[String]) getOrElse (s"No item with key '$key' passed in".failNel)
  def fromBundle(bundle: ParamBundle) : Output
}

private[submission] trait DataFromBundleParser extends FromBundleParser {

  self: FromMapParser =>

  protected val DataKey = "data"

  protected def fromBundleHelper(bundle: ParamBundle) : Output

  override def fromBundle(bundle: ParamBundle) : Output = {
    if (bundle.stringParams.contains(DataKey))
      fromMap(bundle.stringParams)
    else
      fromBundleHelper(bundle)
  }

}

private[submission] trait FromMapParser extends Parser {
  protected type MapInput = Map[String, String]
  def fromMap(implicit params: MapInput) : Output
  def fetch(key: String)(implicit params: MapInput) = // Converts keys to `Validation`s
    params.get(key) map (_.successNel[String]) getOrElse (s"No item with key '$key' passed in".failNel)

}

private[submission] trait FromStringParser extends Parser {
  def fromString(str: String) : Output
}
