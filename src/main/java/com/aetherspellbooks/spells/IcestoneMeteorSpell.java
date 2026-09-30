package com.aetherspellbooks.spells;

import com.aetherspellbooks.entity.IcestoneMeteor;
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
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Calls a boulder of icestone down from the sky onto the spot the caster aims at. It shatters in a freezing blast
 * (more damage toward the centre) that chills and throws back foes and freezes the surrounding water.
 */
public class IcestoneMeteorSpell extends AetherSpell {
    public static final float RANGE = 36f;
    public static final double DROP_HEIGHT = 16;
    public static final double DROP_OFFSET = 5;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(SchoolRegistry.ICE_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(30)
            .build();

    public IcestoneMeteorSpell() {
        super("icestone_meteor");
        this.baseManaCost = 80;
        this.manaCostPerLevel = 12;
        this.baseSpellPower = 14;
        this.spellPowerPerLevel = 3;
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
        return Optional.of(SoundRegistry.FROSTWAVE_PREPARE.get());
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundRegistry.ICE_CAST.get());
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.irons_spellbooks.impact_damage", Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)),
                Component.translatable("ui.irons_spellbooks.radius", Utils.stringTruncation(getRadius(spellLevel), 1)),
                Component.translatable("ui.aether_spellbooks.chill_duration", Utils.timeFromTicks(getChillTicks(spellLevel), 1)),
                Component.translatable("ui.aether_spellbooks.freezes_water")
        );
    }

    public static float getRadius(int spellLevel) {
        return 3f + 0.3f * spellLevel;
    }

    public static int getChillTicks(int spellLevel) {
        return 80 + 20 * spellLevel;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel server) {
            Vec3 target = aimPoint(server, caster);
            Vec3 flat = caster.getLookAngle().multiply(1, 0, 1);
            flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
            // falls in from high above, slightly from the caster's side, so the caster sees it coming
            Vec3 spawn = target.add(0, DROP_HEIGHT, 0).subtract(flat.scale(DROP_OFFSET));
            var ceiling = server.clip(new ClipContext(target.add(0, 1, 0), spawn, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (ceiling.getType() != HitResult.Type.MISS) {
                spawn = ceiling.getLocation().subtract(spawn.subtract(target).normalize().scale(1.5));
            }
            IcestoneMeteor meteor = new IcestoneMeteor(level, caster);
            meteor.setPos(spawn);
            meteor.shoot(target.subtract(spawn).normalize());
            meteor.setDamage(getSpellPower(spellLevel, caster));
            meteor.configure(getRadius(spellLevel), getChillTicks(spellLevel));
            level.addFreshEntity(meteor);

            ASFx.burst(level, ParticleHelper.SNOWFLAKE, spawn, 30, 1.2, 0.05);
            ASFx.blastwave(level, ASFx.FROST_CYAN, target, getRadius(spellLevel));
            ASFx.ring(level, ParticleHelper.SNOW_DUST, target.add(0, 0.1, 0), getRadius(spellLevel), 28, 0);
        }
        super.onCast(level, spellLevel, caster, castSource, playerMagicData);
    }

    /** Where the caster is looking: the first block or entity within range, then down to the ground below it. */
    private static Vec3 aimPoint(ServerLevel level, LivingEntity caster) {
        HitResult hit = Utils.raycastForEntity(level, caster, RANGE, true);
        Vec3 point = hit.getLocation();
        var down = level.clip(new ClipContext(point.add(0, 0.5, 0), point.subtract(0, 24, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, caster));
        return down.getType() == HitResult.Type.MISS ? point : down.getLocation();
    }
}
