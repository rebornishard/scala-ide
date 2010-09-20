/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.util

class Continuation[T](f : => T) {
  def apply() = f
}

object Continuation {
  implicit def emptyCont(any : Any) = new Continuation[Unit]( () => () )

  def apply[T](f : => T) = new Continuation[T](f)
  
  def apply[T](conts : Continuation[Unit]*) = new Continuation[Unit](
    conts foreach (_()) 
  )
}