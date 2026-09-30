package com.aetherspellbooks.entity;

import com.aetherspellbooks.registry.ASEntities;
import com.aetherspellbooks.registry.ASParticles;
import com.aetherspellbooks.registry.ASSpells;
import com.aetherspellbooks.util.ASFx;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import io.redspace.ironsspellbooks.particle.SparkParticleOptions;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The golden spear of Radiant Javelin (rendered as the Aether's Valkyrie Lance). It flies fast and nearly straight and
 * pierces every foe in its path; where it lands it plants itself, then erupts in a column of holy light that damages
 * foes and heals allies around it. A javelin that hits nothing erupts in mid-air after two seconds.
 */
public class RadiantJavelin extends AbstractMagicProjectile {
    private static final EntityDataAccessor<Boolean> DATA_PLANTED = SynchedEntityData.defineId(RadiantJavelin.class, EntityDataSerializers.BOOLEAN);
    public static final int ERUPT_DELAY = 10;
    public static final int MAX_FLIGHT_TICKS = 40;
    private static final SparkParticleOptions GOLD_SPARK = new SparkParticleOptions(ASFx.VALKYRIE_GOLD);

    private final Set<UUID> alreadyHit = new HashSet<>();
    private float eruptionRadius = 3f;
    private float eruptionDamage;
    private float healing;
    private int plantedTicks;
    /** Client: the last flight direction, so a planted spear keeps its angle. */
    public Vec3 renderDirection = new Vec3(0, 0, 1);

    public RadiantJavelin(EntityType<? extends RadiantJavelin> type, Level level) {
        super(type, level);
        setInfinitePiercing();
    }

    public RadiantJavelin(Level level, LivingEntity shooter) {
        this(ASEntities.RADIANT_JAVELIN.get(), level);
        setOwner(shooter);
    }

    public void setEruption(float radius, float damage, float healing) {
        this.eruptionRadius = radius;
        this.eruptionDamage = damage;
        this.healing = healing;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PLANTED, false);
    }

    public boolean isPlanted() {
        return entityData.get(DATA_PLANTED);
    }

    public int getPlantedTicks() {
        return plantedTicks;
    }

    @Override
    public void tick() {
        if (isPlanted()) {
            // stay where it struck: no movement or collision any more
            setDeltaMovement(Vec3.ZERO);
            plantedTicks++;
            if (level().isClientSide) {
                plantedParticles();
            } else if (plantedTicks >= ERUPT_DELAY) {
                erupt();
                discard();
            }
            return;
        }
        super.tick();
        if (level().isClientSide) {
            Vec3 motion = getDeltaMovement();
            if (motion.lengthSqr() > 1.0E-4) {
                renderDirection = motion.normalize();
            }
        } else if (tickCount > MAX_FLIGHT_TICKS) {
            plant(position());
        }
    }

    @Override
    public void trailParticles() {
        Vec3 back = getDeltaMovement().scale(-0.5);
        Vec3 jitter = Utils.getRandomVec3(0.08);
        level().addParticle(ASParticles.SKY_SPARKLE.get(), getX() + jitter.x, getY() + 0.1 + jitter.y, getZ() + jitter.z, back.x * 0.1, back.y * 0.1, back.z * 0.1);
        if (tickCount % 2 == 0) {
            level().addParticle(ParticleTypes.END_ROD, getX() + back.x, getY() + 0.1 + back.y, getZ() + back.z, 0, 0, 0);
        }
        if (random.nextInt(3) == 0) {
            level().addParticle(ASParticles.FEATHER.get(), getX(), getY() + 0.1, getZ(), jitter.x, 0, jitter.z);
        }
    }

    private void plantedParticles() {
        // gathering light around the spear before it erupts
        double r = eruptionRadius * (1 - plantedTicks / (double) ERUPT_DELAY);
        for (int i = 0; i < 3; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            level().addParticle(ASParticles.SKY_SPARKLE.get(), getX() + Math.cos(a) * r, getY() + 0.1, getZ() + Math.sin(a) * r, 0, 0.04, 0);
        }
        level().addParticle(ParticleTypes.END_ROD, getX(), getY() + 0.3, getZ(), 0, 0.06, 0);
    }

    @Override
    public void impactParticles(double x, double y, double z) {
        MagicManager.spawnParticles(level(), GOLD_SPARK, x, y, z, 10, 0.1, 0.1, 0.1, 0.5, false);
        MagicManager.spawnParticles(level(), ASParticles.SKY_SPARKLE.get(), x, y, z, 6, 0.2, 0.2, 0.2, 0.05, false);
    }

    @Override
    public float getSpeed() {
        return 2.2f;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.012;
    }

    @Override
    public Optional<net.minecraft.core.Holder<SoundEvent>> getImpactSound() {
        return Optional.of(net.minecraft.core.Holder.direct(SoundEvents.TRIDENT_HIT));
    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult result) {
        super.onHitBlock(result);
        // bury the tip a little so the spear stands in the block it struck
        plant(result.getLocation().add(getDeltaMovement().normalize().scale(0.25)));
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult result) {
        Entity target = result.getEntity();
        if (!alreadyHit.add(target.getUUID())) {
            return;
        }
        super.onHitEntity(result);
        if (DamageSources.applyDamage(target, damage, ASSpells.RADIANT_JAVELIN.get().getDamageSource(this, getOwner()))) {
            target.setDeltaMovement(target.getDeltaMovement().add(getDeltaMovement().normalize().scale(0.25)).add(0, 0.1, 0));
            target.hurtMarked = true;
        }
        consumeEntityImpact(result, true);
    }

    private void plant(Vec3 at) {
        if (isPlanted()) {
            return;
        }
        setPos(at);
        setDeltaMovement(Vec3.ZERO);
        entityData.set(DATA_PLANTED, true);
        plantedTicks = 0;
        level().playSound(null, at.x, at.y, at.z, SoundRegistry.DIVINE_SMITE_WINDUP.get(), SoundSource.PLAYERS, 0.8f, 1.3f);
    }

    /** The column of light: damages and lifts foes, heals the caster and their allies. */
    public void erupt() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        LivingEntity owner = getOwner() instanceof LivingEntity living ? living : null;
        Vec3 c = position();
        AABB area = new AABB(c, c).inflate(eruptionRadius, 3, eruptionRadius);
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, area,
                t -> t.isAlive() && !t.isSpectator() && t.position().multiply(1, 0, 1).distanceToSqr(c.multiply(1, 0, 1)) <= eruptionRadius * eruptionRadius)) {
            boolean ally = owner != null && (target == owner || target.isAlliedTo(owner) || owner.isAlliedTo(target));
            if (ally) {
                if (healing > 0 && target.getHealth() < target.getMaxHealth()) {
                    target.heal(healing);
                    server.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + target.getBbHeight() + 0.3, target.getZ(), 2, 0.3, 0.1, 0.3, 0);
                }
            } else if (DamageSources.applyDamage(target, eruptionDamage, ASSpells.RADIANT_JAVELIN.get().getDamageSource(this, owner))) {
                target.setDeltaMovement(target.getDeltaMovement().x * 0.5, Math.max(target.getDeltaMovement().y, 0.45), target.getDeltaMovement().z * 0.5);
                target.hurtMarked = true;
            }
        }
        // a pillar of light rising from the spear, a golden shockwave and a ring of sparkles
        for (int i = 0; i < 28; i++) {
            double y = i * 0.25;
            server.sendParticles(ParticleTypes.END_ROD, c.x, c.y + y, c.z, 1, 0.12, 0.05, 0.12, 0.01);
            server.sendParticles(ASParticles.SKY_SPARKLE.get(), c.x, c.y + y, c.z, 1, 0.35, 0.1, 0.35, 0.02);
        }
        ASFx.helix(server, ASParticles.SKY_SPARKLE.get(), c, eruptionRadius * 0.45, 5, 36, 2);
        ASFx.blastwave(server, ASFx.VALKYRIE_GOLD, c, eruptionRadius);
        ASFx.blastwave(server, ASFx.CLOUD_WHITE, c, eruptionRadius * 0.55f);
        ASFx.ring(server, ASParticles.SKY_SPARKLE.get(), c.add(0, 0.2, 0), eruptionRadius * 0.5, 28, 0.3);
        server.sendParticles(GOLD_SPARK, c.x, c.y + 0.5, c.z, 24, 0.2, 0.4, 0.2, 0.7);
        ASFx.shake(server, c, 8, 6);
        server.playSound(null, c.x, c.y, c.z, SoundRegistry.SUNBEAM_IMPACT.get(), SoundSource.PLAYERS, 1.2f, 1.15f);
    }
}
