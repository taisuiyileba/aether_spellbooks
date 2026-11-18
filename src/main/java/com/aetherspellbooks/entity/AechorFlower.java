package com.aetherspellbooks.entity;

import com.aetherspellbooks.registry.ASEntities;
import com.aetherteam.aether.client.AetherSoundEvents;
import com.aetherteam.aether.entity.AetherEntityTypes;
import com.aetherteam.aether.entity.monster.AechorPlant;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import org.joml.Vector3f;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

public class AechorFlower extends OverheadSpellEntity {
    public static final int RANGE = 7;
    public static final int FIRST_SHOT = 9;
    public static final int SECOND_SHOT = 19;
    public static final int RECOVER = 26;
    private static final int VOLLEY_INTERVAL = 42;
    private static final EntityDataAccessor<Long> VOLLEY_START = SynchedEntityData.defineId(AechorFlower.class, EntityDataSerializers.LONG);
    private static final DustParticleOptions POLLEN = new DustParticleOptions(new Vector3f(0.64f, 0.95f, 0.54f), 0.62f);
    private static final EntityDataAccessor<Long> EXPIRES = SynchedEntityData.defineId(AechorFlower.class, EntityDataSerializers.LONG);
    private int nextSearch = 7;
    private LivingEntity target;
    private AechorPlant animationSource;

    public AechorFlower(EntityType<? extends AechorFlower> type, Level level) {
        super(type, level);
    }

    public AechorFlower(Level level, LivingEntity owner) {
        this(ASEntities.AECHOR_FLOWER.get(), level);
        setOwner(owner);
        setYRot(owner.getYRot());
        setXRot(18);
    }

    // The Aether's petal animation reads a plant's state. This client-only stand-in is never added to the level.
    public AechorPlant animationSource() {
        if (animationSource == null) {
            animationSource = new AechorPlant(AetherEntityTypes.AECHOR_PLANT.get(), level());
        }
        return animationSource;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(VOLLEY_START, -1L);
        entityData.define(EXPIRES, 0L);
    }

    @Override
    public void setLifetime(int lifetime) {
        super.setLifetime(lifetime);
        entityData.set(EXPIRES, level().getGameTime() + lifetime);
    }

    public float fade(float partial) {
        return Mth.clamp((entityData.get(EXPIRES) - level().getGameTime() - partial) / 7f, 0, 1);
    }

    public float volleyAge(float partial) {
        long start = entityData.get(VOLLEY_START);
        return start < 0 ? -1 : level().getGameTime() - start + partial;
    }

    @Override
    protected Vec3 anchor(LivingEntity owner, Vec3 feet, float partialTick) {
        Vec3 eye = feet.add(0, owner.getEyeHeight(), 0);
        double time = level().getGameTime() - 1 + (double) partialTick;
        Vec3 overhead = feet.add(0, owner.getBbHeight() + 0.83 + 0.07 * Math.sin(time * 0.15), 0);
        var roof = level().clip(new ClipContext(eye, overhead.add(0, 0.25, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        return roof.getType() == HitResult.Type.MISS ? overhead : roof.getLocation().subtract(0, 0.25, 0);
    }

    static boolean isEnemy(LivingEntity owner, LivingEntity target) {
        if (target == owner || !target.isAlive() || target.isSpectator() || owner.isAlliedTo(target)
                || target.isAlliedTo(owner) || DamageSources.isFriendlyFireBetween(owner, target)) {
            return false;
        }
        if (owner instanceof Mob mob) {
            return target == mob.getTarget();
        }
        return target instanceof Enemy || (target instanceof Mob mob && mob.getTarget() == owner)
                || target == owner.getLastHurtMob() || target == owner.getLastHurtByMob();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || isRemoved()) {
            return;
        }
        LivingEntity owner = getOwner();
        if (owner == null || !owner.isAlive() || owner.level() != server) {
            discard();
            return;
        }
        if (target == null) {
            setXRot(Mth.approach(getXRot(), 18, 6));
            if (tickCount < nextSearch) {
                return;
            }
            nextSearch = tickCount + 4;
            target = findTarget(server, owner);
            if (target == null) {
                return;
            }
            entityData.set(VOLLEY_START, server.getGameTime());
            playSound(SoundEvents.SPORE_BLOSSOM_PLACE, 0.55f, 1.45f);
        }

        int age = (int) volleyAge(0);
        if (!canAimAt(server, owner, target)) {
            target = null;
            entityData.set(VOLLEY_START, -1L);
            return;
        }
        // Track during anticipation and recovery. Hold the heading for the final two ticks of each release.
        if (age < FIRST_SHOT - 2 || (age > FIRST_SHOT && age < SECOND_SHOT - 2)) {
            Vec3 to = target.getBoundingBox().getCenter().subtract(position());
            float yaw = (float) (-Mth.atan2(to.x, to.z) * Mth.RAD_TO_DEG);
            float tilt = (float) (Mth.atan2(to.horizontalDistance(), to.y) * Mth.RAD_TO_DEG);
            setYRot(Mth.approachDegrees(getYRot(), yaw, 33));
            setXRot(Mth.approach(getXRot(), tilt, 24));
        }
        if (age == FIRST_SHOT || age == SECOND_SHOT) {
            fireNeedle(server, owner, age == SECOND_SHOT);
        }
        if (age >= SECOND_SHOT) {
            target = null;
            nextSearch = tickCount + VOLLEY_INTERVAL - SECOND_SHOT;
        }
    }

    private LivingEntity findTarget(ServerLevel server, LivingEntity owner) {
        LivingEntity focus = owner.getLastHurtMob();
        if (focus != null && owner.tickCount - owner.getLastHurtMobTimestamp() < 60 && canAimAt(server, owner, focus)) {
            return focus;
        }
        return server.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(RANGE), t -> canAimAt(server, owner, t))
                .stream().min(Comparator.comparingDouble(owner::distanceToSqr)).orElse(null);
    }

    private boolean canAimAt(ServerLevel server, LivingEntity owner, LivingEntity enemy) {
        return enemy.level() == server && owner.distanceToSqr(enemy) <= RANGE * RANGE && isEnemy(owner, enemy)
                && server.clip(new ClipContext(position(), enemy.getBoundingBox().getCenter(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    private void fireNeedle(ServerLevel server, LivingEntity owner, boolean second) {
        Vec3 aim = Vec3.directionFromRotation(getXRot() - 90, getYRot());
        Vec3 muzzle = position().add(aim.scale(0.32));
        if (server.clip(new ClipContext(position(), muzzle, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
                .getType() != HitResult.Type.MISS) {
            return;
        }
        AechorNeedle needle = new AechorNeedle(ASEntities.AECHOR_NEEDLE.get(), server);
        needle.setOwner(owner);
        needle.setBaseDamage(damage);
        needle.setPos(muzzle);
        needle.shoot(aim.x, aim.y, aim.z, 1.35f, 0);
        server.addFreshEntity(needle);
        server.sendParticles(POLLEN, muzzle.x, muzzle.y, muzzle.z, 6, 0.08, 0.08, 0.08, 0.04);
        playSound(AetherSoundEvents.ENTITY_AECHOR_PLANT_SHOOT.get(), 0.7f, second ? 1.34f : 1.05f);
    }

    @Override
    protected void onExpire() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, getX(), getY(), getZ(), 8, 0.25, 0.15, 0.25, 0.04);
        }
    }
}
