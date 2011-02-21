/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.util.{ Map => JMap }

import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jdt.internal.core.Openable
import org.eclipse.jface.text.{ Position => JFacePosition }
import org.eclipse.jface.text.source.Annotation

import scala.tools.eclipse.contribution.weaving.jdt.IScalaOverrideIndicator

import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility

import scala.tools.eclipse.ScalaPresentationCompiler

trait ScalaOverrideIndicatorBuilder { self : ScalaPresentationCompiler =>
  class OverrideIndicatorBuilderTraverser(scu : ScalaCompilationUnit, annotationMap : JMap[AnyRef, AnyRef]) extends Traverser {
    val ANNOTATION_TYPE= "org.eclipse.jdt.ui.overrideIndicator"

    case class ScalaIndicator(text : String, file : Openable, pos : Int, val isOverwrite : Boolean) extends Annotation(ANNOTATION_TYPE, false, text) 
    with IScalaOverrideIndicator {
      def open = {
        EditorUtility.openInEditor(file, true) match { 
          case editor : ITextEditor => editor.selectAndReveal(pos, 0)
          case _ =>
        }
      }
    }

    case class JavaIndicator(
      packageName : String,
      typeNames : String,
      methodName : String,
      methodTypeSignatures : List[String],
      text : String,
      val isOverwrite : Boolean
    ) extends Annotation(ANNOTATION_TYPE, false, text) with IScalaOverrideIndicator {
      def open() {
        val tpe0 = JDTUtils.resolveType(scu.newSearchableEnvironment().nameLookup, packageName, typeNames, 0)
        tpe0 match {
          case Some(tpe) =>
            val method = tpe.getMethod(methodName, methodTypeSignatures.toArray)
            if (method.exists)
              JavaUI.openInEditor(method, true, true);
          case _ =>
        }
     }
    }
    
    override def traverse(tree: Tree): Unit = {
      tree match {
        case defn: DefTree if (defn.symbol ne NoSymbol) && defn.symbol.pos.isOpaqueRange =>
          for(base <- defn.symbol.allOverriddenSymbols) {
            val isOverwrite = base.isDeferred && !defn.symbol.isDeferred
            val text = (if (isOverwrite) "implements " else "overrides ") + base.fullName
            val position = new JFacePosition(defn.pos.startOrPoint, 0)

            if (base.isJavaDefined) {
              val packageName = base.enclosingPackage.fullName
              val typeNames = enclosingTypeNames(base).mkString(".")
              val methodName = base.name.toString
              val paramTypes = base.tpe.paramss.flatMap(_.map(_.tpe))
              val methodTypeSignatures = paramTypes.map(mapParamTypeSignature(_))
              annotationMap.put(JavaIndicator(packageName, typeNames, methodName, methodTypeSignatures, text, isOverwrite), position)
            } else locate(base, scu) map {
              case (f, pos) =>  annotationMap.put(ScalaIndicator(text, f, pos, isOverwrite), position)
            }
          }
        case _ =>
      }
  
      super.traverse(tree)
    }
  }
}