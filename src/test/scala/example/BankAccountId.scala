package com.github.j5ik2o.pekko.persistence.typed.fsm
package example

import java.util.UUID

case class BankAccountId(value: UUID) {
  def aggregateTypeName: String = "BankAccount"
  def asString: String = value.toString
}
