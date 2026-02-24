package net.herotale.herocore.api.leveling;

/**
 * Controls how XP adjustments are transformed for removal paths.
 */
public enum XPAdjustmentPolicy {
    /**
     * Apply configured source weight only (no gain multiplier stat).
     */
    APPLY_SOURCE_WEIGHT_ONLY,

    /**
     * Apply no adjustment; use raw amount.
     */
    APPLY_NONE,

    /**
     * Reserved for custom handling; currently behaves like source-weight-only.
     */
    CUSTOM
}
