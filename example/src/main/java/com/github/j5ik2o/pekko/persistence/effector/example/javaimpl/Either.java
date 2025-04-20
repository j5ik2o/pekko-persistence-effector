package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl;

import java.util.Optional;
import java.util.function.Function;

/**
 * A simple implementation of Either type
 *
 * @param <L> Left type
 * @param <R> Right type
 */
public class Either<L, R> {
    private final Optional<L> left;
    private final Optional<R> right;

    private Either(Optional<L> left, Optional<R> right) {
        this.left = left;
        this.right = right;
    }

    /**
     * Create a left Either
     *
     * @param value Left value
     * @param <L> Left type
     * @param <R> Right type
     * @return Left Either
     */
    public static <L, R> Either<L, R> left(L value) {
        return new Either<>(Optional.of(value), Optional.empty());
    }

    /**
     * Create a right Either
     *
     * @param value Right value
     * @param <L> Left type
     * @param <R> Right type
     * @return Right Either
     */
    public static <L, R> Either<L, R> right(R value) {
        return new Either<>(Optional.empty(), Optional.of(value));
    }

    /**
     * Check if this is a left Either
     *
     * @return true if left, false otherwise
     */
    public boolean isLeft() {
        return left.isPresent();
    }

    /**
     * Check if this is a right Either
     *
     * @return true if right, false otherwise
     */
    public boolean isRight() {
        return right.isPresent();
    }

    /**
     * Get the left value
     *
     * @return Left value
     * @throws IllegalStateException if this is not a left Either
     */
    public L getLeft() {
        return left.orElseThrow(() -> new IllegalStateException("Either.left is empty"));
    }

    /**
     * Get the right value
     *
     * @return Right value
     * @throws IllegalStateException if this is not a right Either
     */
    public R getRight() {
        return right.orElseThrow(() -> new IllegalStateException("Either.right is empty"));
    }

    /**
     * Apply a function to either the left or right value
     *
     * @param leftMapper Function to apply to left value
     * @param rightMapper Function to apply to right value
     * @param <T> Result type
     * @return Result of applying the appropriate function
     */
    public <T> T fold(Function<L, T> leftMapper, Function<R, T> rightMapper) {
        if (isLeft()) {
            return leftMapper.apply(getLeft());
        } else {
            return rightMapper.apply(getRight());
        }
    }
}
