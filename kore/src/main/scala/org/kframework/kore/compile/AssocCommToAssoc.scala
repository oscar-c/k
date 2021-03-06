package org.kframework.kore.compile

import org.kframework.Collections._
import org.kframework.attributes.Att
import org.kframework.definition.{Module, Rule, Sentence}
import org.kframework.kore.SortedADT.SortedKVariable
import org.kframework.kore._


/**
 * Compiler pass for merging the rules as expected by FastRuleMatcher
 */
class AssocCommToAssoc(c: Constructors[K]) extends (Module => Module) {

  import c._

  val s = new ScalaSugar(c)

  import s._

  override def apply(m: Module): Module = {
    Module(m.name, m.imports, m.localSentences flatMap {apply(_)(m)}, m.att)
  }

  def apply(s: Sentence)(implicit m: Module): List[Sentence] = s match {
    //TODO(AndreiS): handle AC in requires and ensures
    case r: Rule => apply(r.body) map {Rule(_, r.requires, r.ensures, r.att)}
    case _ => List(s)
  }

  def apply(k: K)(implicit m: Module): List[K] = k match {
    case Unapply.KApply(label: KLabel, children: List[K]) if isAssocComm(label) =>
      convert(label, children, None)
    case Unapply.KRewrite(Unapply.KApply(label: KLabel, children: List[K]), right: K) if isAssocComm(label) =>
      //TODO(AndreiS): right is not always normalized, despite being normal right after NormalizeAssoc
      convert(label, children, Some(right))
    case Unapply.KApply(label: KLabel, children: List[K]) =>
      crossProduct(children map apply) map {label(_: _*)}
    case Unapply.KRewrite(left: K, right: K) =>
      apply(left) map {KRewrite(_, right, Att())}
    case _ =>
      List(k)
  }

  def isAssocComm(label: KLabel)(implicit m: Module): Boolean = {
    val att: Att = m.attributesFor.getOrElse(label, Att())
    att.contains(Att.assoc) && att.contains(Att.comm)
  }

  def convert(label: KLabel, children: List[K], rightOption: Option[K])(implicit m: Module): List[K] = {
    val opSort: Sort = m.signatureFor(label).head._2

    val (elements: Seq[K], nonElements: Seq[K]) = children partition {
      case v: SortedKVariable => m.subsorts.lessThanEq(v.sort, opSort);
      case _ => true
    }

    assert(nonElements.size <= 1)
    assert(nonElements.headOption forall { case v: KVariable => v.name.equals("THE_VARIABLE") || v.name.startsWith("DotVar") })
    val frameOption = nonElements.headOption

    val convertedChildren: List[List[K]] = frameOption match {
      case Some(v: KVariable) if v.name.equals("THE_VARIABLE") =>
        elements.permutations.toList map {
          _.foldRight(List(anonymousVariable(opSort))) { (e, l) => anonymousVariable(opSort) :: e :: l }
        }
      //TODO(AndreiS): check the variable is free (not constrained elsewhere by the rule)
      case Some(v: KVariable) if v.name.startsWith("DotVar") =>
        elements.permutations.toList map {
          _.foldRight(List(dotVariable(opSort, 0))) { (e, l) => dotVariable(opSort, (l.size + 1) / 2) :: e :: l }
        }
      case None =>
        elements.toList.permutations.toList
    }

    val convertedRightOption: Option[K] = rightOption match {
      case Some(right) =>
        frameOption match {
          case Some(v: KVariable) if v.name.startsWith("DotVar") =>
            //TODO(AndreiS): substitute in the entire rule, not just locally
            Some(substituteFrame(right, v.name, label((0 to elements.size) map {dotVariable(opSort, _)}: _*)))
          case _ => Some(right)
        }
      case None => None
    }

    val results = convertedChildren flatMap { cs => crossProduct(cs map apply) } map {label(_: _*)}
    convertedRightOption match {
      case Some(convertedRight) => results map {KRewrite(_, convertedRight, Att())}
      case None => results
    }
  }

  def substituteFrame(k: K, name: String, substitute: K): K = k match {
    case Unapply.KApply(label: KLabel, children: List[K]) => label(children map {substituteFrame(_, name, substitute)}: _*)
    case Unapply.KVariable(`name`) => substitute
    case _: K => k
  }

  def crossProduct[T](lls: List[List[T]]): List[List[T]] = {
    lls match {
      case (head: List[T]) :: (tail: List[List[T]]) =>
        for {(x: T) <- head; (xs: List[T]) <- crossProduct(tail)} yield x :: xs
      case List() => List(List())
    }
  }

  def anonymousVariable(s: Sort): K = SortedADT.SortedKVariable("THE_VARIABLE", Att().add("sort", s.name))

  def dotVariable(s: Sort, n: Int): K = SortedADT.SortedKVariable(s.name + "_DotVar" + n, Att().add("sort", s.name))

}
