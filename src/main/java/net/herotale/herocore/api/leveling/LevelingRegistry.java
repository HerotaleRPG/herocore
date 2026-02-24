package net.herotale.herocore.api.leveling;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Registry of named leveling profiles. Any plugin can register a profile
 * and grant XP to it. The {@code XP_GAIN_MULTIPLIER} attribute is honored
 * globally across all profiles.
 * <p>
 * All runtime methods take {@code Ref<EntityStore>} — UUID is for persistence only.
 */
public interface LevelingRegistry {

    /**
     * Register a leveling profile. Idempotent — re-registration with the same ID overwrites.
     *
     * @param profile the profile to register
     */
    void register(LevelingProfile profile);

    /**
     * Unregister a profile by ID.
     *
     * @param profileId the profile ID to remove
     */
    void unregister(String profileId);

    /**
     * Get a registered profile by ID.
     *
     * @param profileId the profile ID
     * @return the profile, or null if not registered
     */
    LevelingProfile getProfile(String profileId);

    /**
     * Grant XP to an entity for a specific profile.
     * <ol>
     *   <li>Multiply amount by entity's {@code XP_GAIN_MULTIPLIER} stat</li>
     *   <li>Add to stored XP for this profile</li>
     *   <li>Check if new XP total crosses level threshold</li>
     *   <li>If yes: update level, dispatch {@code LevelUpEvent} or {@code LevelDownEvent}
     *       via {@code store.invoke()}</li>
     * </ol>
     *
     * @param entityRef the live entity handle
     * @param store     the entity store (for reading/writing components and dispatching level events)
     * @param profileId the leveling profile ID
     * @param amount    raw XP amount (before multipliers)
     * @param source    where the XP came from
     */
    void grantXP(Ref<EntityStore> entityRef, Store<EntityStore> store,
                 String profileId, double amount, XPSource source);

    /**
     * Get the current level for an entity in a profile.
     *
     * @param entityRef the live entity handle
     * @param store     the entity store (for reading components)
     * @param profileId the profile ID
     * @return the current level, or 1 if no data
     */
    int getLevel(Ref<EntityStore> entityRef, Store<EntityStore> store, String profileId);

    /**
     * Get the current cumulative XP for an entity in a profile.
     *
     * @param entityRef the live entity handle
     * @param store     the entity store (for reading components)
     * @param profileId the profile ID
     * @return the cumulative XP, or 0 if no data
     */
    long getXP(Ref<EntityStore> entityRef, Store<EntityStore> store, String profileId);

    /**
     * Set the XP and level directly (for admin/data loading).
     *
     * @param entityRef the live entity handle
     * @param store     the entity store (for reading/writing components)
     * @param profileId the profile ID
     * @param xp        the cumulative XP to set
     */
    void setXP(Ref<EntityStore> entityRef, Store<EntityStore> store, String profileId, long xp);
}
