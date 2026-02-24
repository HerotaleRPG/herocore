package net.herotale.herocore.api.component;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ECS Component: Tracks an entity's level, XP, and progression state per leveling profile.
 * Each profile (e.g. combat, woodcutting) has its own level and XP.
 * <p>
 * Hytale has no native leveling system, so HeroCore owns this entirely.
 * Persisted via {@link BuilderCodec}; profile data is stored as a single serialized string.
 */
public class HeroCoreProgressionComponent implements Component<EntityStore> {

    /** Per-profile progress. Key = profile ID (e.g. "combat", "woodcutting"). */
    private final Map<String, ProfileProgressData> profiles = new ConcurrentHashMap<>();

    /** Default constructor required by registration factory. */
    public HeroCoreProgressionComponent() {}

    /** Copy constructor required by {@link #clone()}. */
    public HeroCoreProgressionComponent(HeroCoreProgressionComponent other) {
        for (Map.Entry<String, ProfileProgressData> e : other.profiles.entrySet()) {
            this.profiles.put(e.getKey(), new ProfileProgressData(e.getValue()));
        }
    }

    @Override
    public Component<EntityStore> clone() {
        return new HeroCoreProgressionComponent(this);
    }

    // ── Static ComponentType handle ──────────────────────────────────
    private static ComponentType<EntityStore, HeroCoreProgressionComponent> type;

    public static ComponentType<EntityStore, HeroCoreProgressionComponent> getComponentType() {
        return type;
    }

    public static void setComponentType(ComponentType<EntityStore, HeroCoreProgressionComponent> t) {
        type = t;
    }

    // ── Persistence: single string "id|level|xp|xpToNext;id2|..." (profile IDs must not contain | or ;)
    private static final String ENTRY_SEP = ";";
    private static final String FIELD_SEP = "|";

    public String getProfilesEncoded() {
        if (profiles.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ProfileProgressData> e : profiles.entrySet()) {
            if (sb.length() > 0) sb.append(ENTRY_SEP);
            ProfileProgressData d = e.getValue();
            sb.append(e.getKey()).append(FIELD_SEP)
              .append(d.level).append(FIELD_SEP)
              .append(d.currentXP).append(FIELD_SEP)
              .append(d.xpToNextLevel);
        }
        return sb.toString();
    }

    public void setProfilesEncoded(String encoded) {
        profiles.clear();
        if (encoded == null || encoded.isEmpty()) return;
        for (String entry : encoded.split(ENTRY_SEP)) {
            String[] parts = entry.split("\\" + FIELD_SEP);
            if (parts.length != 4) continue;
            try {
                String profileId = parts[0];
                int level = Integer.parseInt(parts[1]);
                float currentXP = Float.parseFloat(parts[2]);
                float xpToNextLevel = Float.parseFloat(parts[3]);
                profiles.put(profileId, new ProfileProgressData(level, currentXP, xpToNextLevel));
            } catch (NumberFormatException ignored) { }
        }
    }

    // ── BuilderCodec ─────────────────────────────────────────────────
    public static final BuilderCodec<HeroCoreProgressionComponent> CODEC = BuilderCodec
            .builder(HeroCoreProgressionComponent.class, HeroCoreProgressionComponent::new)
            .append(new KeyedCodec<>("HC_Profiles", Codec.STRING),
                    HeroCoreProgressionComponent::setProfilesEncoded,
                    HeroCoreProgressionComponent::getProfilesEncoded)
            .add()
            .build();

    // ── API (per-profile) ──────────────────────────────────────────

    /**
     * Get progress for a profile. Returns default (level 1, 0 XP) if not present.
     */
    public ProfileProgressData getProgress(String profileId) {
        return profiles.computeIfAbsent(profileId, id -> new ProfileProgressData(1, 0f, 100f));
    }

    /**
     * Set progress for a profile.
     */
    public void setProgress(String profileId, ProfileProgressData data) {
        profiles.put(profileId, new ProfileProgressData(data));
    }

    @Override
    public String toString() {
        return "HeroCoreProgressionComponent{profiles=" + profiles + '}';
    }

    // ── Per-profile data (immutable value) ───────────────────────────

    public static final class ProfileProgressData {
        private final int level;
        private final float currentXP;
        private final float xpToNextLevel;

        public ProfileProgressData(int level, float currentXP, float xpToNextLevel) {
            this.level = level;
            this.currentXP = currentXP;
            this.xpToNextLevel = xpToNextLevel;
        }

        public ProfileProgressData(ProfileProgressData other) {
            this(other.level, other.currentXP, other.xpToNextLevel);
        }

        public int getLevel() { return level; }
        public float getCurrentXP() { return currentXP; }
        public float getXpToNextLevel() { return xpToNextLevel; }

        public ProfileProgressData withLevel(int level) {
            return new ProfileProgressData(level, currentXP, xpToNextLevel);
        }
        public ProfileProgressData withCurrentXP(float currentXP) {
            return new ProfileProgressData(level, currentXP, xpToNextLevel);
        }
        public ProfileProgressData withXpToNextLevel(float xpToNextLevel) {
            return new ProfileProgressData(level, currentXP, xpToNextLevel);
        }

        @Override
        public String toString() {
            return "ProfileProgressData{level=" + level + ", xp=" + currentXP + ", xpToNext=" + xpToNextLevel + '}';
        }
    }
}
