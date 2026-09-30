package com.aetherspellbooks.spells;

import com.aetherspellbooks.entity.RadiantJavelin;
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
import io.redspace.ironsspellbooks.registries.SoundRegistry;
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
 * Hurls a golden Valkyrie spear that pierces every foe in its path, plants itself where it lands and erupts in a
 * column of holy light: foes around it are struck and tossed, the caster and allies are healed.
 */
public class RadiantJavelinSpell extends AetherSpell {
    public static final float ERUPTION_RATIO = 0.6f;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.UNCOMMON)
            .setSchoolResource(SchoolRegistry.HOLY_RESOURCE)
            .setMaxLevel(8)
            .setCooldownSeconds(12)
            .build();

    public RadiantJavelinSpell() {
        super("radiant_javelin");
        this.baseManaCost = 35;
        this.manaCostPerLevel = 5;
        this.baseSpellPower = 8;
        this.spellPowerPerLevel = 2;
        this.castTime = 16;
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
        return SpellAnimations.THROW_SINGLE_ITEM;
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundRegistry.DIVINE_SMITE_WINDUP.get());
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.TRIDENT_THROW);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        float damage = getSpellPower(spellLevel, caster);
        return List.of(
                Component.translatable("ui.irons_spellbooks.impact_damage", Utils.stringTruncation(damage, 1)),
                Component.translatable("ui.irons_spellbooks.aoe_damage", Utils.stringTruncation(damage * ERUPTION_RATIO, 1)),
                Component.translatable("ui.irons_spellbooks.radius", Utils.stringTruncation(getRadius(spellLevel), 1)),
                Component.translatable("ui.irons_spellbooks.aoe_healing", Utils.stringTruncation(getHealing(spellLevel), 1)),
                Component.translatable("ui.aether_spellbooks.pierces_all")
        );
    }

    public static float getRadius(int spellLevel) {
        return 2.5f + 0.2f * spellLevel;
    }

    public static float getHealing(int spellLevel) {
        return 1f + 0.5f * spellLevel;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData playerMagicData) {
        if (!level.isClientSide) {
            RadiantJavelin javelin = new RadiantJavelin(level, caster);
            Vec3 look = caster.getLookAngle();
            Vec3 from = caster.getEyePosition().subtract(0, 0.25, 0).add(look.scale(0.6));
            javelin.setPos(from);
            javelin.shoot(look);
            float damage = getSpellPower(spellLevel, caster);
            javelin.setDamage(damage);
            javelin.setEruption(getRadius(spellLevel), damage * ERUPTION_RATIO, getHealing(spellLevel));
            level.addFreshEntity(javelin);
            // a flash where the spear leaves the hand (a little ahead, so it does not fill a first-person view)
            Vec3 flash = from.add(look.scale(1.2));
            ASFx.burst(level, ASParticles.SKY_SPARKLE.get(), flash, 8, 0.2, 0.05);
            ASFx.burst(level, ASParticles.FEATHER.get(), flash, 3, 0.25, 0.02);
        }
        super.onCast(level, spellLevel, caster, castSource, playerMagicData);
    }
}
