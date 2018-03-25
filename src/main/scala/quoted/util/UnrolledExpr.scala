package scala.quoted
package util

import scala.quoted.util.Lifters._

object UnrolledExpr {

  implicit class Unrolled[T: Liftable, It <: Iterable[T]](xs: It) {
    def unrolled: UnrolledExpr[T, It] = new UnrolledExpr(xs)
  }

  // TODO support blocks in the compiler to avoid creating trees of blocks?
  def block[T](stats: Iterable[Expr[_]], expr: Expr[T]): Expr[T] = {
    val it: Iterator[Expr[_]] = stats.iterator
    def rec(): Expr[T] = if (it.hasNext) '{ ~it.next(); ~rec() } else expr
    rec()
  }

}

class UnrolledExpr[T: Liftable, It <: Iterable[T]](xs: It) {
  import UnrolledExpr._

  def foreach[U](f: T => Expr[U]): Expr[Unit] = block(xs.map(f), '())

  def withFilter(f: T => Boolean): UnrolledExpr[T, Iterable[T]] = new UnrolledExpr(xs.filter(f))

  def foldLeft[U](acc: Expr[U])(f: (Expr[U], T) => Expr[U]): Expr[U] =
    xs.foldLeft(acc)((acc, x) => f(acc, x))
}
