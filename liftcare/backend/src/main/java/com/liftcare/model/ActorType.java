package com.liftcare.model;

/**
 * Distinguishes whether an incident history or audit action was performed
 * by an authenticated human user or generated automatically by a system process.
 */
public enum ActorType {
    USER,
    SYSTEM
}
