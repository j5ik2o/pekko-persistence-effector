package com.github.j5ik2o.pekko.persistence.effector.scaladsl

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PersistenceIdSpec extends AnyWordSpec with Matchers {

  "PersistenceId" should {

    "create from unique id" in {
      val id = "test-persistence-id"
      val persistenceId = PersistenceId.ofUniqueId(id)

      persistenceId.asString shouldBe id
      persistenceId.entityTypeHint shouldBe None
      persistenceId.entityId shouldBe id
      persistenceId.hasTypeHint shouldBe false
    }

    "create from entity type and entity id with default separator" in {
      val entityType = "BankAccount"
      val entityId = "12345"
      val persistenceId = PersistenceId.of(entityType, entityId)

      persistenceId.asString shouldBe s"$entityType|$entityId"
      persistenceId.entityTypeHint shouldBe Some(entityType)
      persistenceId.entityId shouldBe entityId
      persistenceId.hasTypeHint shouldBe true
    }

    "create from entity type and entity id with custom separator" in {
      val entityType = "BankAccount"
      val entityId = "12345"
      val separator = "-"
      val persistenceId = PersistenceId.of(entityType, entityId, separator)

      persistenceId.asString shouldBe s"$entityType$separator$entityId"
      // Note: entityTypeHint and entityId methods use default separator, so they won't work correctly
      // This is consistent with Pekko's behavior
    }

    "reject empty id" in
      assertThrows[IllegalArgumentException] {
        PersistenceId.ofUniqueId("")
      }

    "reject empty entity type" in
      assertThrows[IllegalArgumentException] {
        PersistenceId.of("", "entity-id")
      }

    "reject empty entity id" in
      assertThrows[IllegalArgumentException] {
        PersistenceId.of("entity-type", "")
      }

    "reject entity type containing default separator" in
      assertThrows[IllegalArgumentException] {
        PersistenceId.of("entity|type", "entity-id")
      }

    "reject entity id containing default separator" in
      assertThrows[IllegalArgumentException] {
        PersistenceId.of("entity-type", "entity|id")
      }

    "reject entity type containing custom separator" in
      assertThrows[IllegalArgumentException] {
        PersistenceId.of("entity-type", "entity-id", "-")
      }

    "extract entity type hint from string" in {
      val persistenceIdString = "BankAccount|12345"
      PersistenceId.extractEntityTypeHint(persistenceIdString) shouldBe "BankAccount"
    }

    "extract entity id from string" in {
      val persistenceIdString = "BankAccount|12345"
      PersistenceId.extractEntityId(persistenceIdString) shouldBe "12345"
    }

    "return empty string when extracting type hint from string without separator" in {
      val persistenceIdString = "simple-id"
      PersistenceId.extractEntityTypeHint(persistenceIdString) shouldBe ""
    }

    "return full string when extracting entity id from string without separator" in {
      val persistenceIdString = "simple-id"
      PersistenceId.extractEntityId(persistenceIdString) shouldBe "simple-id"
    }

    "handle multiple separators correctly" in {
      val entityType = "BankAccount"
      val entityId = "12345|extra|parts"
      // This should fail because entityId contains separator
      assertThrows[IllegalArgumentException] {
        PersistenceId.of(entityType, entityId)
      }
    }

    "be equal for same id" in {
      val id = "test-id"
      val persistenceId1 = PersistenceId.ofUniqueId(id)
      val persistenceId2 = PersistenceId.ofUniqueId(id)

      persistenceId1 shouldBe persistenceId2
      persistenceId1.hashCode shouldBe persistenceId2.hashCode
    }

    "not be equal for different ids" in {
      val persistenceId1 = PersistenceId.ofUniqueId("id1")
      val persistenceId2 = PersistenceId.ofUniqueId("id2")

      persistenceId1 should not be persistenceId2
    }

    "have proper toString representation" in {
      val id = "test-id"
      val persistenceId = PersistenceId.ofUniqueId(id)

      persistenceId.toString shouldBe s"PersistenceId(None,$id,|)"
    }
  }
}
