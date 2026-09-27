package com.aetherspellbooks.entity;

import com.aetherspellbooks.registry.ASEntities;
import com.aetherspellbooks.registry.ASParticles;
import com.aetherspellbooks.registry.ASSpells;
import com.aetherteam.aether.client.AetherSoundEvents;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

/**
 * A roaming whirlwind: drifts forward, steers toward nearby enemies, and drags them into
 * a rising spiral while buffeting them with damage.
 */
public class AetherWhirlwind extends OwnedSpellEntity {
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(AetherWhirlwind.class, EntityDataSerializers.FLOAT);
    public static final float HEIGHT = 4.5f;
    private static final double SPEED = 0.16;
    private static final int DAMAGE_INTERVAL = 10;

    private Vec3 heading = new Vec3(0, 0, 1);

    public AetherWhirlwind(EntityType<? extends AetherWhirlwind> type, Level level) {
        super(type, level);
    }

    public AetherWhirlwind(Level level, LivingEntity owner, Vec3 heading) {
        this(ASEntities.AETHER_WHIRLWIND.get(), level);
        setOwner(owner);
        Vec3 flat = heading.multiply(1, 0, 1);
        this.heading = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_RADIUS, 2.5f);
    }

    public void setRadius(float radius) {
        entityData.set(DATA_RADIUS, radius);
    }

    public float getRadius() {
        return entityData.get(DATA_RADIUS);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            spawnFunnelParticles();
            return;
        }
        if (tickCount % 10 == 1) {
            steerTowardEnemies();
        }
        Vec3 next = position().add(heading.scale(SPEED));
        // hug the terrain, but float across gaps between islands
        Vec3 grounded = Utils.moveToRelativeGroundLevel(level(), next, 2);
        setPos(grounded.y > next.y - 3 && grounded.y < next.y + 3 ? grounded : next);

        float radius = getRadius();
        AABB area = new AABB(getX() - radius, getY() - 0.5, getZ() - radius, getX() + radius, getY() + HEIGHT, getZ() + radius);
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, area, this::canAffect)) {
            Vec3 toAxis = new Vec3(getX() - target.getX(), 0, getZ() - target.getZ());
            double distance = toAxis.length();
            if (distance > radius) {
                continue;
            }
            Vec3 inward = distance < 1.0E-3 ? Vec3.ZERO : toAxis.scale(1 / distance);
            Vec3 tangent = new Vec3(-inward.z, 0, inward.x);
            double resist = 1 - target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
            Vec3 motion = target.getDeltaMovement()
                    .add(inward.scale(0.07 * resist))
                    .add(tangent.scale(0.11 * resist));
            double lift = Math.min(0.42, motion.y + 0.1 * resist);
            target.setDeltaMovement(motion.x * 0.9, lift, motion.z * 0.9);
            target.hurtMarked = true;
            target.resetFallDistance();
            if ((tickCount + target.getId()) % DAMAGE_INTERVAL == 0) {
                DamageSources.applyDamage(target, damage, ASSpells.AETHER_WHIRLWIND.get().getDamageSource(this, getOwner()));
            }
        }
        if (tickCount % 40 == 1) {
            level().playSound(null, getX(), getY(), getZ(), AetherSoundEvents.ENTITY_ZEPHYR_AMBIENT.get(), SoundSource.NEUTRAL, 0.8f, 1.4f);
        }
    }

    private void steerTowardEnemies() {
        float radius = getRadius();
        level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(10), t -> canAffect(t) && t instanceof net.minecraft.world.entity.monster.Enemy)
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .ifPresent(target -> {
                    Vec3 toTarget = target.position().subtract(position()).multiply(1, 0, 1);
                    if (toTarget.length() > radius * 0.5) {
                        heading = heading.lerp(toTarget.normalize(), 0.35).normalize();
                    }
                });
    }

    private void spawnFunnelParticles() {
        float radius = getRadius();
        for (int i = 0; i < 6; i++) {
            float heightT = random.nextFloat();
            double height = heightT * HEIGHT;
            double r = radius * (0.25 + 0.75 * heightT);
            double angle = tickCount * 0.45 + i * (Mth.TWO_PI / 6) + heightT * 4;
            double x = getX() + Math.cos(angle) * r;
            double z = getZ() + Math.sin(angle) * r;
            double vx = -Math.sin(angle) * 0.18;
            double vz = Math.cos(angle) * 0.18;
            level().addParticle(ASParticles.CLOUD_PUFF.get(), x, getY() + height, z, vx, 0.03, vz);
        }
        if (random.nextInt(3) == 0) {
            level().addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(),
                    (random.nextDouble() - 0.5) * 0.3, 0.02, (random.nextDouble() - 0.5) * 0.3);
        }
    }
}
