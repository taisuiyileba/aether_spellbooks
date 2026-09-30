package com.aetherspellbooks.entity;

import com.aetherspellbooks.config.ASConfig;
import com.aetherspellbooks.registry.ASEntities;
import com.aetherspellbooks.registry.ASParticles;
import com.aetherteam.aether.entity.passive.Moa;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A spectral, pre-saddled Moa bound to its summoner. Cannot breed, lay eggs, be fed or drop loot.
 * Vanishes a few seconds after its rider dismounts. Reuses the Aether's Moa model and renderer.
 */
public class SummonedMoa extends Moa implements IMagicSummon {
    private int riderlessTicks;

    public SummonedMoa(EntityType<? extends Moa> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public SummonedMoa(Level level) {
        this(ASEntities.SUMMONED_MOA.get(), level);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // spectral shimmer, and feathers shed while flapping in the air
            if (random.nextFloat() < 0.25f) {
                Vec3 offset = new Vec3(random.nextGaussian() * 0.4, random.nextFloat() * 1.8, random.nextGaussian() * 0.4);
                level().addParticle(ASParticles.SKY_SPARKLE.get(), getX() + offset.x, getY() + offset.y, getZ() + offset.z, 0, 0.01, 0);
            }
            if (!onGround() && random.nextFloat() < 0.35f) {
                level().addParticle(ASParticles.FEATHER.get(), getRandomX(0.8), getY() + 1.0 + random.nextFloat() * 0.6, getRandomZ(0.8),
                        (random.nextDouble() - 0.5) * 0.05, -0.02, (random.nextDouble() - 0.5) * 0.05);
            }
            return;
        }
        if (getPassengers().isEmpty()) {
            if (++riderlessTicks > ASConfig.MOA_DISMOUNT_UNSUMMON_SECONDS.get() * 20) {
                onUnSummon();
            }
        } else {
            riderlessTicks = 0;
        }
    }

    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        if (player == getSummoner() && !isVehicle() && !player.isShiftKeyDown()) {
            if (!level().isClientSide) {
                player.startRiding(this);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (shouldIgnoreDamage(source)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean isAlliedTo(@NotNull Entity entity) {
        return entity == getSummoner() || super.isAlliedTo(entity) || this.isAlliedHelper(entity);
    }

    @Override
    public void onUnSummon() {
        if (!level().isClientSide) {
            MagicManager.spawnParticles(level(), ParticleTypes.POOF, getX(), getY() + 0.8, getZ(), 15, 0.4, 0.8, 0.4, 0.03, false);
            MagicManager.spawnParticles(level(), ASParticles.FEATHER.get(), getX(), getY() + 1.2, getZ(), 20, 0.5, 0.5, 0.5, 0.05, false);
            ejectPassengers();
            discard();
        }
    }

    @Override
    public void die(@NotNull DamageSource source) {
        this.onDeathHelper();
        super.die(source);
    }

    @Override
    public void onRemovedFromLevel() {
        this.onRemovedHelper(this);
        super.onRemovedFromLevel();
    }

    // --- no eggs, breeding, feeding or drops ---

    @Override
    public int getEggTime() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean canBreed() {
        return false;
    }

    @Override
    public boolean isFood(@NotNull ItemStack stack) {
        return false;
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob entity) {
        return null;
    }

    @Override
    protected void dropEquipment() {
        // the saddle is conjured, not dropped
    }

    @Override
    protected @NotNull net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> getDefaultLootTable() {
        return BuiltInLootTables.EMPTY;
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }
}
