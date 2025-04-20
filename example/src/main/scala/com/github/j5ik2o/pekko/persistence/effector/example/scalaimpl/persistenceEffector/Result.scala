package com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.persistenceEffector

final case class Result[S, E](
  bankAccount: S,
  event: E,
)
