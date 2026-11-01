package com.liftcare.model;

/**
 * Compliance state of an incident's Response or Resolution SLA clock.
 */
public enum SlaState {
    /** Target deadline is in the future and time remaining exceeds the warning threshold. */
    ON_TRACK,

    /** Target deadline is approaching within the configured warning threshold. */
    APPROACHING_DEADLINE,

    /** Target deadline has passed without satisfying the required lifecycle milestone. */
    BREACHED,

    /** Milestone was achieved on or before the target deadline. */
    MET,

    /** The SLA clock is not applicable for the current incident lifecycle state. */
    NOT_APPLICABLE,

    /** No active SLA policy exists or could be resolved for this incident. */
    POLICY_MISSING
}
