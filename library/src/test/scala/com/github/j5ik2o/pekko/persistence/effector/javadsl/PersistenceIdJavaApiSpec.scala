package com.github.j5ik2o.pekko.persistence.effector.javadsl

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PersistenceIdJavaApiSpec extends AnyWordSpec with Matchers {

  "Java PersistenceId" should {

    "create from unique id" in {
      val id = "test-persistence-id"
      val persistenceId = PersistenceId.ofUniqueId(id)

      persistenceId.getId shouldBe id
      persistenceId.getEntityTypeHint shouldBe null
      persistenceId.getEntityId shouldBe id
      persistenceId.hasTypeHint shouldBe false
    }

    "create from entity type and entity id with default separator" in {
      val entityType = "BankAccount"
      val entityId = "12345"
      val persistenceId = PersistenceId.of(entityType, entityId)

      persistenceId.getId shouldBe s"$entityType|$entityId"
      persistenceId.getEntityTypeHint shouldBe entityType
      persistenceId.getEntityId shouldBe entityId
      persistenceId.hasTypeHint shouldBe true
    }

    "create from entity type and entity id with custom separator" in {
      val entityType = "BankAccount"
      val entityId = "12345"
      val separator = "-"
      val persistenceId = PersistenceId.of(entityType, entityId, separator)

      persistenceId.getId shouldBe s"$entityType$separator$entityId"
      persistenceId.getEntityTypeHint shouldBe entityType
      persistenceId.getEntityId shouldBe entityId
      persistenceId.getSeparator shouldBe separator
      persistenceId.hasTypeHint shouldBe true
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

      persistenceId.toString shouldBe s"PersistenceId($id)"
    }

    "work with builder" in {
      // Test with uniqueId
      val pid1 = PersistenceId
        .builder()
        .withUniqueId("test-id")
        .build()
      pid1.getId shouldBe "test-id"

      // Test with entityTypeHint and entityId
      val pid2 = PersistenceId
        .builder()
        .withEntityTypeHint("BankAccount")
        .withEntityId("12345")
        .build()
      pid2.getId shouldBe "BankAccount|12345"

      // Test with custom separator
      val pid3 = PersistenceId
        .builder()
        .withEntityTypeHint("BankAccount")
        .withEntityId("12345")
        .withSeparator("-")
        .build()
      pid3.getId shouldBe "BankAccount-12345"

      // Test with entityId only (no type hint)
      val pid4 = PersistenceId
        .builder()
        .withEntityId("simple-id")
        .build()
      pid4.getId shouldBe "simple-id"
      pid4.getEntityTypeHint shouldBe null
    }

    "fail builder without required fields" in
      assertThrows[IllegalStateException] {
        PersistenceId.builder().build()
      }

    "provide entity type hint as Optional" in {
      // With type hint
      val pid1 = PersistenceId.of("BankAccount", "12345")
      val hint1 = pid1.getEntityTypeHintOptional
      hint1.isPresent shouldBe true
      hint1.get shouldBe "BankAccount"

      // Without type hint
      val pid2 = PersistenceId.ofUniqueId("simple-id")
      val hint2 = pid2.getEntityTypeHintOptional
      hint2.isPresent shouldBe false
    }

    "convert from Scala" in {
      val scalaPid = com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistenceId.of("BankAccount", "12345")

      val javaPid = PersistenceId.fromScala(scalaPid)
      javaPid.getId shouldBe "BankAccount|12345"
      javaPid.getEntityTypeHint shouldBe "BankAccount"
      javaPid.getEntityId shouldBe "12345"
    }

    "convert to Scala" in {
      val javaPid = PersistenceId.of("BankAccount", "12345")
      val scalaPid = javaPid.toScala

      scalaPid.asString shouldBe "BankAccount|12345"
      scalaPid.entityTypeHint.isDefined shouldBe true
      scalaPid.entityTypeHint.get shouldBe "BankAccount"
      scalaPid.entityId shouldBe "12345"
    }
  }
}
