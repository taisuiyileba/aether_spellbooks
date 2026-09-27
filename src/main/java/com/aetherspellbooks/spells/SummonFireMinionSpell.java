package com.aetherspellbooks.spells;

import com.aetherspellbooks.config.ASConfig;
import com.aetherspellbooks.entity.SummonedFireMinion;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.SummonManager;
import io.redspace.ironsspellbooks.capabilities.magic.SummonedEntitiesCastData;
import com.aetherspellbooks.util.ASFx;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Summons friendly Fire Minions, the Sun Spirit's servants.
 */
public class SummonFireMinionSpell extends AetherSummonSpell {
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(SchoolRegistry.FIRE_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(60)
            .build();

    public SummonFireMinionSpell() {
        super("summon_fire_minion");
        this.baseManaCost = 80;
        this.manaCostPerLevel = 15;
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 2;
        this.castTime = 30;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.BLAZE_AMBIENT);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.FIRECHARGE_USE);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.irons_spellbooks.summon_count", getSummonCount(spellLevel)),
                Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(getMinionDamage(spellLevel, caster), 1)),
                Component.translatable("ui.irons_spellbooks.duration", Utils.timeFromTicks(getSummonDurationTicks(spellLevel, caster), 1)),
                Component.translatable("ui.aether_spellbooks.minion_burst")
        );
    }

    public static int getSummonCount(int spellLevel) {
        return 1 + spellLevel / 2;
    }

    public float getMinionDamage(int spellLevel, @Nullable LivingEntity caster) {
        return (float) (ASConfig.FIRE_MINION_DAMAGE.get() * getSpellPower(spellLevel, caster) / baseSpellPower);
    }

    @Override
    public int getSummonDurationTicks(int spellLevel, @Nullable LivingEntity caster) {
        return 60 * 20;
    }

    @Override
    protected void summon(ServerLevel level, int spellLevel, LivingEntity caster, int durationTicks, SummonedEntitiesCastData castData) {
        float damage = getMinionDamage(spellLevel, caster);
        ASFx.shake(level, caster.position(), 8, 6);
        for (int i = 0; i < getSummonCount(spellLevel); i++) {
            SummonedFireMinion minion = new SummonedFireMinion(level);
            Vec3 spawn = caster.position().add(Utils.getRandomScaled(2), 0.2, Utils.getRandomScaled(2));
            minion.moveTo(Utils.moveToRelativeGroundLevel(level, spawn, 4));
            minion.finalizeSpawn(level, level.getCurrentDifficultyAt(minion.blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
            minion.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(damage);
            minion.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20 + 4 * spellLevel);
            minion.setHealth(minion.getMaxHealth());
            level.addFreshEntity(minion);
            SummonManager.initSummon(caster, minion, durationTicks, castData);
            // a pillar of flame where each minion rises
            ASFx.helix(level, ParticleHelper.FIRE, minion.position(), 0.7, 2.4, 20, 2);
            level.sendParticles(ParticleHelper.FIERY_SPARKS, minion.getX(), minion.getY() + 1, minion.getZ(), 12, 0.3, 0.6, 0.3, 0.2);
            ASFx.blastwave(level, ASFx.SOLAR_ORANGE, minion.position(), 1.5f);
        }
    }
}
