package net.herotale.herocore.api.event;

import com.hypixel.hytale.component.system.EcsEvent;

/**
 * ECS Event fired when an entity loses levels in a specific leveling profile.
 * <p>
 * Dispatched via {@code cb.invoke(entityRef, new LevelDownEvent(profileId, oldLevel, newLevel))}
 * through the native ECS event system.
 */
public class LevelDownEvent extends EcsEvent {

    private final String profileId;
    private final int oldLevel;
    private final int newLevel;

    public LevelDownEvent(String profileId, int oldLevel, int newLevel) {
        this.profileId = profileId;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public String getProfileId() {
        return profileId;
    }

    public int getOldLevel() {
        return oldLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }
}
