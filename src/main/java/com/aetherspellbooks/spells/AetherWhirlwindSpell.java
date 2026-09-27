package com.aetherspellbooks.spells;

import com.aetherspellbooks.entity.AetherWhirlwind;
import com.aetherspellbooks.registry.ASParticles;
import com.aetherspellbooks.util.ASFx;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Conjures a roaming Aether whirlwind that hunts nearby enemies and drags them into the sky.
 */
public class AetherWhirlwindSpell extends AetherSpell {
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(SchoolRegistry.NATURE_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(25)
            .build();

    public AetherWhirlwindSpell() {
        super("aether_whirlwind");
        this.baseManaCost = 50;
        this.manaCostPerLevel = 10;
        this.baseSpellPower = 2;
        this.spellPowerPerLevel = 1;
        this.castTime = 15;
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
        return SpellAnimations.ANIMATION_LONG_CAST;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SpellAnimations.ANIMATION_LONG_CAST_FINISH;
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.ELYTRA_FLYING);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.EVOKER_CAST_SPELL);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aether_spellbooks.damage_per_second", Utils.stringTruncation(getSpellPower(spellLevel, caster) * 2, 1)),
                Component.translatable("ui.irons_spellbooks.radius", Utils.stringTruncation(getRadius(spellLevel), 1)),
                Component.translatable("ui.irons_spellbooks.duration", Utils.timeFromTicks(getDurationTicks(spellLevel), 1))
        );
    }

    public static float getRadius(int spellLevel) {
        return 2.5f + 0.25f * spellLevel;
    }

    public static int getDurationTicks(int spellLevel) {
        return (6 + 2 * spellLevel) * 20;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData playerMagicData) {
        if (!level.isClientSide) {
            Vec3 forward = caster.getLookAngle().multiply(1, 0, 1).normalize();
            Vec3 spawn = Utils.moveToRelativeGroundLevel(level, caster.position().add(forward.scale(2.5)), 3);
            AetherWhirlwind whirlwind = new AetherWhirlwind(level, caster, forward);
            whirlwind.setPos(spawn);
            whirlwind.setRadius(getRadius(spellLevel));
            whirlwind.setDamage(getSpellPower(spellLevel, caster));
            whirlwind.setLifetime(getDurationTicks(spellLevel));
            level.addFreshEntity(whirlwind);
            ASFx.helix(level, ASParticles.CLOUD_PUFF.get(), spawn, 1.2, 4, 24, 3);
        }
        super.onCast(level, spellLevel, caster, castSource, playerMagicData);
    }
}
