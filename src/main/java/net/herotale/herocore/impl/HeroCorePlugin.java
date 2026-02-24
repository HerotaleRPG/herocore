package net.herotale.herocore.impl;

import javax.annotation.Nonnull;
import java.util.logging.Level;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import net.herotale.herocore.api.HeroCore;
import net.herotale.herocore.api.damage.HeroCoreDamageEvent;
import net.herotale.herocore.api.event.CombatExitEvent;
import net.herotale.herocore.api.event.LevelDownEvent;
import net.herotale.herocore.api.event.LevelUpEvent;
import net.herotale.herocore.api.heal.HeroCoreHealEvent;
import net.herotale.herocore.api.leveling.XPSource;
import net.herotale.herocore.impl.entity.MobRegistryImpl;
import net.herotale.herocore.impl.harvest.HarvestTierRegistryImpl;
import net.herotale.herocore.impl.leveling.LevelingRegistryImpl;
import net.herotale.herocore.impl.zone.ZoneModifierRegistryImpl;
import net.herotale.herocore.impl.config.CoreConfig;
import net.herotale.herocore.impl.config.CoreConfigLoader;
import net.herotale.herocore.impl.language.*;
import net.herotale.herocore.api.language.*;
import net.herotale.herocore.impl.system.AttributeDerivationSystem;
import net.herotale.herocore.impl.system.HeroCoreSetupSystem;
import net.herotale.herocore.system.combat.CombatTimeoutSystem;
import net.herotale.herocore.system.combat.StatusEffectTickSystem;
import net.herotale.herocore.system.damage.*;
import net.herotale.herocore.system.heal.*;

/**
 * Hytale plugin entry point for HeroCore.
 * <p>
 * <b>Architecture:</b> HeroCore is a <b>schema library and formula provider</b> that extends
 * Hytale's native ECS and stat systems, not a parallel system alongside them.
 * <p>
 * All damage/heal systems are registered as ECS {@code EntityEventSystem} instances
 * with correct ordering via {@code SystemDependency}. There is no custom event bus
 * or system orchestrator — Hytale's native system pipeline handles execution order.
 */
public class HeroCorePlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static HeroCorePlugin instance;

    private CoreConfig config;
    private LanguageService languageService;
    private TrainingRegistry trainingRegistry;
    private HerochatLanguageHook herochatHook;

    public HeroCorePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> preLoad() {
        config = CoreConfigLoader.load();
        return super.preLoad();
    }

    @Override
    protected void setup() {
        var reg = getEntityStoreRegistry();

        // 1. Register HeroCore component types (persistent + non-persistent)
        HeroCoreComponentRegistry.registerComponents(reg);

        // 2. Register ECS event types
        reg.registerEntityEventType(HeroCoreDamageEvent.class);
        reg.registerEntityEventType(HeroCoreHealEvent.class);
        reg.registerEntityEventType(LevelUpEvent.class);
        reg.registerEntityEventType(LevelDownEvent.class);
        reg.registerEntityEventType(CombatExitEvent.class);

        // 3. Register HolderSystem — ensures HeroCoreStatsComponent on all entities
        reg.registerSystem(new HeroCoreSetupSystem());

        // 4. Register AttributeDerivationSystem (tick system: primary → derived stats)
        reg.registerSystem(new AttributeDerivationSystem());

        // 5. Register damage pipeline (EntityEventSystem instances, ordered via SystemDependency)
        reg.registerSystem(new AttackDamageBonusSystem());
        reg.registerSystem(new FallDamageReductionSystem());
        reg.registerSystem(new ResistanceMitigationSystem(
                (float) config.damage().maxResistanceReduction()));
        reg.registerSystem(new CriticalHitSystem(
                (float) config.damage().critDamageBaseMultiplier()));
        reg.registerSystem(new LifestealSystem());
        reg.registerSystem(new MinimumDamageSystem(
                (float) config.damage().minimumDamage()));
        reg.registerSystem(new DamageApplicationSystem());

        // 6. Register heal pipeline (EntityEventSystem instances, ordered via SystemDependency)
        reg.registerSystem(new HealingPowerScalingSystem(
                config.heal().healingPowerScalesRegenTick()));
        reg.registerSystem(new HealingReceivedBonusSystem());
        reg.registerSystem(new HealCritSystem());

        // 7. Register tick-based systems (DelayedEntitySystem)
        reg.registerSystem(new CombatTimeoutSystem(
                config.resourceRegen().combatTimeoutSeconds()));
        reg.registerSystem(new StatusEffectTickSystem());

        // 8. Initialize the public API facade
        HeroCore api = HeroCore.initialize();

        // 9. Instantiate and wire up registries
        // Convert XP source weights from config (String keys) to enum keys
        var sourceWeights = new java.util.HashMap<XPSource, Double>();
        config.leveling().sourceWeights().forEach((key, value) -> {
            try {
                sourceWeights.put(XPSource.valueOf(key.toUpperCase()), value);
            } catch (IllegalArgumentException e) {
                LOGGER.at(Level.WARNING).log("Unknown XP source in config: " + key);
            }
        });

        api.setLevelingRegistry(new LevelingRegistryImpl(sourceWeights));
        api.setMobRegistry(new MobRegistryImpl());
        api.setZoneModifierRegistry(new ZoneModifierRegistryImpl());
        api.setHarvestTierRegistry(new HarvestTierRegistryImpl());

        // 10. Initialize language system
        // Use consistent directory structure: mods/herocore (same as config.json)
        java.nio.file.Path heroCorePath = java.nio.file.Path.of("mods", "herocore");
        LanguagePersistenceLayer persistence = new JsonLanguagePersistence(heroCorePath);
        this.languageService = new LanguageServiceImpl(persistence);
        this.trainingRegistry = new TrainingRegistryImpl(languageService);
        this.herochatHook = new HerochatLanguageHookImpl(languageService);
        
        // Load default languages
        LanguageConfigLoader configLoader = new LanguageConfigLoader();
        java.nio.file.Path languageConfigPath = heroCorePath.resolve("languages.json");
        for (LanguageDefinition langDef : configLoader.loadFromFile(languageConfigPath)) {
            languageService.registerLanguage(langDef);
        }
        
        // Load persisted player profiles
        languageService.loadAll();
        
        api.setLanguageService(languageService);
        api.setTrainingRegistry(trainingRegistry);

        LOGGER.at(Level.INFO).log("HeroCore initialized — damage/heal pipelines, " +
                "combat timeout, status effects, AttributeDerivationSystem, language system, and all registries ready.");
    }

    @Override
    protected void start() {
        // Resolve and validate stat type indices after assets are loaded
        try {
            HeroCoreStatTypes.update();
            HeroCoreStatTypes.validate();
            LOGGER.at(Level.INFO).log("HeroCore stat types resolved and validated successfully.");
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("HeroCore stat type validation failed. " +
                    "Some derived attributes may not function.");
        }
    }

    @Override
    protected void shutdown() {
        // Save all language data before shutdown
        if (languageService != null) {
            languageService.saveAll();
        }
        LOGGER.at(Level.INFO).log("HeroCore shutting down cleanly.");
    }

    // ── Accessors ────────────────────────────────────────────────────

    public static HeroCorePlugin get() { return instance; }
    public CoreConfig getConfig() { return config; }
    public LanguageService getLanguageService() { return languageService; }
    public TrainingRegistry getTrainingRegistry() { return trainingRegistry; }
    public HerochatLanguageHook getHerochatLanguageHook() { return herochatHook; }
}
