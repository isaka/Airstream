package com.raquo.airstream.split

import com.raquo.airstream.core.{
  EventStream,
  Signal,
  Observable,
  BaseObservable
}
import scala.quoted.{Expr, Quotes, Type}
import scala.annotation.{unused, targetName}
import scala.compiletime.summonInline

/** `SplitMatchOneMacros` turns this code
  *
  * ```scala
  * sealed trait Foo
  * final case class Bar(strOpt: Option[String]) extends Foo
  * enum Baz extends Foo {
  *   case Baz1, Baz2
  * }
  * case object Tar extends Foo
  * val splitter = fooSignal
  *   .splitMatchOne
  *   .handleCase { case Bar(Some(str)) => str } { (str, strSignal) =>
  *     renderStrNode(str, strSignal)
  *   }
  *   .handleCase { case baz: Baz => baz } { (baz, bazSignal) =>
  *     renderBazNode(baz, bazSignal)
  *   }
  *   .handleCase {
  *     case Tar    => ()
  *     case _: Int => ()
  *   } { (_, _) => div("Taz") }
  *   .toSignal
  * ```
  *
  * into this code:
  *
  * ```scala
  * val splitter = fooSignal.
  *  .map { i =>
  *    i match {
  *      case Bar(Some(str)) => (0, str)
  *      case baz: Baz => (1, baz)
  *      case Tar => (2, ())
  *      case _: Int => (2, ())
  *    }
  *  }
  *  .splitOne(_._1) { ??? }
  * ```
  *
  * After macros expansion, compiler will warns above code "match may not be
  * exhaustive" and "unreachable case" as expected.
  */
object SplitMatchOneMacros {

  extension [Self[+_] <: Observable[_], I](inline observable: BaseObservable[Self, I]) {
    inline def splitMatchOne: SplitMatchOneObservable[Self, I, Nothing] =
      SplitMatchOneObservable.build(
        observable,
        Nil,
        Map.empty[Int, Function2[Any, Any, Nothing]]
      )
  }

  extension [Self[+_] <: Observable[_], I, O](
    inline matchSplitObservable: SplitMatchOneObservable[Self, I, O]
  ) {
    inline def handleCase[A, B, O1 >: O](inline casePf: PartialFunction[A, B])(inline handleFn: (B, Signal[B]) => O1) = ${
      handleCaseImpl('{ matchSplitObservable }, '{ casePf }, '{ handleFn })
    }

    inline private def handlePfType[T](inline casePf: PartialFunction[Any, T]) = ${
      handleTypeImpl[Self, I, O, T]('{ matchSplitObservable }, '{ casePf })
    }

    inline def handleType[T]: SplitMatchOneTypeObservable[Self, I, O, T] = handlePfType[T] { case t: T => t }

    inline private def handlePfValue[V](inline casePf: PartialFunction[Any, V]) = ${
      handleValueImpl[Self, I, O, V]('{ matchSplitObservable }, '{ casePf })
    }

    inline def handleValue[V](inline v: V)(using inline valueOf: ValueOf[V]): SplitMatchOneValueObservable[Self, I, O, V] = handlePfValue[V] { case _: V => v }
  }

  extension [Self[+_] <: Observable[_], I, O, T](inline matchTypeObserver: SplitMatchOneTypeObservable[Self, I, O, T]) {
    inline def apply[O1 >: O](inline handleFn: (T, Signal[T]) => O1): SplitMatchOneObservable[Self, I, O1] = ${
      handleTypeApplyImpl('{ matchTypeObserver }, '{ handleFn })
    }
  }

  extension [Self[+_] <: Observable[_], I, O, V](inline matchValueObservable: SplitMatchOneValueObservable[Self, I, O, V]) {
    inline private def deglate[O1 >: O](inline handleFn: (V, Signal[V]) => O1) = ${
      handleValueApplyImpl('{ matchValueObservable }, '{ handleFn })
    }

    inline def apply[O1 >: O](inline handleFn: Signal[V] => O1): SplitMatchOneObservable[Self, I, O1] = deglate { (_, vSignal) => handleFn(vSignal) }
  }

  extension [I, O](inline matchSplitObservable: SplitMatchOneObservable[Signal, I, O]) {
    inline def toSignal: Signal[O] = ${
      observableImpl('{ matchSplitObservable })
    }
  }

  extension [I, O](inline matchSplitObservable: SplitMatchOneObservable[EventStream, I, O]) {
    inline def toStream: EventStream[O] = ${
      observableImpl('{ matchSplitObservable })
    }
  }

  private def handleCaseImpl[Self[+_] <: Observable[_]: Type, I: Type, O: Type, O1 >: O: Type, A: Type, B: Type](
    matchSplitObservableExpr: Expr[SplitMatchOneObservable[Self, I, O]],
    casePfExpr: Expr[PartialFunction[A, B]],
    handleFnExpr: Expr[Function2[B, Signal[B], O1]]
  )(
    using quotes: Quotes
  ): Expr[SplitMatchOneObservable[Self, I, O1]] = {
    import quotes.reflect.*

    matchSplitObservableExpr match {
      case '{
            SplitMatchOneObservable.build[Self, I, O](
              $observableExpr,
              $caseListExpr,
              $handlerMapExpr
            )
          } =>
        innerHandleCaseImpl(
          observableExpr,
          caseListExpr,
          handlerMapExpr,
          casePfExpr,
          handleFnExpr
        )
      case other =>
        report.errorAndAbort(
          "Macro expansion failed, please use `splitMatchOne` instead of creating new SplitMatchOneObservable explicitly"
        )
    }
  }

  private def handleTypeImpl[Self[+_] <: Observable[_]: Type, I: Type, O: Type, T: Type](
    matchSplitObservableExpr: Expr[SplitMatchOneObservable[Self, I, O]],
    casePfExpr: Expr[PartialFunction[T, T]]
  )(
    using quotes: Quotes
  ): Expr[SplitMatchOneTypeObservable[Self, I, O, T]] = {
    import quotes.reflect.*

    matchSplitObservableExpr match {
      case '{
            SplitMatchOneObservable.build[Self, I, O](
              $observableExpr,
              $caseListExpr,
              $handlerMapExpr
            )
          } =>
        '{
          SplitMatchOneTypeObservable.build[Self, I, O, T](
            $observableExpr,
            $caseListExpr,
            $handlerMapExpr,
            $casePfExpr
          )
        }
      case other =>
        report.errorAndAbort(
          "Macro expansion failed, please use `splitMatchOne` instead of creating new SplitMatchOneObservable explicitly"
        )
    }
  }

  private def handleTypeApplyImpl[Self[+_] <: Observable[_]: Type, I: Type, O: Type, O1 >: O: Type, T: Type](
    matchSplitObservableExpr: Expr[SplitMatchOneTypeObservable[Self, I, O, T]],
    handleFnExpr: Expr[Function2[T, Signal[T], O1]]
  )(
    using quotes: Quotes
  ): Expr[SplitMatchOneObservable[Self, I, O1]] = {
    import quotes.reflect.*

    matchSplitObservableExpr match {
      case '{
            SplitMatchOneTypeObservable.build[Self, I, O, T](
              $observableExpr,
              $caseListExpr,
              $handlerMapExpr,
              $tCaseExpr
            )
          } =>
        innerHandleCaseImpl[Self, I, O, O1, T, T](
          observableExpr,
          caseListExpr,
          handlerMapExpr,
          tCaseExpr,
          handleFnExpr
        )
      case other =>
        report.errorAndAbort(
          "Macro expansion failed, please use `splitMatchOne` instead of creating new SplitMatchOneObservable explicitly"
        )
    }
  }

  private def handleValueImpl[Self[+_] <: Observable[_]: Type, I: Type, O: Type, V: Type](
    matchSplitObservableExpr: Expr[SplitMatchOneObservable[Self, I, O]],
    casePfExpr: Expr[PartialFunction[V, V]]
  )(
    using quotes: Quotes
  ): Expr[SplitMatchOneValueObservable[Self, I, O, V]] = {
    import quotes.reflect.*

    matchSplitObservableExpr match {
      case '{
            SplitMatchOneObservable.build[Self, I, O](
              $observableExpr,
              $caseListExpr,
              $handlerMapExpr
            )
          } =>
        '{
          SplitMatchOneValueObservable.build[Self, I, O, V](
            $observableExpr,
            $caseListExpr,
            $handlerMapExpr,
            $casePfExpr
          )
        }
      case other =>
        report.errorAndAbort(
          "Macro expansion failed, please use `splitMatchOne` instead of creating new SplitMatchOneObservable explicitly"
        )
    }
  }

  private def handleValueApplyImpl[Self[+_] <: Observable[_]: Type, I: Type, O: Type, O1 >: O: Type, V: Type](
    matchValueObservableExpr: Expr[SplitMatchOneValueObservable[Self, I, O, V]],
    handleFnExpr: Expr[Function2[V, Signal[V], O1]]
  )(
    using quotes: Quotes
  ): Expr[SplitMatchOneObservable[Self, I, O1]] = {
    import quotes.reflect.*

    matchValueObservableExpr match {
      case '{
            SplitMatchOneValueObservable.build[Self, I, O, V](
              $observableExpr,
              $caseListExpr,
              $handlerMapExpr,
              $tCaseExpr
            )
          } =>
        innerHandleCaseImpl[Self, I, O, O1, V, V](
          observableExpr,
          caseListExpr,
          handlerMapExpr,
          tCaseExpr,
          handleFnExpr
        )
      case other =>
        report.errorAndAbort(
          "Macro expansion failed, please use `splitMatchOne` instead of creating new SplitMatchOneObservable explicitly"
        )
    }
  }

  private def innerHandleCaseImpl[Self[+_] <: Observable[_]: Type, I: Type, O: Type, O1 >: O: Type, A: Type, B: Type](
    observableExpr: Expr[BaseObservable[Self, I]],
    caseListExpr: Expr[List[PartialFunction[Any, Any]]],
    handlerMapExpr: Expr[Map[Int, Function2[Any, Any, O]]],
    casePfExpr: Expr[PartialFunction[A, B]],
    handleFnExpr: Expr[Function2[B, Signal[B], O1]]
  )(
    using quotes: Quotes
  ): Expr[SplitMatchOneObservable[Self, I, O1]] = {
    import quotes.reflect.*

    val caseExprList = MacrosUtilities.exprOfListToListOfExpr(caseListExpr)

    val nextCaseExprList =
      casePfExpr.asExprOf[PartialFunction[Any, Any]] :: caseExprList

    val nextCaseListExpr = MacrosUtilities.listOfExprToExprOfList(nextCaseExprList)

    '{
      SplitMatchOneObservable.build[Self, I, O1](
        $observableExpr,
        $nextCaseListExpr,
        ($handlerMapExpr + ($handlerMapExpr.size -> $handleFnExpr
          .asInstanceOf[Function2[Any, Any, O1]]))
      )
    }
  }

  private inline def toSplittableOneObservable[Self[+_] <: Observable[_], O](
    parentObservable: BaseObservable[Self, (Int, Any)],
    handlerMap: Map[Int, Function2[Any, Any, O]]
  ): Self[O] = {
    parentObservable
      .matchStreamOrSignal(
        ifStream = _.splitOne(_._1) { case (idx, (_, b), dataSignal) =>
          val bSignal = dataSignal.map(_._2)
          handlerMap.apply(idx).apply(b, bSignal)
        },
        ifSignal = _.splitOne(_._1) { case (idx, (_, b), dataSignal) =>
          val bSignal = dataSignal.map(_._2)
          handlerMap.apply(idx).apply(b, bSignal)
        }
      )
      .asInstanceOf[Self[O]] // #TODO[Integrity] Same as FlatMap/AsyncStatusObservable, how to type this properly?
  }

  private def observableImpl[Self[+_] <: Observable[_]: Type, I: Type, O: Type](
    matchSplitObservableExpr: Expr[SplitMatchOneObservable[Self, I, O]]
  )(
    using quotes: Quotes
  ): Expr[Self[O]] = {
    import quotes.reflect.*

    matchSplitObservableExpr match {
      case '{ SplitMatchOneObservable.build[Self, I, O]($_, Nil, $_) } =>
        report.errorAndAbort(
          "Macro expansion failed, need at least one handleCase"
        )
      case '{
            SplitMatchOneObservable.build[Self, I, O](
              $observableExpr,
              $caseListExpr,
              $handlerMapExpr
            )
          } =>
        '{
          toSplittableOneObservable(
            $observableExpr
              .map(i => ${ MacrosUtilities.innerObservableImpl('i, caseListExpr) })
              .asInstanceOf[BaseObservable[Self, (Int, Any)]],
            $handlerMapExpr
          )
        }
      case _ =>
        report.errorAndAbort(
          "Macro expansion failed, please use `splitMatchOne` instead of creating new SplitMatchOneObservable explicitly"
        )
    }
  }

}
