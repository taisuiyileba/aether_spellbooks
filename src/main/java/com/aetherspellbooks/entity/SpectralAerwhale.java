package com.aetherspellbooks.entity;

import com.aetherspellbooks.registry.ASEntities;
import com.aetherspellbooks.registry.ASParticles;
import com.aetherspellbooks.registry.ASSpells;
import com.aetherspellbooks.util.ASFx;
import com.aetherteam.aether.client.AetherSoundEvents;
import com.aetherteam.aether.entity.passive.Aerwhale;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A spectral Aerwhale called by Aerwhale Song. It swims in a straight line across the sky and sings every half second:
 * each song rolls down to the ground beneath it, hurting and tossing up foes and lifting allies with Slow Falling and
 * Jump Boost. Reuses the Aether's Aerwhale model and renderer (drawn translucent); it has no AI, cannot be hurt or
 * interacted with, and is never saved.
 */
public class SpectralAerwhale extends Aerwhale {
    private static final EntityDataAccessor<Integer> DATA_LIFETIME = SynchedEntityData.defineId(SpectralAerwhale.class, EntityDataSerializers.INT);
    public static final int FADE_IN = 12;
    public static final int FADE_OUT = 16;
    public static final int SONG_INTERVAL = 10;
    public static final float SONG_DEPTH = 14f;
    public static final int BLESSING_TICKS = 80;

    private Vec3 heading = new Vec3(0, 0, 1);
    private double speed = 0.5;
    private float damage;
    private float songRadius = 4f;
    @Nullable
    private UUID ownerId;
    @Nullable
    private LivingEntity cachedOwner;

    public SpectralAerwhale(EntityType<? extends Aerwhale> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.xpReward = 0;
        setNoGravity(true);
        setInvulnerable(true);
    }

    public SpectralAerwhale(Level level, LivingEntity owner, Vec3 heading, int lifetime) {
        this(ASEntities.SPECTRAL_AERWHALE.get(), level);
        this.ownerId = owner.getUUID();
        this.cachedOwner = owner;
        Vec3 flat = heading.multiply(1, 0, 1);
        this.heading = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        entityData.set(DATA_LIFETIME, lifetime);
        float yaw = (float) (Mth.atan2(-this.heading.x, this.heading.z) * Mth.RAD_TO_DEG);
        setYRotData(yaw);
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    public void configure(float damage, float songRadius, double speed) {
        this.damage = damage;
        this.songRadius = songRadius;
        this.speed = speed;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LIFETIME, 80);
    }

    public int getLifetime() {
        return entityData.get(DATA_LIFETIME);
    }

    /** Opacity for the renderer: fades in when summoned and out at the end of its swim. */
    public float getFade(float partialTick) {
        float age = tickCount + partialTick;
        return Mth.clamp(age / FADE_IN, 0f, 1f) * Mth.clamp((getLifetime() - age) / FADE_OUT, 0f, 1f);
    }

    @Nullable
    public LivingEntity getOwner() {
        if (cachedOwner != null && !cachedOwner.isRemoved()) {
            return cachedOwner;
        }
        if (ownerId != null && level() instanceof ServerLevel server && server.getEntity(ownerId) instanceof LivingEntity living) {
            cachedOwner = living;
        }
        return cachedOwner;
    }

    // ------------------------------------------------------------------ no AI: a scripted swim
    @Override
    public void registerGoals() {
    }

    @Override
    public void travel(@NotNull Vec3 input) {
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            trailParticles();
            return;
        }
        // a gentle rise and dip of the nose while it swims
        setXRotData(Mth.sin(tickCount * 0.15f) * 6f);
        setPos(position().add(heading.scale(speed)).add(0, Mth.cos(tickCount * 0.15f) * 0.02, 0));
        if (tickCount >= 6 && tickCount % SONG_INTERVAL == 0 && tickCount < getLifetime() - FADE_OUT / 2) {
            sing();
        }
        if (tickCount >= getLifetime()) {
            discard();
        }
    }

    public void sing() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        LivingEntity owner = getOwner();
        Vec3 c = position();
        var clip = server.clip(new ClipContext(c, c.subtract(0, SONG_DEPTH, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        double groundY = clip.getType() == HitResult.Type.MISS ? c.y - SONG_DEPTH : clip.getLocation().y;
        AABB area = new AABB(c.x - songRadius, groundY - 1, c.z - songRadius, c.x + songRadius, c.y + 1.5, c.z + songRadius);
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, area, t -> t != this && t.isAlive() && !t.isSpectator()
                && !(t instanceof SpectralAerwhale) && t.position().multiply(1, 0, 1).distanceToSqr(c.multiply(1, 0, 1)) <= songRadius * songRadius)) {
            boolean ally = owner != null && (target == owner || target.isAlliedTo(owner) || owner.isAlliedTo(target));
            if (ally) {
                // the whale's wake: float down gently and leap high
                target.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, BLESSING_TICKS, 0, false, false, true));
                target.addEffect(new MobEffectInstance(MobEffects.JUMP, BLESSING_TICKS, 1, false, false, true));
            } else if (DamageSources.applyDamage(target, damage, ASSpells.AERWHALE_SONG.get().getDamageSource(this, owner))) {
                target.setDeltaMovement(target.getDeltaMovement().x * 0.6, Math.max(target.getDeltaMovement().y, 0.55), target.getDeltaMovement().z * 0.6);
                target.hurtMarked = true;
                target.addEffect(new MobEffectInstance(MobEffectRegistry.SLOWED, 30, 0));
                server.sendParticles(ASParticles.CLOUD_PUFF.get(), target.getX(), target.getY() + 0.3, target.getZ(), 6, 0.3, 0.1, 0.3, 0.05);
            }
        }
        // the song rolls down to the ground: rings there, notes at the whale's head
        Vec3 ground = new Vec3(c.x, groundY, c.z);
        ASFx.blastwave(server, ASFx.FROST_CYAN, ground, songRadius);
        ASFx.ring(server, ASParticles.CLOUD_PUFF.get(), ground.add(0, 0.2, 0), songRadius * 0.5, 20, 0.25);
        ASFx.line(server, ASParticles.SKY_SPARKLE.get(), c, ground, (int) Math.max(4, (c.y - groundY) * 1.5), 0.6);
        Vec3 head = c.add(heading.scale(2.2)).add(0, 1.2, 0);
        for (int i = 0; i < 3; i++) {
            server.sendParticles(ParticleTypes.NOTE, head.x, head.y + i * 0.3, head.z, 0, random.nextFloat(), 0, 0, 1);
        }
        if (tickCount % (SONG_INTERVAL * 2) == 0) {
            server.playSound(null, c.x, c.y, c.z, AetherSoundEvents.ENTITY_AERWHALE_AMBIENT.get(), SoundSource.PLAYERS, 2.5f, 0.75f + random.nextFloat() * 0.35f);
        }
    }

    private void trailParticles() {
        float fade = getFade(0);
        if (fade <= 0.05f) {
            return;
        }
        Vec3 back = position().subtract(heading.scale(2.5));
        if (random.nextFloat() < fade) {
            level().addParticle(ASParticles.CLOUD_PUFF.get(), back.x + (random.nextDouble() - 0.5) * 2, back.y + 0.8 + (random.nextDouble() - 0.5), back.z + (random.nextDouble() - 0.5) * 2,
                    0, 0.01, 0);
        }
        for (int i = 0; i < 2; i++) {
            level().addParticle(ASParticles.SKY_SPARKLE.get(), getX() + (random.nextDouble() - 0.5) * 4, getY() + random.nextDouble() * 2.5, getZ() + (random.nextDouble() - 0.5) * 4,
                    0, 0.01, 0);
        }
    }

    // ------------------------------------------------------------------ untouchable, silent, transient
    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(@NotNull DamageSource source) {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    protected @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    public void checkDespawn() {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
    }
}
