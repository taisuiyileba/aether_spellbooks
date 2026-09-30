package com.aetherspellbooks.registry;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.entity.AetherWhirlwind;
import com.aetherspellbooks.entity.IcestoneMeteor;
import com.aetherspellbooks.entity.RadiantJavelin;
import com.aetherspellbooks.entity.SpectralAerwhale;
import com.aetherspellbooks.entity.StormBolt;
import com.aetherspellbooks.entity.StormCloud;
import com.aetherspellbooks.entity.SolarBoltProjectile;
import com.aetherspellbooks.entity.SolarOrb;
import com.aetherspellbooks.entity.StonebreakerShard;
import com.aetherspellbooks.entity.ZephyrOrbProjectile;
import com.aetherspellbooks.entity.SummonedFireMinion;
import com.aetherspellbooks.entity.SummonedMoa;
import com.aetherspellbooks.entity.ThunderCrystalProjectile;
import com.aetherspellbooks.entity.mob.AetherSpellcaster;
import com.aetherspellbooks.entity.mob.SolarAcolyte;
import com.aetherspellbooks.entity.mob.ValkyrieSorceress;
import com.aetherteam.aether.data.resources.AetherMobCategory;
import com.aetherteam.aether.entity.monster.dungeon.FireMinion;
import com.aetherteam.aether.entity.passive.Aerwhale;
import com.aetherteam.aether.entity.passive.Moa;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ASEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AetherSpellbooks.MODID);

    public static final RegistryObject<EntityType<StonebreakerShard>> STONEBREAKER_SHARD = ENTITIES.register("stonebreaker_shard",
            () -> EntityType.Builder.<StonebreakerShard>of(StonebreakerShard::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f)
                    .clientTrackingRange(64)
                    .build(AetherSpellbooks.id("stonebreaker_shard").toString()));

    public static final RegistryObject<EntityType<ThunderCrystalProjectile>> THUNDER_CRYSTAL = ENTITIES.register("thunder_crystal",
            () -> EntityType.Builder.<ThunderCrystalProjectile>of(ThunderCrystalProjectile::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(64)
                    .build(AetherSpellbooks.id("thunder_crystal").toString()));

    public static final RegistryObject<EntityType<SummonedFireMinion>> SUMMONED_FIRE_MINION = ENTITIES.register("summoned_fire_minion",
            () -> EntityType.Builder.<SummonedFireMinion>of(SummonedFireMinion::new, MobCategory.MISC)
                    .sized(1.1f, 1.95f)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build(AetherSpellbooks.id("summoned_fire_minion").toString()));

    public static final RegistryObject<EntityType<SummonedMoa>> SUMMONED_MOA = ENTITIES.register("summoned_moa",
            () -> EntityType.Builder.<SummonedMoa>of(SummonedMoa::new, MobCategory.MISC)
                    .sized(0.9f, 2.15f)
                    .clientTrackingRange(10)
                    .build(AetherSpellbooks.id("summoned_moa").toString()));

    public static final RegistryObject<EntityType<ZephyrOrbProjectile>> ZEPHYR_ORB = ENTITIES.register("zephyr_orb",
            () -> EntityType.Builder.<ZephyrOrbProjectile>of(ZephyrOrbProjectile::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f)
                    .clientTrackingRange(64)
                    .build(AetherSpellbooks.id("zephyr_orb").toString()));

    public static final RegistryObject<EntityType<SolarBoltProjectile>> SOLAR_BOLT = ENTITIES.register("solar_bolt",
            () -> EntityType.Builder.<SolarBoltProjectile>of(SolarBoltProjectile::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f)
                    .fireImmune()
                    .clientTrackingRange(64)
                    .build(AetherSpellbooks.id("solar_bolt").toString()));

    public static final RegistryObject<EntityType<AetherWhirlwind>> AETHER_WHIRLWIND = ENTITIES.register("aether_whirlwind",
            () -> EntityType.Builder.<AetherWhirlwind>of(AetherWhirlwind::new, MobCategory.MISC)
                    .sized(1.5f, 4.5f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build(AetherSpellbooks.id("aether_whirlwind").toString()));

    public static final RegistryObject<EntityType<SolarOrb>> SOLAR_ORB = ENTITIES.register("solar_orb",
            () -> EntityType.Builder.<SolarOrb>of(SolarOrb::new, MobCategory.MISC)
                    .sized(0.8f, 0.8f)
                    .fireImmune()
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build(AetherSpellbooks.id("solar_orb").toString()));

    // --- v1.5 spell entities ---
    public static final RegistryObject<EntityType<RadiantJavelin>> RADIANT_JAVELIN = ENTITIES.register("radiant_javelin",
            () -> EntityType.Builder.<RadiantJavelin>of(RadiantJavelin::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(64)
                    .build(AetherSpellbooks.id("radiant_javelin").toString()));

    public static final RegistryObject<EntityType<StormCloud>> STORM_CLOUD = ENTITIES.register("storm_cloud",
            () -> EntityType.Builder.<StormCloud>of(StormCloud::new, MobCategory.MISC)
                    .sized(3f, 1f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build(AetherSpellbooks.id("storm_cloud").toString()));

    public static final RegistryObject<EntityType<StormBolt>> STORM_BOLT = ENTITIES.register("storm_bolt",
            () -> EntityType.Builder.<StormBolt>of(StormBolt::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(64)
                    .build(AetherSpellbooks.id("storm_bolt").toString()));

    public static final RegistryObject<EntityType<SpectralAerwhale>> SPECTRAL_AERWHALE = ENTITIES.register("spectral_aerwhale",
            () -> EntityType.Builder.<SpectralAerwhale>of(SpectralAerwhale::new, MobCategory.MISC)
                    .sized(3f, 2f)
                    .noSave()
                    .clientTrackingRange(80)
                    .updateInterval(1)
                    .build(AetherSpellbooks.id("spectral_aerwhale").toString()));

    public static final RegistryObject<EntityType<IcestoneMeteor>> ICESTONE_METEOR = ENTITIES.register("icestone_meteor",
            () -> EntityType.Builder.<IcestoneMeteor>of(IcestoneMeteor::new, MobCategory.MISC)
                    .sized(1.2f, 1.2f)
                    .clientTrackingRange(80)
                    .updateInterval(1)
                    .build(AetherSpellbooks.id("icestone_meteor").toString()));

    // --- Spellcasting mobs: counted against the Aether's surface monster cap, like Swets and Whirlwinds ---
    public static final RegistryObject<EntityType<ValkyrieSorceress>> VALKYRIE_SORCERESS = ENTITIES.register("valkyrie_sorceress",
            () -> EntityType.Builder.<ValkyrieSorceress>of(ValkyrieSorceress::new, AetherMobCategory.AETHER_SURFACE_MONSTER)
                    .sized(.6f, 2f)
                    .clientTrackingRange(64)
                    .build(AetherSpellbooks.id("valkyrie_sorceress").toString()));

    public static final RegistryObject<EntityType<SolarAcolyte>> SOLAR_ACOLYTE = ENTITIES.register("solar_acolyte",
            () -> EntityType.Builder.<SolarAcolyte>of(SolarAcolyte::new, AetherMobCategory.AETHER_SURFACE_MONSTER)
                    .sized(.6f, 2f)
                    .fireImmune()
                    .clientTrackingRange(64)
                    .build(AetherSpellbooks.id("solar_acolyte").toString()));

    public static void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(SUMMONED_FIRE_MINION.get(), FireMinion.createMobAttributes().build());
        event.put(SUMMONED_MOA.get(), Moa.createMobAttributes().build());
        event.put(SPECTRAL_AERWHALE.get(), Aerwhale.createMobAttributes().build());
        event.put(VALKYRIE_SORCERESS.get(), ValkyrieSorceress.prepareAttributes().build());
        event.put(SOLAR_ACOLYTE.get(), SolarAcolyte.prepareAttributes().build());
    }

    public static void onSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(VALKYRIE_SORCERESS.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                AetherSpellcaster::checkSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(SOLAR_ACOLYTE.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                AetherSpellcaster::checkSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }
}
