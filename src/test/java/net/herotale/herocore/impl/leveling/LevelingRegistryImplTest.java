package net.herotale.herocore.impl.leveling;

import net.herotale.herocore.api.component.HeroCoreProgressionComponent;
import net.herotale.herocore.api.leveling.LevelingProfile;
import net.herotale.herocore.api.leveling.XPAdjustmentPolicy;
import net.herotale.herocore.api.leveling.XPSource;
import net.herotale.herocore.api.leveling.XpCurve;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LevelingRegistryImplTest {

    private static final LevelingProfile COMBAT_PROFILE = LevelingProfile.builder()
            .id("combat")
            .maxLevel(10)
            .xpCurve(XpCurve.linear(100))
            .build();

    @Test
    void removeXp_smallAmount_doesNotChangeLevelOrFireEvent() {
        var start = new HeroCoreProgressionComponent.ProfileProgressData(3, 250f, 0f);

        var result = LevelingRegistryImpl.computeProgressDelta(COMBAT_PROFILE, start, -40, false);

        assertEquals(3, result.oldLevel());
        assertEquals(3, result.newLevel());
        assertEquals(210f, result.updatedData().getCurrentXP());
    }

    @Test
    void removeXp_singleLevelDown_firesOneLevelDownEvent() {
        var start = new HeroCoreProgressionComponent.ProfileProgressData(4, 350f, 0f);

        var result = LevelingRegistryImpl.computeProgressDelta(COMBAT_PROFILE, start, -100, false);

        assertEquals(4, result.oldLevel());
        assertEquals(3, result.newLevel());
        assertEquals(250f, result.updatedData().getCurrentXP());
    }

    @Test
    void removeXp_multiLevelDown_firesSingleBoundedLevelDownEvent() {
        var start = new HeroCoreProgressionComponent.ProfileProgressData(6, 550f, 0f);

        var result = LevelingRegistryImpl.computeProgressDelta(COMBAT_PROFILE, start, -400, false);

        assertEquals(6, result.oldLevel());
        assertEquals(2, result.newLevel());
        assertEquals(150f, result.updatedData().getCurrentXP());
    }

    @Test
    void removeXp_belowZero_clampsXpAtZeroAndLevelOne() {
        var start = new HeroCoreProgressionComponent.ProfileProgressData(5, 450f, 0f);

        var result = LevelingRegistryImpl.computeProgressDelta(COMBAT_PROFILE, start, -2000, false);

        assertEquals(5, result.oldLevel());
        assertEquals(1, result.newLevel());
        assertEquals(0f, result.updatedData().getCurrentXP());
    }

    @Test
    void removeXp_unknownProfile_throwsIllegalArgumentException() {
        Map<XPSource, Double> sourceWeights = new EnumMap<>(XPSource.class);
        LevelingRegistryImpl registry = new LevelingRegistryImpl(sourceWeights);

        assertThrows(IllegalArgumentException.class, () ->
                registry.removeXP(null, null, "missing", 10, XPSource.KILL,
                        XPAdjustmentPolicy.APPLY_SOURCE_WEIGHT_ONLY));
    }
}
