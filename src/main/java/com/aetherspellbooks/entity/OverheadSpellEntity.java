package com.aetherspellbooks.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** A spell attached to its caster. Combat and rendering use the same attachment at their respective times. */
public abstract class OverheadSpellEntity extends OwnedSpellEntity {
    private static final EntityDataAccessor<Integer> CASTER = SynchedEntityData.defineId(OverheadSpellEntity.class, EntityDataSerializers.INT);

    protected OverheadSpellEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CASTER, -1);
    }

    @Override
    public void setOwner(@Nullable LivingEntity owner) {
        super.setOwner(owner);
        entityData.set(CASTER, owner == null ? -1 : owner.getId());
        if (owner != null) setPos(anchor(owner, owner.position(), 1));
    }

    @Override
    @Nullable
    public LivingEntity getOwner() {
        if (!level().isClientSide) return super.getOwner();
        return level().getEntity(entityData.get(CASTER)) instanceof LivingEntity living ? living : null;
    }

    protected abstract Vec3 anchor(LivingEntity owner, Vec3 feet, float partialTick);

    /** Match LevelRenderer.renderEntity, including its history fields after teleports. */
    private static Vec3 renderPosition(Entity entity, float partialTick) {
        if (entity.tickCount == 0) return entity.position();
        return new Vec3(Mth.lerp(partialTick, entity.xOld, entity.getX()),
                Mth.lerp(partialTick, entity.yOld, entity.getY()), Mth.lerp(partialTick, entity.zOld, entity.getZ()));
    }

    public Vec3 renderOffset(float partialTick) {
        LivingEntity owner = getOwner();
        if (owner == null) return Vec3.ZERO;
        return anchor(owner, renderPosition(owner, partialTick), partialTick).subtract(renderPosition(this, partialTick));
    }

    @Override
    public Vec3 getLightProbePosition(float partialTick) {
        LivingEntity owner = getOwner();
        return owner == null ? super.getLightProbePosition(partialTick)
                : anchor(owner, renderPosition(owner, partialTick), partialTick).add(0, getEyeHeight(), 0);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        LivingEntity owner = getOwner();
        // Include the whole billboard, the head offset and both endpoints of this frame's movement.
        if (owner == null) return super.getBoundingBoxForCulling().inflate(2.5);
        return owner.getBoundingBox().expandTowards(owner.xOld - owner.getX(), owner.yOld - owner.getY(), owner.zOld - owner.getZ())
                .expandTowards(0, 1.8, 0).inflate(3.5);
    }

    @Override
    public void tick() {
        super.tick();
        LivingEntity owner = getOwner();
        if (!isRemoved() && owner != null && owner.level() == level()) {
            setPos(anchor(owner, owner.position(), 1));
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        // A delayed move packet may still carry new aim angles. Once attached, only its rotation is needed.
        LivingEntity owner = getOwner();
        if (level().isClientSide && owner != null) {
            Vec3 point = anchor(owner, owner.position(), 1);
            super.lerpTo(point.x, point.y, point.z, yaw, pitch, steps);
        } else {
            super.lerpTo(x, y, z, yaw, pitch, steps);
        }
    }
}
