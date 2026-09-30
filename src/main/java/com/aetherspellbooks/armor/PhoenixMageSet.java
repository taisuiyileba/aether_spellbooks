package com.aetherspellbooks.armor;

import com.aetherspellbooks.registry.ASArmorMaterials;
import com.aetherspellbooks.util.ASFx;
import com.aetherteam.aether.attachment.AetherPlayerAttachment;
import com.aetherteam.aether.client.AetherSoundEvents;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Full Phoenix Mage set.
 * <ul>
 *     <li>Inherited from the Aether's Phoenix armour: immune to fire and lava, flames never stick, swims swiftly
 *     through lava and is wreathed in flame. Unlike the original it does not crumble into obsidian in water;
 *     the fire just hisses into steam.</li>
 *     <li>Rebirth: a fatal blow instead engulfs the wearer in a burst of flame that scorches and repels nearby
 *     enemies and restores 40% health, once every five minutes.</li>
 * </ul>
 * Spell damage is not blocked by the fire immunity (the set's fire magic resistance handles it instead).
 */
public final class PhoenixMageSet {
    public static final int REBIRTH_COOLDOWN_TICKS = 20 * 300;
    public static final float REBIRTH_HEALTH = 0.4f;
    public static final float REBIRTH_RADIUS = 4.5f;
    public static final float REBIRTH_DAMAGE = 6f;
    private static final String REBIRTH_TAG = "aether_spellbooks.phoenix_rebirth_ready_at";

    private PhoenixMageSet() {
    }

    public static boolean isWornBy(LivingEntity entity) {
        return ASArmorMaterials.PHOENIX_MAGE.isWornBy(entity);
    }

    /** Called every tick for wearers of the full set, on both sides (the client moves its own player). */
    public static void tick(LivingEntity entity) {
        entity.clearFire();
        if (entity.isInLava()) {
            entity.resetFallDistance();
            float boost = 1.75f + Math.min(EnchantmentHelper.getEnchantmentLevel(entity.registryAccess().holderOrThrow(net.minecraft.world.item.enchantment.Enchantments.DEPTH_STRIDER), entity), 3);
            entity.moveRelative(0.04f * boost, new Vec3(entity.xxa, entity.yya, entity.zza));
            if (entity instanceof Player player && java.util.Optional.of(player.getData(com.aetherteam.aether.attachment.AetherDataAttachments.AETHER_PLAYER)).map(AetherPlayerAttachment::isJumping).orElse(false)) {
                Vec3 motion = entity.getDeltaMovement();
                entity.setDeltaMovement(motion.x, Math.max(motion.y, 0.12), motion.z);
            }
        }
        if (entity.level() instanceof ServerLevel server) {
            var random = server.getRandom();
            double x = entity.getX() + random.nextGaussian() / 5, y = entity.getY() + entity.getBbHeight() * 0.5 + random.nextGaussian() / 3, z = entity.getZ() + random.nextGaussian() / 5;
            if (entity.isInWaterRainOrBubble()) {
                if (entity.tickCount % 3 == 0) {
                    server.sendParticles(ParticleTypes.CLOUD, x, y, z, 1, 0.1, 0.1, 0.1, 0.01);
                }
            } else {
                server.sendParticles(ParticleTypes.FLAME, x, y, z, 1, 0, 0, 0, 0);
                if (entity.tickCount % 6 == 0) {
                    server.sendParticles(ParticleHelper.EMBERS, x, y, z, 1, 0.2, 0.3, 0.2, 0.02);
                }
            }
        }
    }

    /** Fire and lava cannot harm a full set wearer; fire spells still can. */
    public static boolean blocksDamage(LivingEntity entity, DamageSource source) {
        return source.is(DamageTypeTags.IS_FIRE) && !(source instanceof SpellDamageSource) && isWornBy(entity);
    }

    public static boolean isRebirthReady(LivingEntity entity) {
        if (entity instanceof Player player) {
            return !player.getCooldowns().isOnCooldown(entity.getItemBySlot(EquipmentSlot.CHEST).getItem());
        }
        return entity.level().getGameTime() >= entity.getPersistentData().getLong(REBIRTH_TAG);
    }

    /** Tries to cheat death; returns true when the wearer was reborn (the death must then be cancelled). */
    public static boolean tryRebirth(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel server) || !isWornBy(entity) || !isRebirthReady(entity)) {
            return false;
        }
        if (entity instanceof Player player) {
            // the cooldown shows on every piece of the set
            for (EquipmentSlot slot : ASArmorMaterials.ARMOR_SLOTS) {
                player.getCooldowns().addCooldown(entity.getItemBySlot(slot).getItem(), REBIRTH_COOLDOWN_TICKS);
            }
        } else {
            CompoundTag data = entity.getPersistentData();
            data.putLong(REBIRTH_TAG, server.getGameTime() + REBIRTH_COOLDOWN_TICKS);
        }

        entity.setHealth(entity.getMaxHealth() * REBIRTH_HEALTH);
        List.copyOf(entity.getActiveEffects()).stream()
                .filter(effect -> effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
                .forEach(effect -> entity.removeEffect(effect.getEffect()));
        entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200));
        entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        entity.clearFire();

        DamageSource flame = entity instanceof Player player ? entity.damageSources().playerAttack(player) : entity.damageSources().mobAttack(entity);
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(REBIRTH_RADIUS),
                t -> t != entity && t.isAlive() && !t.isSpectator() && !t.isAlliedTo(entity) && !entity.isAlliedTo(t))) {
            target.hurt(flame, REBIRTH_DAMAGE);
            target.igniteForSeconds(6);
            Vec3 away = target.position().subtract(entity.position()).multiply(1, 0, 1);
            if (away.lengthSqr() > 1.0E-4) {
                target.knockback(1.2, -away.x, -away.z);
            }
        }

        Vec3 center = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
        ASFx.blastwave(server, ASFx.SOLAR_ORANGE, entity.position(), REBIRTH_RADIUS + 0.5f);
        ASFx.burst(server, ParticleHelper.FIRE, center, 80, 0.5, 0.25);
        ASFx.burst(server, ParticleHelper.EMBERS, center, 40, 0.8, 0.12);
        ASFx.helix(server, ParticleHelper.FIRE, entity.position(), 1.1, entity.getBbHeight() + 1.5, 40, 3);
        ASFx.shake(server, entity.position(), 12, 10);
        server.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 1.3f);
        server.playSound(null, entity.getX(), entity.getY(), entity.getZ(), AetherSoundEvents.ENTITY_SUN_SPIRIT_SHOOT_FIRE.get(), SoundSource.PLAYERS, 1.2f, 0.7f);
        return true;
    }
}
