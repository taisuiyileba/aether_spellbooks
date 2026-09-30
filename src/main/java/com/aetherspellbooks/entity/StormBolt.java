package com.aetherspellbooks.entity;

import com.aetherspellbooks.registry.ASEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

/**
 * Visual only: a jagged bolt of lightning from the Thunderhead down to where it struck. Lasts a few ticks.
 */
public class StormBolt extends Entity {
    private static final EntityDataAccessor<Float> DATA_HEIGHT = SynchedEntityData.defineId(StormBolt.class, EntityDataSerializers.FLOAT);
    public static final int LIFETIME = 8;

    public StormBolt(EntityType<? extends StormBolt> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static void spawn(ServerLevel level, Vec3 ground, float height) {
        StormBolt bolt = new StormBolt(ASEntities.STORM_BOLT.get(), level);
        bolt.setPos(ground);
        bolt.entityData.set(DATA_HEIGHT, Math.max(1f, height));
        level.addFreshEntity(bolt);
    }

    public float getHeight() {
        return entityData.get(DATA_HEIGHT);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > LIFETIME) {
            discard();
        }
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_HEIGHT, 6f);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /** The bolt reaches up to the cloud, so it must not be culled as a small box on the ground. */
    @Override
    public @NotNull AABB getBoundingBoxForCulling() {
        return getBoundingBox().expandTowards(0, getHeight(), 0).inflate(1.5, 0, 1.5);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
    }

    @Override
    public @NotNull Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
