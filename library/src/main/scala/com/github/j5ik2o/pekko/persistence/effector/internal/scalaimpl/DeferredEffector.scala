package com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl

import com.github.j5ik2o.pekko.persistence.effector.scaladsl.{
  PersistenceEffector,
  PersistenceEffectorConfig,
}
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.ActorContext

private[effector] class DeferredEffector[S, E, M](
  ctx: ActorContext[M],
  config: PersistenceEffectorConfig[S, E, M],
) extends PersistenceEffector[S, E, M] {

  def getState: S = config.initialState

  override def persistEvent(event: E)(onPersisted: E => Behavior[M]): Behavior[M] =
    onPersisted(event)

  override def persistEvents(events: Seq[E])(onPersisted: Seq[E] => Behavior[M]): Behavior[M] =
    onPersisted(events)

  override def persistSnapshot(snapshot: S, force: Boolean)(
    onPersisted: S => Behavior[M]): Behavior[M] =
    onPersisted(snapshot)

  override def persistEventWithSnapshot(event: E, snapshot: S, forceSnapshot: Boolean)(
    onPersisted: E => Behavior[M]): Behavior[M] =
    persistEvent(event)(onPersisted)

  override def persistEventsWithSnapshot(events: Seq[E], snapshot: S, forceSnapshot: Boolean)(
    onPersisted: Seq[E] => Behavior[M]): Behavior[M] =
    persistEvents(events)(onPersisted)
}
