package com.aetherspellbooks.registry;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.spells.AercloudStepSpell;
import com.aetherspellbooks.spells.AerwhaleSongSpell;
import com.aetherspellbooks.spells.IcestoneMeteorSpell;
import com.aetherspellbooks.spells.RadiantJavelinSpell;
import com.aetherspellbooks.spells.ThunderheadSpell;
import com.aetherspellbooks.spells.AetherWhirlwindSpell;
import com.aetherspellbooks.spells.CloudSentinelsSpell;
import com.aetherspellbooks.spells.GravititeSurgeSpell;
import com.aetherspellbooks.spells.SolarFlareSpell;
import com.aetherspellbooks.spells.ValkyrieLungeSpell;
import com.aetherspellbooks.spells.ZephyrBlastSpell;
import com.aetherspellbooks.spells.FrostboundCrystalSpell;
import com.aetherspellbooks.spells.StonebreakerShardSpell;
import com.aetherspellbooks.spells.SummonFireMinionSpell;
import com.aetherspellbooks.spells.SummonMoaSpell;
import com.aetherspellbooks.spells.ThunderCrystalSpell;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ASSpells {
    public static final DeferredRegister<AbstractSpell> SPELLS = DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, AetherSpellbooks.MODID);

    public static final RegistryObject<AbstractSpell> AERCLOUD_STEP = SPELLS.register("aercloud_step", AercloudStepSpell::new);
    public static final RegistryObject<AbstractSpell> STONEBREAKER_SHARD = SPELLS.register("stonebreaker_shard", StonebreakerShardSpell::new);
    public static final RegistryObject<AbstractSpell> THUNDER_CRYSTAL = SPELLS.register("thunder_crystal", ThunderCrystalSpell::new);
    public static final RegistryObject<AbstractSpell> FROSTBOUND_CRYSTAL = SPELLS.register("frostbound_crystal", FrostboundCrystalSpell::new);
    public static final RegistryObject<AbstractSpell> SUMMON_FIRE_MINION = SPELLS.register("summon_fire_minion", SummonFireMinionSpell::new);
    public static final RegistryObject<AbstractSpell> SUMMON_MOA = SPELLS.register("summon_moa", SummonMoaSpell::new);

    public static final RegistryObject<AbstractSpell> GRAVITITE_SURGE = SPELLS.register("gravitite_surge", GravititeSurgeSpell::new);
    public static final RegistryObject<AbstractSpell> VALKYRIE_LUNGE = SPELLS.register("valkyrie_lunge", ValkyrieLungeSpell::new);
    public static final RegistryObject<AbstractSpell> ZEPHYR_BLAST = SPELLS.register("zephyr_blast", ZephyrBlastSpell::new);
    public static final RegistryObject<AbstractSpell> AETHER_WHIRLWIND = SPELLS.register("aether_whirlwind", AetherWhirlwindSpell::new);
    public static final RegistryObject<AbstractSpell> SOLAR_FLARE = SPELLS.register("solar_flare", SolarFlareSpell::new);
    public static final RegistryObject<AbstractSpell> CLOUD_SENTINELS = SPELLS.register("cloud_sentinels", CloudSentinelsSpell::new);

    // v1.5
    public static final RegistryObject<AbstractSpell> RADIANT_JAVELIN = SPELLS.register("radiant_javelin", RadiantJavelinSpell::new);
    public static final RegistryObject<AbstractSpell> THUNDERHEAD = SPELLS.register("thunderhead", ThunderheadSpell::new);
    public static final RegistryObject<AbstractSpell> AERWHALE_SONG = SPELLS.register("aerwhale_song", AerwhaleSongSpell::new);
    public static final RegistryObject<AbstractSpell> ICESTONE_METEOR = SPELLS.register("icestone_meteor", IcestoneMeteorSpell::new);

    public static void register(IEventBus bus) {
        SPELLS.register(bus);
    }
}
