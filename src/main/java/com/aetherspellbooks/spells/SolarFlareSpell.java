package com.aetherspellbooks.spells;

import com.aetherspellbooks.entity.SolarOrb;
import com.aetherspellbooks.util.ASFx;
import com.aetherteam.aether.client.AetherSoundEvents;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Kindles a miniature Sun Spirit above the caster that rains homing sunfire bolts on nearby foes.
 */
public class SolarFlareSpell extends AetherSpell {
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(SchoolRegistry.FIRE_RESOURCE)
            .setMaxLevel(3)
            .setCooldownSeconds(60)
            .build();

    public SolarFlareSpell() {
        super("solar_flare");
        this.baseManaCost = 100;
        this.manaCostPerLevel = 30;
        this.baseSpellPower = 6;
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
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.CHARGE_RAISED_HAND;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SpellAnimations.CAST_T_POSE;
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(AetherSoundEvents.ENTITY_SUN_SPIRIT_INTERACT.get());
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(AetherSoundEvents.ENTITY_SUN_SPIRIT_ACTIVATE.get());
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)),
                Component.translatable("ui.aether_spellbooks.bolts_per_second", Utils.stringTruncation(20f / getFireInterval(spellLevel), 1)),
                Component.translatable("ui.irons_spellbooks.duration", Utils.timeFromTicks(getDurationTicks(spellLevel), 1))
        );
    }

    public static int getDurationTicks(int spellLevel) {
        return (10 + 3 * spellLevel) * 20;
    }

    public static int getFireInterval(int spellLevel) {
        return 20 - 3 * spellLevel;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData playerMagicData) {
        if (!level.isClientSide) {
            // only one sun per caster
            level.getEntitiesOfClass(SolarOrb.class, caster.getBoundingBox().inflate(64), orb -> orb.getOwner() == caster).forEach(SolarOrb::discard);
            SolarOrb orb = new SolarOrb(level, caster);
            orb.setDamage(getSpellPower(spellLevel, caster));
            orb.setLifetime(getDurationTicks(spellLevel));
            orb.setFireInterval(getFireInterval(spellLevel));
            level.addFreshEntity(orb);

            ASFx.helix(level, ParticleHelper.FIRE, caster.position(), 1.0, caster.getBbHeight() + 1.5, 36, 3);
            ASFx.blastwave(level, ASFx.SOLAR_ORANGE, caster.position(), 3.5f);
            ASFx.burst(level, ParticleHelper.FIERY_SPARKS, orb.position(), 20, 0.3, 0.4);
            ASFx.shake(level, caster.position(), 10, 8);
        }
        super.onCast(level, spellLevel, caster, castSource, playerMagicData);
    }
}
