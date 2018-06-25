package scala.quoted.shonan

import dotty.tools.dotc.quoted.Toolbox._
import scala.quoted._

// RINGS

trait Ring[T] {
  val zero: T
  val one: T
  val add: (x: T, y: T) => T
  val sub: (x: T, y: T) => T
  val mul: (x: T, y: T) => T
  // val eq: (x: T, y: T) => Option[Boolean]

  implicit class Ops(x: T) {
    def +(y: T): T = add(x, y)
    def -(y: T): T = sub(x, y)
    def *(y: T): T = mul(x, y)
  }
}

object Ring {
  implicit def ringInt: Ring[Int] = new Ring {
    val zero = 0
    val one  = 0
    val add = (x, y) => x + y
    val sub = (x, y) => x - y
    val mul = (x, y) => x * y
    // val eq  = (x, y) => Some(x == y)
  }

  implicit def ringIntExpr: Ring[Expr[Int]] = new Ring {
    val zero = '(0)
    val one  = '(1)
    val add = (x, y) => '(~x + ~y)
    val sub = (x, y) => '(~x - ~y)
    val mul = (x, y) => '(~x * ~y)
    // val eq  = (x, y) => None
  }

  // implicit def ringExpr[U](implicit t: Expr[Ring[U]]): Ring[Expr[U]] = new Ring {
  //   val zero = '((~t).zero)
  //   val one  = '((~t).one)
  //   val add = (x, y) => '((~t).add(~x, ~y))
  //   val sub = (x, y) => '((~t).sub(~x, ~y))
  //   val mul = (x, y) => '((~t).mul(~x, ~y))
  //   // val eq  = (x, y) => None
  // }

  implicit def ringComplex[U](implicit t: Ring[U]): Ring[Complex[U]] = new Ring {
    import t._
    val zero = Complex(t.zero, t.zero)
    val one  = Complex(t.one, t.zero)
    val add = (x, y) => Complex(x.re + y.re, x.im + y.im)
    val sub = (x, y) => Complex(x.re + y.re, x.im + y.im)
    val mul = (x, y) => Complex(x.re * y.re - x.im * y.im, x.re * y.im + x.im * y.re)
    // val eq  = (x, y) => ???
  }

  // implicit def ringComplexExpr[U : Type](implicit t: Ring[U]): Ring[Expr[Complex[U]]] = new Ring[Expr[Complex[U]]] {
  //   import t._
  //   val zero = '(Complex(t.zero, t.zero))
  //   val one  = '(Complex(t.one, t.zero))
  //   val add = (x, y) => '(Complex((~x).re + (~y).re, (~x).im + (~y).im))
  //   val sub = (x, y) => '(Complex((~x).re - (~y).re, (~x).im - (~y).im))
  //   val mul = (x, y) => '(Complex((~x).re * (~y).re - (~x).im * (~y).im, (~x).re * (~y).im + (~x).im * (~y).re))
  //   // val eq  = (x, y) => ???
  // }
}

case class Complex[T](re: T, im: T)

object Complex {
  implicit def complexIsLiftable[T: Type: Liftable]: Liftable[Complex[T]] = new Liftable {
    def toExpr(c: Complex[T]): Expr[Complex[T]] = '{ Complex(~c.re.toExpr, ~c.im.toExpr) }
  }

 def of_complex_expr(x: Expr[Complex[Int]]): Complex[Expr[Int]] = Complex('((~x).re), '((~x).im))
 def of_expr_complex(x: Complex[Expr[Int]]): Expr[Complex[Int]] = '(Complex(~x.re, ~x.im))

}

class Rings {
  {
    val intComplex = implicitly[Ring[Complex[Int]]]
    import intComplex._

    println(Complex(1, 2) * Complex(4, 2))
  }

  {
    val intExprComplex = implicitly[Ring[Complex[Expr[Int]]]]
    import intExprComplex._

    val res = Complex('(1), '(2)) * Complex('(4), '(2))
    println(s"Complex(${res.re.show}, ${res.im.show})")
  }

  // {
  //   val intExprComplex = implicitly[Ring[Expr[Complex[Int]]]]
  //   import intExprComplex._

  //   val res = '(Complex(1, 2)) * '(Complex(4, 2))
  //   println(res.show)
  // }
}



// VECTORS


case class Vec[Idx, T](size: Idx, get: Idx => T) {
  def apply(idx: Idx): T = get(idx)

  def vecMap[U](f: T => U): Vec[Idx, U] = Vec(size, i => f(get(i)))

  def zipWith[U, V](vec2: Vec[Idx, U], f: (T, U) => V): Vec[Idx, V] =
    Vec(size, i => f(get(i), vec2(i)))
}

case class OVec[Idx, T, Unt](size: Idx, update: (Idx, T) => Unt) {
  def vecAssign(vecIn: Vec[Idx, T]): Vec[Idx, Unt] =
    Vec(vecIn.size, i => update(i, vecIn(i)))
}

object Vec {
  def fromArray[T](a: Array[T]): (Vec[Int, T], OVec[Int, T, Unit]) =
    (Vec(a.size, i => a(i)), OVec(a.size, (i, v) => a(i) = v))
}


// vector ops

trait VecOp[Idx, Unt] {
  def iter(arr: Vec[Idx, Unt]): Unt
}

object VecOp {
  implicit def staticVec: VecOp[Int, Unit] = new StaticVec
  implicit def dynVec: VecOp[Expr[Int], Expr[Unit]] = new DynVec

  class StaticVec extends VecOp[Int, Unit] {
    def iter(arr: Vec[Int, Unit]): Unit = {
      for (i <- 0 until arr.size)
        arr(i)
    }
  }

  class DynVec extends VecOp[Expr[Int], Expr[Unit]] {
    def iter(arr: Vec[Expr[Int], Expr[Unit]]): Expr[Unit] = '{
      for (i <- 0 until ~arr.size)
        ~arr('(i))
    }
  }
}


trait VecROp[Idx, T, Unt] extends VecOp[Idx, Unt] {
  def reduce(add: (T, T) => T, acc: T, vec: Vec[Idx, T]): T
}

object VecROp {
  implicit def staticVec[T](implicit r: Ring[T]): VecROp[Int, T, Unit] = new StaticVecR
  implicit def dynVec[T: Type](implicit r: Ring[Expr[T]]): VecROp[Expr[Int], Expr[T], Expr[Unit]] = new DynVecR

  class StaticVecR[T](implicit r: Ring[T]) extends VecOp.StaticVec with VecROp[Int, T, Unit] {
    import r._
    def reduce(plus: (T, T) => T, acc: T, vec: Vec[Int, T]): T = {
      var sum = zero
      for (i <- 0 until vec.size)
        sum = plus(sum, vec(i))
      sum
    }
  }

  class DynVecR[T: Type](implicit r: Ring[Expr[T]]) extends VecOp.DynVec with VecROp[Expr[Int], Expr[T], Expr[Unit]] {
    def reduce(plus: (Expr[T], Expr[T]) => Expr[T], acc: Expr[T], vec: Vec[Expr[Int], Expr[T]]): Expr[T] = '{
      var sum = ~r.zero
      for (i <- 0 until ~vec.size)
        sum = ~{ plus('(sum), vec('(i))) }
      sum
    }
  }

}


// blas

class Blas1[Idx, T, Unt](implicit r: Ring[T], vec: VecOp[Idx, Unt]) {
  import r._
  import vec._

  implicit class Blas1VecOps(v1: Vec[Idx, T]) {
    def `*.`(v2: Vec[Idx, T]): Vec[Idx, T] = v1.zipWith(v2, mul)
  }

  implicit class Blas1OVecOps(vout: OVec[Idx, T, Unt]) {
    def :=(vin: Vec[Idx, T]): Unt = iter(vout.vecAssign(vin))
  }
}

class Blas2[Idx, T, Unt](implicit r: Ring[T], vec: VecROp[Idx, T, Unt]) extends Blas1[Idx, T, Unt] {
  import r._
  import vec._

  implicit class Blas2VecOps(v1: Vec[Idx, T]) {
    def dot(v2: Vec[Idx, T]): T = reduce(add, zero, v1 `*.` v2)
  }

  implicit class Blas2MatOps(a: Vec[Idx, Vec[Idx, T]]) {
    def *(v: Vec[Idx, T]): Vec[Idx, T] = a.vecMap(x => v dot x)
  }
}

// vmult

class Vmult[Idx, T, Unt](implicit r: Ring[T], vec: VecOp[Idx, Unt]) {
  private[this] val blas = new Blas1()(r, vec)
  import blas._
  def vmult(vout: OVec[Idx, T, Unt], v1: Vec[Idx, T], v2: Vec[Idx, T]): Unt = vout := v1 `*.` v2
}

object Vmults {
  def vmult(vout: Array[Complex[Int]], v1: Array[Complex[Int]], v2: Array[Complex[Int]]): Unit = {
    val n = vout.length

    val vout_ = OVec(n, (i, v: Complex[Int]) => vout(i) = v)
    val v1_ = Vec (n, i => v1(i))
    val v2_ = Vec (n, i => v2(i))

    val V = new Vmult[Int, Complex[Int], Unit]
    V.vmult(vout_, v1_, v2_)
  }

  def vmultCA: Expr[(Array[Complex[Int]], Array[Complex[Int]], Array[Complex[Int]]) => Unit] = '{
    (vout, v1, v2) => {
      val n = vout.length
      ~{
        val vout_ = OVec[Expr[Int], Complex[Expr[Int]], Expr[Unit]]('(n), (i, v) => '(vout(~i) = ~Complex.of_expr_complex(v)))
        val v1_ = Vec ('(n), i => Complex.of_complex_expr('(v1(~i))))
        val v2_ = Vec ('(n), i => Complex.of_complex_expr('(v2(~i))))

        val V = new Vmult[Expr[Int], Complex[Expr[Int]], Expr[Unit]]
        V.vmult(vout_, v1_, v2_)
      }
    }
  }
}


class MVmult[Idx, T, Unt](implicit r: Ring[T], vec: VecROp[Idx, T, Unt]) {
  private[this] val blas2 = new Blas2()(r, vec)
  import blas2._
  def mvmult(vout: OVec[Idx, T, Unt], a: Vec[Idx, Vec[Idx, T]], v: Vec[Idx, T]): Unt = vout := a * v
}

object MVmult {
  def mvmult_p(vout: Array[Int], a: Array[Array[Int]], v: Array[Int]): Unit = {
    val n = vout.length
    val m = v.length

    val vout_ = OVec(n, (i, x: Int) => vout(i) = x)
    val a_ = Vec (n, i => Vec(m, j => a(i)(j)))
    val v_ = Vec (n, i => v(i))

    val MV = new MVmult[Int, Int, Unit]
    MV.mvmult(vout_, a_, v_)
  }

  def mvmult_c: Expr[(Array[Int], Array[Array[Int]], Array[Int]) => Unit] = '{
    (vout, a, v) => {
      val n = vout.length
      val m = v.length
      ~{
        val vout_ = OVec('(n), (i, x: Expr[Int]) => '(vout(~i) = ~x))
        val a_ = Vec('(n), (i: Expr[Int]) => Vec('(m), (j: Expr[Int]) => '{ a(~i)(~j) } ))
        val v_ = Vec('(n), (i: Expr[Int]) => '(v(~i)))

        val MV = new MVmult[Expr[Int], Expr[Int], Expr[Unit]]
        MV.mvmult(vout_, a_, v_)
      }
    }
  }

  // .<fun vout a v →
  //   let n = Array.length vout and m = Array.length v in
  //   .~(let vout = OVec (.<n>.,fun i v → .<vout.(.~i) ← .~v>.) in
  //   let v = Vec (.<m>.,fun j → .<v.(.~j)>.) in
  //   let a = Vec (.<n>.,fun i →
  //   Vec (.<m>., fun j → .<a.(.~i).(.~j)>.)) in
  //   let module MV = MVMULT(RingFloatCode)(VecRDyn(RingFloatCode))
  //   in MV.mvmult vout a v)
  //   >.

}

object HMM {

  def main(args: Array[String]): Unit = {
    println("dafd")
    new Rings

    val arr1 = Array(Complex(1, 0), Complex(0, 4), Complex(2, 2))
    val arr2 = Array(Complex(2, 0), Complex(1, 1), Complex(1, 2))
    val out  = Array(Complex(0, 0), Complex(0, 0), Complex(0, 0))
    Vmults.vmult(out, arr1, arr2)
    println(out.toList)

    println(Vmults.vmultCA.show)

    val a = Array(
      Array( 5,  0,  0,  5,  0),
      Array( 0,  0, 10,  0,  0),
      Array( 0, 10,  0,  0,  0),
      Array( 0,  0,  2,  3,  5),
      Array( 0,  0,  3,  0,  7)
    )

    val v1 = Array(1, 2, 3, 4, 5)
    val v1out = Array(0, 0, 0, 0, 0)
    MVmult.mvmult_p(v1out, a, v1)
    println(v1out.toList)

    println(MVmult.mvmult_c.show)

  }
}



