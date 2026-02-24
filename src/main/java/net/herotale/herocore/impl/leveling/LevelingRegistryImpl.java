package net.herotale.herocore.impl.leveling;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import net.herotale.herocore.api.component.HeroCoreProgressionComponent;
import net.herotale.herocore.api.event.LevelDownEvent;
import net.herotale.herocore.api.event.LevelUpEvent;
import net.herotale.herocore.api.leveling.*;
import net.herotale.herocore.impl.HeroCoreStatTypes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the {@link LevelingRegistry}.
 * Manages named leveling profiles and per-entity XP/level data.
 * <p>
 * Uses {@code Ref<EntityStore>} for all runtime operations — no UUID in the API.
 * Level/XP data is read from and written to {@link HeroCoreProgressionComponent}
 * on the entity. Level events are dispatched via {@code store.invoke()}, flowing through the native ECS event system.
 */
public class LevelingRegistryImpl implements LevelingRegistry {

    private final Map<String, LevelingProfile> profiles = new ConcurrentHashMap<>();
    private final Map<XPSource, Double> sourceWeights;

    /**
     * @param sourceWeights XP source weights (multipliers per source type)
     */
    public LevelingRegistryImpl(Map<XPSource, Double> sourceWeights) {
        this.sourceWeights = sourceWeights;
    }

    @Override
    public void register(LevelingProfile profile) {
        profiles.put(profile.getId(), profile);
    }

    @Override
    public void unregister(String profileId) {
        profiles.remove(profileId);
    }

    @Override
    public LevelingProfile getProfile(String profileId) {
        return profiles.get(profileId);
    }

    @Override
    public void grantXP(Ref<EntityStore> entityRef, Store<EntityStore> store,
                        String profileId, double amount, XPSource source) {
        LevelingProfile profile = profiles.get(profileId);
        if (profile == null) {
            throw new IllegalArgumentException("Unknown leveling profile: " + profileId);
        }

        // Apply source weight
        double sourceWeight = sourceWeights.getOrDefault(source, 1.0);
        amount *= sourceWeight;

        // Apply XP_GAIN_MULTIPLIER from EntityStatMap if available
        int xpMultIndex = HeroCoreStatTypes.getIndex("HeroCoreXpGainMultiplier");
        if (xpMultIndex >= 0) {
            float xpMult = HeroCoreStatTypes.getStatValue(entityRef, xpMultIndex);
            if (xpMult > 0) {
                amount *= xpMult;
            }
        }

        // Read current progression for this profile from the entity's component
        HeroCoreProgressionComponent progression = store.getComponent(
                entityRef, HeroCoreProgressionComponent.getComponentType());
        if (progression == null) return;

        HeroCoreProgressionComponent.ProfileProgressData data = progression.getProgress(profileId);
        int oldLevel = data.getLevel();
        long currentXp = (long) data.getCurrentXP();
        currentXp += Math.round(amount);

        // Clamp XP to max
        long maxXp = profile.getXpCurve().getThreshold(profile.getMaxLevel());
        if (maxXp > 0) {
            currentXp = Math.min(currentXp, maxXp);
        }

        // Recalculate level
        int newLevel = profile.getXpCurve().getLevel(currentXp);
        newLevel = Math.min(newLevel, profile.getMaxLevel());

        // XP to next level for this profile
        float xpToNext = 0f;
        if (newLevel < profile.getMaxLevel()) {
            long nextThreshold = profile.getXpCurve().getThreshold(newLevel + 1);
            xpToNext = (float) (nextThreshold - currentXp);
        }

        // Write back to the entity's component for this profile only
        progression.setProgress(profileId, new HeroCoreProgressionComponent.ProfileProgressData(
                newLevel, (float) currentXp, xpToNext));

        // Fire level change events via Store.invoke() (store takes a buffer internally).
        if (newLevel > oldLevel) {
            store.invoke(entityRef, new LevelUpEvent(profileId, oldLevel, newLevel));
        } else if (newLevel < oldLevel) {
            store.invoke(entityRef, new LevelDownEvent(profileId, oldLevel, newLevel));
        }
    }

    @Override
    public int getLevel(Ref<EntityStore> entityRef, Store<EntityStore> store, String profileId) {
        HeroCoreProgressionComponent progression = store.getComponent(
                entityRef, HeroCoreProgressionComponent.getComponentType());
        return progression != null ? progression.getProgress(profileId).getLevel() : 1;
    }

    @Override
    public long getXP(Ref<EntityStore> entityRef, Store<EntityStore> store, String profileId) {
        HeroCoreProgressionComponent progression = store.getComponent(
                entityRef, HeroCoreProgressionComponent.getComponentType());
        return progression != null ? (long) progression.getProgress(profileId).getCurrentXP() : 0;
    }

    @Override
    public void setXP(Ref<EntityStore> entityRef, Store<EntityStore> store, String profileId, long xp) {
        LevelingProfile profile = profiles.get(profileId);
        if (profile == null) {
            throw new IllegalArgumentException("Unknown leveling profile: " + profileId);
        }

        HeroCoreProgressionComponent progression = store.getComponent(
                entityRef, HeroCoreProgressionComponent.getComponentType());
        if (progression == null) return;

        int newLevel = Math.min(profile.getXpCurve().getLevel(xp), profile.getMaxLevel());
        float xpToNext = 0f;
        if (newLevel < profile.getMaxLevel()) {
            long nextThreshold = profile.getXpCurve().getThreshold(newLevel + 1);
            xpToNext = (float) (nextThreshold - xp);
        }
        progression.setProgress(profileId, new HeroCoreProgressionComponent.ProfileProgressData(
                newLevel, (float) xp, xpToNext));
    }
}
