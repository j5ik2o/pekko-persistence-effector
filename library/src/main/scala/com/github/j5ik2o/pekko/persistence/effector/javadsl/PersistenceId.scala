package com.github.j5ik2o.pekko.persistence.effector.javadsl

import java.util.{Objects, Optional}
import scala.jdk.OptionConverters.*
import scala.compiletime.uninitialized

/**
 * Represents a unique identifier for persistent actors.
 * 
 * This is a Java API wrapper around the Scala PersistenceId implementation.
 */
final class PersistenceId private (
  private val underlying: com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistenceId
) {
  Objects.requireNonNull(underlying, "underlying cannot be null")
  
  /**
   * Gets the complete persistence identifier string.
   * 
   * @return The complete persistence identifier
   */
  def getId: String = underlying.asString
  
  /**
   * Gets the entity type hint.
   * 
   * @return The entity type hint (null if not present)
   */
  def getEntityTypeHint: String = underlying.entityTypeHint.orNull
  
  /**
   * Gets the entity id.
   * 
   * @return The entity identifier
   */
  def getEntityId: String = underlying.entityId
  
  /**
   * Gets the separator used.
   * 
   * @return The separator
   */
  def getSeparator: String = underlying.separator
  
  /**
   * Checks if this persistence id contains a type hint.
   * 
   * @return true if entityTypeHint is present, false otherwise
   */
  def hasTypeHint: Boolean = underlying.hasTypeHint
  
  /**
   * Gets the entity type hint as Optional.
   * 
   * @return Optional containing the entity type hint if present
   */
  def getEntityTypeHintOptional: Optional[String] = underlying.entityTypeHint.toJava
  
  /**
   * Gets the underlying Scala PersistenceId.
   * 
   * @return The underlying Scala implementation
   */
  def toScala: com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistenceId = underlying
  
  override def equals(obj: Any): Boolean = {
    import scala.compiletime.asMatchable
    obj.asMatchable match {
      case other: PersistenceId => Objects.equals(underlying, other.underlying)
      case _ => false
    }
  }
  
  override def hashCode(): Int = underlying.hashCode()
  
  override def toString: String = s"PersistenceId(${getId})"
}

object PersistenceId {
  /**
   * The default separator used between entity type hint and entity id.
   * This follows the same convention as Pekko's PersistenceId.
   */
  val DEFAULT_SEPARATOR: String = com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistenceId.DefaultSeparator
  
  /**
   * Creates a PersistenceId from a complete unique identifier string.
   * 
   * @param id The complete persistence identifier
   * @return A new PersistenceId instance
   */
  def ofUniqueId(id: String): PersistenceId = 
    new PersistenceId(com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistenceId.ofUniqueId(id))
  
  /**
   * Creates a PersistenceId by concatenating entityTypeHint and entityId with the default separator.
   * 
   * @param entityTypeHint The entity type hint
   * @param entityId The entity identifier
   * @return A new PersistenceId instance
   */
  def of(entityTypeHint: String, entityId: String): PersistenceId = 
    new PersistenceId(com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistenceId.of(entityTypeHint, entityId))
  
  /**
   * Creates a PersistenceId by concatenating entityTypeHint and entityId with a custom separator.
   * 
   * @param entityTypeHint The entity type hint
   * @param entityId The entity identifier
   * @param separator The custom separator to use
   * @return A new PersistenceId instance
   */
  def of(entityTypeHint: String, entityId: String, separator: String): PersistenceId = 
    new PersistenceId(com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistenceId.of(entityTypeHint, entityId, separator))
  
  /**
   * Creates a PersistenceId from a Scala PersistenceId.
   * 
   * @param scalaPersistenceId The Scala PersistenceId
   * @return A new Java PersistenceId instance
   */
  def fromScala(scalaPersistenceId: com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistenceId): PersistenceId = 
    new PersistenceId(scalaPersistenceId)
  
  /**
   * Extracts the entity type hint from a persistence id string.
   * 
   * @param persistenceId The persistence id string
   * @return The entity type hint if found, empty string otherwise
   */
  def extractEntityTypeHint(persistenceId: String): String = 
    com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistenceId.extractEntityTypeHint(persistenceId)
  
  /**
   * Extracts the entity id from a persistence id string.
   * 
   * @param persistenceId The persistence id string
   * @return The entity id
   */
  def extractEntityId(persistenceId: String): String = 
    com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistenceId.extractEntityId(persistenceId)
  
  /**
   * Creates a builder for constructing PersistenceId instances.
   * 
   * @return A new Builder instance
   */
  def builder(): Builder = new Builder()
  
  /**
   * Builder for PersistenceId instances.
   */
  final class Builder {
    private var entityTypeHint: String = uninitialized
    private var entityId: String = uninitialized
    private var separator: String = DEFAULT_SEPARATOR
    private var uniqueId: String = uninitialized
    
    /**
     * Sets the entity type hint.
     * 
     * @param entityTypeHint The entity type hint
     * @return This builder
     */
    def withEntityTypeHint(entityTypeHint: String): Builder = {
      this.entityTypeHint = entityTypeHint
      this
    }
    
    /**
     * Sets the entity id.
     * 
     * @param entityId The entity id
     * @return This builder
     */
    def withEntityId(entityId: String): Builder = {
      this.entityId = entityId
      this
    }
    
    /**
     * Sets a custom separator.
     * 
     * @param separator The custom separator
     * @return This builder
     */
    def withSeparator(separator: String): Builder = {
      this.separator = separator
      this
    }
    
    /**
     * Sets a complete unique id directly.
     * 
     * @param uniqueId The complete unique id
     * @return This builder
     */
    def withUniqueId(uniqueId: String): Builder = {
      this.uniqueId = uniqueId
      this
    }
    
    /**
     * Builds the PersistenceId instance.
     * 
     * @return A new PersistenceId instance
     */
    def build(): PersistenceId = {
      if (uniqueId != null) {
        PersistenceId.ofUniqueId(uniqueId)
      } else if (entityTypeHint != null && entityId != null) {
        if (DEFAULT_SEPARATOR == separator) {
          PersistenceId.of(entityTypeHint, entityId)
        } else {
          PersistenceId.of(entityTypeHint, entityId, separator)
        }
      } else if (entityId != null) {
        // No type hint case
        new PersistenceId(
          new com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistenceId(
            None,
            entityId,
            separator
          )
        )
      } else {
        throw new IllegalStateException("Either uniqueId or entityId must be set")
      }
    }
  }
}