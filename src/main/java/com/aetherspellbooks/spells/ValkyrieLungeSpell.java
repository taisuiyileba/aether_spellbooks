package com.aetherspellbooks.spells;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.registry.ASParticles;
import com.aetherspellbooks.util.ASFx;
import com.aetherspellbooks.util.ASScheduler;
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
import io.redspace.ironsspellbooks.capabilities.magic.PlayerRecasts;
import io.redspace.ironsspellbooks.capabilities.magic.RecastInstance;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.particle.SparkParticleOptions;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A Valkyrie's charge, cast as a chain of dashes (2-4 by level; recast within 3 seconds, mana is paid once).
 * <ul>
 *     <li>Every dash strikes each foe in its path once and <b>brands</b> it for 4 seconds (it glows).</li>
 *     <li>Dashing through a branded foe consumes the brand for +50% damage - weave back and forth through a group.</li>
 *     <li>The last dash of the chain ends in a <b>Valkyrie's Verdict</b>: a golden shockwave where the caster lands
 *     that launches nearby foes (and consumes their brands too). Cast it airborne while looking down to dive.</li>
 * </ul>
 * Mobs dash once per cast.
 */
public class ValkyrieLungeSpell extends AetherSpell {
    public static final int RECAST_WINDOW_TICKS = 60;
    public static final int BRAND_TICKS = 80;
    public static final float BRAND_BONUS = 0.5f;
    public static final float VERDICT_DAMAGE_RATIO = 0.8f;
    private static final String BRAND_KEY = AetherSpellbooks.MODID + ":valkyrie_brand";
    private static final SparkParticleOptions GOLD_SPARK = new SparkParticleOptions(ASFx.VALKYRIE_GOLD);

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.UNCOMMON)
            .setSchoolResource(SchoolRegistry.HOLY_RESOURCE)
            .setMaxLevel(6)
            .setCooldownSeconds(10)
            .build();

    public ValkyrieLungeSpell() {
        super("valkyrie_lunge");
        this.baseManaCost = 30;
        this.manaCostPerLevel = 5;
        this.baseSpellPower = 6;
        this.spellPowerPerLevel = 2;
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
        return SpellAnimations.ONE_HANDED_HORIZONTAL_SWING_ANIMATION;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.PLAYER_ATTACK_SWEEP);
    }

    @Override
    public int getRecastCount(int spellLevel, @Nullable LivingEntity entity) {
        return spellLevel >= 6 ? 4 : spellLevel >= 3 ? 3 : 2;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        float damage = getDamage(spellLevel, caster);
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(damage, 1)),
                Component.translatable("ui.irons_spellbooks.distance", Utils.stringTruncation(getDistance(spellLevel), 0)),
                Component.translatable("ui.irons_spellbooks.recast_count", getRecastCount(spellLevel, caster)),
                Component.translatable("ui.aether_spellbooks.brand_bonus", (int) (BRAND_BONUS * 100)),
                Component.translatable("ui.aether_spellbooks.verdict", Utils.stringTruncation(damage * VERDICT_DAMAGE_RATIO, 1),
                        Utils.stringTruncation(getVerdictRadius(spellLevel), 1))
        );
    }

    public static float getDistance(int spellLevel) {
        return 6 + spellLevel;
    }

    public static float getVerdictRadius(int spellLevel) {
        return 2.5f + 0.25f * spellLevel;
    }

    public float getDamage(int spellLevel, LivingEntity caster) {
        return getSpellPower(spellLevel, caster);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel server) {
            boolean finalDash = false;
            int dashIndex = 0;
            if (caster instanceof ServerPlayer) {
                PlayerRecasts recasts = playerMagicData.getPlayerRecasts();
                int total = getRecastCount(spellLevel, caster);
                if (!recasts.hasRecastForSpell(getSpellId())) {
                    recasts.addRecast(new RecastInstance(getSpellId(), spellLevel, total, RECAST_WINDOW_TICKS, castSource, null), playerMagicData);
                    finalDash = total <= 1;
                } else {
                    // Iron's Spells decrements the count after onCast, so 1 remaining means this is the last dash
                    int remaining = recasts.getRemainingRecastsForSpell(getSpellId());
                    finalDash = remaining <= 1;
                    dashIndex = total - remaining;
                }
            }
            dash(server, spellLevel, caster, finalDash, dashIndex);
        }
        super.onCast(level, spellLevel, caster, castSource, playerMagicData);
    }

    private void dash(ServerLevel server, int spellLevel, LivingEntity caster, boolean finalDash, int dashIndex) {
        Vec3 look = caster.getLookAngle();
        // the finishing dash may dive steeply when cast in mid-air; the others stay close to level
        boolean dive = finalDash && !caster.onGround() && look.y < -0.35;
        Vec3 direction = new Vec3(look.x, Mth.clamp(look.y, dive ? -0.95 : -0.3, 0.45), look.z).normalize();
        Vec3 start = caster.position().add(0, caster.getBbHeight() * 0.5, 0);
        Vec3 end = start.add(direction.scale(getDistance(spellLevel) * (dive ? 1.4 : 1)));
        var clip = server.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (clip.getType() != HitResult.Type.MISS) {
            end = clip.getLocation().subtract(direction.scale(0.6));
        }
        double distance = start.distanceTo(end);

        // strike everything along the path, once each
        float damage = getDamage(spellLevel, caster);
        Set<LivingEntity> struck = new HashSet<>();
        for (double d = 0; d <= distance; d += 0.5) {
            Vec3 p = start.add(direction.scale(d));
            for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(1.1), t -> canHit(caster, t))) {
                if (struck.add(target)) {
                    strike(server, caster, target, damage, direction);
                }
            }
        }

        // golden wings flare behind the caster, then a trail of light and feathers
        wingFlare(server, caster, direction);
        ASFx.line(server, ASParticles.SKY_SPARKLE.get(), start, end, (int) (distance * 3), 0.25);
        ASFx.line(server, ASParticles.FEATHER.get(), start, end, (int) (distance * 1.2), 0.4);
        ASFx.blastwave(server, ASFx.VALKYRIE_GOLD, caster.position(), 1.5f);

        // the dash itself
        caster.setDeltaMovement(direction.scale(0.45 * distance / 2.5).add(0, dive ? 0 : 0.12, 0));
        caster.hurtMarked = true;
        caster.resetFallDistance();
        if (!dive) {
            caster.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 50, 0, false, false, true));
        }
        // each dash of a chain rings a little higher
        server.playSound(null, caster.getX(), caster.getY(), caster.getZ(), AetherSoundEvents.ITEM_ARMOR_EQUIP_VALKYRIE.get(), SoundSource.PLAYERS,
                1f, 1.1f + 0.12f * dashIndex);
        if (finalDash) {
            awaitVerdict(server, caster, spellLevel, damage * VERDICT_DAMAGE_RATIO, 0);
        }
    }

    private static boolean canHit(LivingEntity caster, LivingEntity target) {
        return target != caster && target.isAlive() && !target.isSpectator() && !target.isAlliedTo(caster) && !caster.isAlliedTo(target);
    }

    private void strike(ServerLevel server, LivingEntity caster, LivingEntity target, float damage, Vec3 direction) {
        boolean branded = consumeBrand(server, target);
        if (DamageSources.applyDamage(target, branded ? damage * (1 + BRAND_BONUS) : damage, getDamageSource(caster))) {
            target.knockback(0.6, -direction.x, -direction.z);
            Vec3 c = target.getBoundingBox().getCenter();
            server.sendParticles(GOLD_SPARK, c.x, c.y, c.z, 12, 0.2, 0.2, 0.2, 0.4);
            server.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            if (branded) {
                judgement(server, target);
            } else {
                brand(server, target);
            }
        }
    }

    // ------------------------------------------------------------------ brands
    public static void brand(ServerLevel server, LivingEntity target) {
        target.getPersistentData().putLong(BRAND_KEY, server.getGameTime() + BRAND_TICKS);
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, BRAND_TICKS, 0, false, false, false));
    }

    public static boolean isBranded(ServerLevel server, LivingEntity target) {
        CompoundTag data = target.getPersistentData();
        return data.contains(BRAND_KEY) && data.getLong(BRAND_KEY) >= server.getGameTime();
    }

    private static boolean consumeBrand(ServerLevel server, LivingEntity target) {
        if (!isBranded(server, target)) {
            return false;
        }
        target.getPersistentData().remove(BRAND_KEY);
        target.removeEffect(MobEffects.GLOWING);
        return true;
    }

    /** The flash when a brand is consumed: a burst of gold and a bell-like strike. */
    private static void judgement(ServerLevel server, LivingEntity target) {
        Vec3 c = target.getBoundingBox().getCenter();
        server.sendParticles(ASParticles.SKY_SPARKLE.get(), c.x, c.y, c.z, 24, 0.35, 0.5, 0.35, 0.08);
        server.sendParticles(GOLD_SPARK, c.x, c.y, c.z, 20, 0.1, 0.1, 0.1, 0.7);
        ASFx.blastwave(server, ASFx.VALKYRIE_GOLD, target.position().add(0, target.getBbHeight() * 0.5, 0), 1.2f);
        server.playSound(null, c.x, c.y, c.z, SoundRegistry.GUIDING_BOLT_IMPACT.get(), SoundSource.PLAYERS, 0.9f, 1.3f);
    }

    // ------------------------------------------------------------------ verdict
    /** Waits (a tick at a time) until the caster lands or the dash is spent, then strikes. */
    private void awaitVerdict(ServerLevel server, LivingEntity caster, int spellLevel, float damage, int tick) {
        ASScheduler.schedule(1, () -> {
            if (!caster.isAlive() || caster.level() != server) {
                return;
            }
            caster.resetFallDistance();
            if ((tick >= 2 && caster.onGround()) || tick >= 14) {
                verdict(server, caster, spellLevel, damage);
            } else {
                awaitVerdict(server, caster, spellLevel, damage, tick + 1);
            }
        });
    }

    private void verdict(ServerLevel server, LivingEntity caster, int spellLevel, float damage) {
        float radius = getVerdictRadius(spellLevel);
        Vec3 center = caster.position();
        AABB area = new AABB(center, center).inflate(radius, 2, radius);
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, area, t -> canHit(caster, t)
                && t.position().multiply(1, 0, 1).distanceToSqr(center.multiply(1, 0, 1)) <= radius * radius)) {
            boolean branded = consumeBrand(server, target);
            if (DamageSources.applyDamage(target, branded ? damage * (1 + BRAND_BONUS) : damage, getDamageSource(caster))) {
                Vec3 away = target.position().subtract(center).multiply(1, 0, 1);
                away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize();
                target.setDeltaMovement(target.getDeltaMovement().add(away.scale(0.45)).add(0, 0.55, 0));
                target.hurtMarked = true;
                if (branded) {
                    judgement(server, target);
                }
            }
        }
        // a golden shockwave, a ring of feathers flung outward and a column of light
        ASFx.blastwave(server, ASFx.VALKYRIE_GOLD, center, radius);
        ASFx.blastwave(server, ASFx.CLOUD_WHITE, center, radius * 0.6f);
        ASFx.ring(server, ASParticles.FEATHER.get(), center.add(0, 0.4, 0), 0.6, 24, 0.35);
        ASFx.ring(server, ASParticles.SKY_SPARKLE.get(), center.add(0, 0.2, 0), radius * 0.5, 32, 0.25);
        ASFx.helix(server, ASParticles.SKY_SPARKLE.get(), center, 0.6, 4.5, 40, 2);
        server.sendParticles(GOLD_SPARK, center.x, center.y + 0.3, center.z, 30, radius * 0.3, 0.1, radius * 0.3, 0.6);
        ASFx.shake(server, center, 12, 8);
        server.playSound(null, center.x, center.y, center.z, SoundRegistry.DIVINE_SMITE_CAST.get(), SoundSource.PLAYERS, 1.1f, 1.1f);
        server.playSound(null, center.x, center.y, center.z, AetherSoundEvents.ENTITY_THUNDER_CRYSTAL_EXPLODE.get(), SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    /** Two fans of feathers and light spreading behind the caster like wings. */
    private static void wingFlare(ServerLevel server, LivingEntity caster, Vec3 direction) {
        Vec3 flat = new Vec3(direction.x, 0, direction.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 right = new Vec3(-flat.z, 0, flat.x);
        Vec3 back = caster.position().add(0, caster.getBbHeight() * 0.7, 0).subtract(flat.scale(0.4));
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 8; i++) {
                double t = i / 7.0;
                Vec3 p = back.add(right.scale(side * (0.3 + 1.3 * t))).add(0, 0.9 * Math.sin(t * Math.PI * 0.8) - 0.2 * t, 0);
                server.sendParticles(i % 2 == 0 ? ASParticles.FEATHER.get() : ASParticles.SKY_SPARKLE.get(), p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
            }
        }
    }
}
