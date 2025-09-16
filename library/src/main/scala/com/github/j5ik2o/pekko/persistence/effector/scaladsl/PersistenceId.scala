package com.github.j5ik2o.pekko.persistence.effector.scaladsl

/**
 * Represents a unique identifier for persistent actors.
 *
 * This is a library-specific implementation that provides similar functionality to Pekko's PersistenceId but without
 * depending on pekko-persistence-typed.
 *
 * @param entityTypeHint
 *   The optional entity type hint
 * @param entityId
 *   The entity identifier
 * @param separator
 *   The separator used (defaults to DefaultSeparator)
 */
final case class PersistenceId private[effector] (
  entityTypeHint: Option[String],
  entityId: String,
  separator: String = PersistenceId.DefaultSeparator,
) {
  require(entityId.nonEmpty, "entityId cannot be empty")
  require(separator.nonEmpty, "separator cannot be empty")
  require(!entityTypeHint.exists(_.isEmpty), "entityTypeHint cannot be empty if present")
  require(!entityTypeHint.exists(_.contains(separator)), s"entityTypeHint cannot contain separator '$separator'")
  require(!entityId.contains(separator), s"entityId cannot contain separator '$separator'")

  /**
   * Gets the complete persistence identifier string.
   *
   * @return
   *   The complete persistence identifier
   */
  def asString: String = entityTypeHint match {
    case None => entityId
    case Some(hint) => s"$hint$separator$entityId"
  }

  /**
   * Checks if this persistence id contains a type hint.
   *
   * @return
   *   true if entityTypeHint is present, false otherwise
   */
  def hasTypeHint: Boolean = entityTypeHint.isDefined
}

object PersistenceId {

  /**
   * The default separator used between entity type hint and entity id. This follows the same convention as Pekko's
   * PersistenceId.
   */
  val DefaultSeparator: String = "|"

  /**
   * Creates a PersistenceId from a complete unique identifier string.
   *
   * @param id
   *   The complete persistence identifier
   * @return
   *   A new PersistenceId instance
   */
  def ofUniqueId(id: String): PersistenceId = {
    require(id.nonEmpty, "id cannot be empty")
    val idx = id.indexOf(DefaultSeparator)
    if (idx == -1) {
      PersistenceId(entityTypeHint = None, entityId = id)
    } else {
      PersistenceId(
        entityTypeHint = Some(id.substring(0, idx)),
        entityId = id.substring(idx + 1),
      )
    }
  }

  /**
   * Creates a PersistenceId by concatenating entityTypeHint and entityId with the default separator.
   *
   * @param entityTypeHint
   *   The entity type hint
   * @param entityId
   *   The entity identifier
   * @return
   *   A new PersistenceId instance
   */
  def of(entityTypeHint: String, entityId: String): PersistenceId = {
    require(entityTypeHint.nonEmpty, "entityTypeHint cannot be empty")
    PersistenceId(
      entityTypeHint = Some(entityTypeHint),
      entityId = entityId,
    )
  }

  /**
   * Creates a PersistenceId by concatenating entityTypeHint and entityId with a custom separator.
   *
   * @param entityTypeHint
   *   The entity type hint
   * @param entityId
   *   The entity identifier
   * @param separator
   *   The custom separator to use
   * @return
   *   A new PersistenceId instance
   */
  def of(entityTypeHint: String, entityId: String, separator: String): PersistenceId = {
    require(entityTypeHint.nonEmpty, "entityTypeHint cannot be empty")
    PersistenceId(
      entityTypeHint = Some(entityTypeHint),
      entityId = entityId,
      separator = separator,
    )
  }

  /**
   * Extracts the entity type hint from a persistence id string.
   *
   * @param persistenceId
   *   The persistence id string
   * @return
   *   The entity type hint if found, empty string otherwise
   */
  def extractEntityTypeHint(persistenceId: String): String =
    ofUniqueId(persistenceId).entityTypeHint.getOrElse("")

  /**
   * Extracts the entity id from a persistence id string.
   *
   * @param persistenceId
   *   The persistence id string
   * @return
   *   The entity id if found, the full string otherwise
   */
  def extractEntityId(persistenceId: String): String =
    ofUniqueId(persistenceId).entityId
}
