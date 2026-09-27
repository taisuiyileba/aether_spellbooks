package com.aetherspellbooks.spells;

import com.aetherspellbooks.block.TemporaryClouds;
import com.aetherspellbooks.config.ASConfig;
import com.aetherspellbooks.registry.ASBlocks;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.core.BlockPos;
import com.aetherspellbooks.registry.ASParticles;
import com.aetherspellbooks.util.ASFx;
import com.aetherteam.aether.client.AetherSoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Optional;

/**
 * Conjures a temporary aercloud platform beneath the caster.
 * Level 3+: the centre becomes a bouncy blue aercloud (sneak to land on it).
 * Level 5: the rim becomes golden aercloud.
 */
public class AercloudStepSpell extends AetherSpell {
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.COMMON)
            .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(12)
            .build();

    /** From this level the golden clouds also grant a short slow fall. */
    public static final int SLOW_FALL_LEVEL = 4;

    public AercloudStepSpell() {
        super("aercloud_step");
        this.baseManaCost = 20;
        this.manaCostPerLevel = 5;
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public CastType getCastType() {
        return CastType.INSTANT;
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.SELF_CAST_ANIMATION;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.WOOL_PLACE);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        int size = getRadius(spellLevel) * 2 + 1;
        var info = new java.util.ArrayList<MutableComponent>(List.of(
                Component.translatable("ui.aether_spellbooks.platform_size", size, size),
                Component.translatable("ui.irons_spellbooks.duration", Utils.timeFromTicks(getDurationTicks(spellLevel), 1))));
        if (spellLevel >= 3) {
            info.add(Component.translatable("ui.aether_spellbooks.bouncy_centre"));
        }
        if (spellLevel >= SLOW_FALL_LEVEL) {
            info.add(Component.translatable("ui.aether_spellbooks.slow_fall", Utils.timeFromTicks(60 + 20 * spellLevel, 1)));
        }
        return info;
    }

    public static int getRadius(int spellLevel) {
        return spellLevel >= 3 ? 2 : 1;
    }

    public static int getDurationTicks(int spellLevel) {
        return (int) ((6 + 2 * spellLevel) * 20 * ASConfig.AERCLOUD_DURATION_MULTIPLIER.get());
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            int radius = getRadius(spellLevel);
            int duration = getDurationTicks(spellLevel);
            BlockPos center = BlockPos.containing(caster.getX(), caster.getY() - 0.5, caster.getZ());
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.offset(dx, 0, dz);
                    // stagger removal slightly so the platform dissolves from the edges
                    int jitter = (Math.abs(dx) + Math.abs(dz) == 0) ? 10 : serverLevel.random.nextInt(8);
                    if (TemporaryClouds.place(serverLevel, pos, chooseCloud(spellLevel, radius, dx, dz), duration + jitter)) {
                        serverLevel.sendParticles(ASParticles.CLOUD_PUFF.get(), pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 3, 0.35, 0.1, 0.35, 0.01);
                    }
                }
            }
            caster.resetFallDistance();
            Vec3 feet = caster.position();
            ASFx.ring(serverLevel, ASParticles.CLOUD_PUFF.get(), feet, 0.5, 20, 0.25);
            ASFx.blastwave(serverLevel, ASFx.CLOUD_WHITE, feet.subtract(0, 0.3, 0), radius + 1.5f);
            ASFx.burst(serverLevel, ASParticles.SKY_SPARKLE.get(), feet.add(0, 0.5, 0), 8, 0.6, 0.02);
            serverLevel.playSound(null, feet.x, feet.y, feet.z, AetherSoundEvents.BLOCK_BLUE_AERCLOUD_BOUNCE.get(), SoundSource.PLAYERS, 0.8f, 1.2f);
            if (spellLevel >= SLOW_FALL_LEVEL) {
                caster.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60 + 20 * spellLevel, 0, false, false, true));
            }
        }
        super.onCast(level, spellLevel, caster, castSource, playerMagicData);
    }

    private static Block chooseCloud(int spellLevel, int radius, int dx, int dz) {
        if (spellLevel >= 3 && dx == 0 && dz == 0) {
            return ASBlocks.TEMPORARY_BLUE_AERCLOUD.get();
        }
        if (spellLevel >= 5 && (Math.abs(dx) == radius || Math.abs(dz) == radius)) {
            return ASBlocks.TEMPORARY_GOLDEN_AERCLOUD.get();
        }
        return ASBlocks.TEMPORARY_COLD_AERCLOUD.get();
    }
}
