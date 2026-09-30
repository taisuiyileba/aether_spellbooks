package com.aetherspellbooks.entity;

import com.aetherspellbooks.registry.ASEntities;
import com.aetherspellbooks.registry.ASParticles;
import com.aetherspellbooks.registry.ASSpells;
import com.aetherspellbooks.util.ASFx;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A dark thundercloud that drifts after nearby enemies and calls lightning down on whoever stands beneath it.
 * Each strike arcs on to one more foe close by; targets standing in water or rain take extra damage.
 */
public class StormCloud extends OwnedSpellEntity {
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(StormCloud.class, EntityDataSerializers.FLOAT);
    public static final float HOVER_HEIGHT = 6f;
    public static final float SEEK_RANGE = 16f;
    public static final float WET_MULTIPLIER = 1.5f;
    public static final float CHAIN_RATIO = 0.5f;
    public static final float CHAIN_RANGE = 4.5f;
    private static final double SPEED = 0.16;

    private int strikeInterval = 24;
    private int nextStrike = 16;

    public StormCloud(EntityType<? extends StormCloud> type, Level level) {
        super(type, level);
    }

    public StormCloud(Level level, LivingEntity owner) {
        this(ASEntities.STORM_CLOUD.get(), level);
        setOwner(owner);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 4.5f);
    }

    public void setRadius(float radius) {
        entityData.set(DATA_RADIUS, radius);
    }

    public float getRadius() {
        return entityData.get(DATA_RADIUS);
    }

    public void setStrikeInterval(int ticks) {
        this.strikeInterval = Math.max(8, ticks);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            cloudParticles();
            return;
        }
        drift();
        if (tickCount >= nextStrike) {
            nextStrike = tickCount + strikeInterval;
            pickStrikeTarget().ifPresent(this::strike);
        }
        if (tickCount % 50 == 5) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.35f, 1.6f + random.nextFloat() * 0.3f);
        }
    }

    // ------------------------------------------------------------------ movement
    private void drift() {
        Vec3 pos = position();
        Optional<LivingEntity> quarry = nearestEnemy(SEEK_RANGE);
        Vec3 flat = Vec3.ZERO;
        if (quarry.isPresent()) {
            Vec3 to = quarry.get().position().subtract(pos).multiply(1, 0, 1);
            if (to.length() > 0.8) {
                flat = to.normalize().scale(Math.min(SPEED, to.length()));
            }
        }
        // hover a fixed height over the ground below (but never pass through a ceiling)
        double groundY = groundBelow(pos);
        double wanted = groundY + HOVER_HEIGHT;
        double dy = Math.max(-0.1, Math.min(0.1, wanted - pos.y));
        Vec3 next = pos.add(flat.x, dy, flat.z);
        var ceiling = level().clip(new ClipContext(pos, next.add(0, 1.2, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (ceiling.getType() == HitResult.Type.MISS) {
            setPos(next);
        } else {
            setPos(pos.add(flat.x, Math.min(0, dy), flat.z));
        }
    }

    private double groundBelow(Vec3 pos) {
        var clip = level().clip(new ClipContext(pos, pos.subtract(0, 24, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        return clip.getType() == HitResult.Type.MISS ? pos.y - HOVER_HEIGHT : clip.getLocation().y;
    }

    private boolean isFoe(LivingEntity target, LivingEntity owner) {
        if (!canAffect(target)) {
            return false;
        }
        if (owner instanceof Mob mob) {
            return target == mob.getTarget();
        }
        return target instanceof Enemy || (target instanceof Mob mob && owner != null && mob.getTarget() == owner)
                || (owner != null && target == owner.getLastHurtMob());
    }

    private Optional<LivingEntity> nearestEnemy(double range) {
        LivingEntity owner = getOwner();
        return level().getEntitiesOfClass(LivingEntity.class, new AABB(position(), position()).inflate(range, 16, range), t -> isFoe(t, owner))
                .stream()
                .min(Comparator.comparingDouble(t -> t.position().multiply(1, 0, 1).distanceToSqr(position().multiply(1, 0, 1))));
    }

    // ------------------------------------------------------------------ lightning
    private Optional<LivingEntity> pickStrikeTarget() {
        LivingEntity owner = getOwner();
        float radius = getRadius();
        Vec3 pos = position();
        List<LivingEntity> below = level().getEntitiesOfClass(LivingEntity.class, new AABB(pos.x - radius, pos.y - 16, pos.z - radius, pos.x + radius, pos.y + 1, pos.z + radius),
                t -> isFoe(t, owner) && t.position().multiply(1, 0, 1).distanceToSqr(pos.multiply(1, 0, 1)) <= radius * radius && openSky(t));
        return below.isEmpty() ? Optional.empty() : Optional.of(below.get(random.nextInt(below.size())));
    }

    /** Lightning cannot pass through a roof between the cloud and the target. */
    private boolean openSky(LivingEntity target) {
        Vec3 top = new Vec3(target.getX(), getY(), target.getZ());
        return level().clip(new ClipContext(top, target.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    public void strike(LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        LivingEntity owner = getOwner();
        hit(server, target, damage);
        StormBolt.spawn(server, target.position(), (float) (getY() - target.getY()));
        // the bolt forks to the closest other foe
        level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(CHAIN_RANGE), t -> t != target && isFoe(t, owner))
                .stream()
                .min(Comparator.comparingDouble(t -> t.distanceToSqr(target)))
                .ifPresent(next -> {
                    hit(server, next, damage * CHAIN_RATIO);
                    ASFx.line(server, ParticleHelper.ELECTRICITY, target.getBoundingBox().getCenter(), next.getBoundingBox().getCenter(), 10, 0.08);
                });
        server.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.4f, 0.9f + random.nextFloat() * 0.2f);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.7f, 1.3f + random.nextFloat() * 0.2f);
    }

    private void hit(ServerLevel server, LivingEntity target, float amount) {
        if (target.isInWaterRainOrBubble()) {
            amount *= WET_MULTIPLIER;
        }
        DamageSources.applyDamage(target, amount, ASSpells.THUNDERHEAD.get().getDamageSource(this, getOwner()));
        Vec3 c = target.getBoundingBox().getCenter();
        server.sendParticles(ParticleHelper.ELECTRIC_SPARKS, c.x, c.y, c.z, 16, 0.3, 0.4, 0.3, 0.3);
        server.sendParticles(ParticleHelper.ELECTRICITY, c.x, c.y, c.z, 10, 0.3, 0.5, 0.3, 0.05);
        ASFx.blastwave(server, ASFx.THUNDER_YELLOW, target.position(), 1.4f);
    }

    // ------------------------------------------------------------------ looks
    private void cloudParticles() {
        float radius = getRadius() * 0.55f;
        for (int i = 0; i < 5; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(random.nextDouble()) * radius;
            level().addParticle(ASParticles.STORM_PUFF.get(), getX() + Math.cos(a) * r, getY() + (random.nextDouble() - 0.3) * 0.9, getZ() + Math.sin(a) * r,
                    (random.nextDouble() - 0.5) * 0.02, 0.003, (random.nextDouble() - 0.5) * 0.02);
        }
        // pale rim so the cloud reads against a dark sky too
        if (random.nextInt(2) == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            level().addParticle(ASParticles.CLOUD_PUFF.get(), getX() + Math.cos(a) * radius, getY() + 0.5, getZ() + Math.sin(a) * radius, 0, 0.005, 0);
        }
        // flickers of lightning inside and rain underneath
        if (random.nextInt(4) == 0) {
            level().addParticle(ParticleHelper.ELECTRICITY, getX() + (random.nextDouble() - 0.5) * radius, getY(), getZ() + (random.nextDouble() - 0.5) * radius, 0, 0, 0);
        }
        for (int i = 0; i < 2; i++) {
            level().addParticle(ParticleTypes.FALLING_WATER, getX() + (random.nextDouble() - 0.5) * radius * 1.6, getY() - 0.4, getZ() + (random.nextDouble() - 0.5) * radius * 1.6, 0, 0, 0);
        }
    }

    @Override
    protected void onExpire() {
        ASFx.burst(level(), ASParticles.STORM_PUFF.get(), position(), 20, 1.2, 0.02);
    }
}
