package com.aetherspellbooks.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A short-lived, non-persistent spell entity with an owner (whirlwinds, the solar orb...).
 */
public abstract class OwnedSpellEntity extends Entity {
    @Nullable
    private UUID ownerId;
    @Nullable
    private LivingEntity cachedOwner;
    protected float damage;
    protected int lifetime = 200;

    protected OwnedSpellEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void setOwner(@Nullable LivingEntity owner) {
        this.cachedOwner = owner;
        this.ownerId = owner == null ? null : owner.getUUID();
    }

    @Nullable
    public LivingEntity getOwner() {
        if (cachedOwner != null && !cachedOwner.isRemoved()) {
            return cachedOwner;
        }
        if (ownerId != null && level() instanceof ServerLevel server && server.getEntity(ownerId) instanceof LivingEntity living) {
            cachedOwner = living;
            return living;
        }
        return null;
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    public void setLifetime(int lifetime) {
        this.lifetime = lifetime;
    }

    /** Whether {@code target} may be affected: not the owner, not an ally, not a spectator. */
    protected boolean canAffect(Entity target) {
        LivingEntity owner = getOwner();
        if (target == this || target == owner || target.isSpectator() || !target.isAlive()) {
            return false;
        }
        return owner == null || !(target.isAlliedTo(owner) || owner.isAlliedTo(target));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && (tickCount > lifetime || (ownerId != null && getOwner() == null && tickCount > 20))) {
            onExpire();
            discard();
        }
    }

    protected void onExpire() {
    }

    @Override
    protected void defineSynchedData() {
    }

    // Spell effects are transient and are not saved with the chunk.
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public @NotNull Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
