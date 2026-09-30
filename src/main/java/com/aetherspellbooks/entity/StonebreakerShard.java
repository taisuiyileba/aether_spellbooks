package com.aetherspellbooks.entity;

import com.aetherspellbooks.registry.ASEntities;
import com.aetherspellbooks.registry.ASSpells;
import com.aetherspellbooks.util.ASFx;
import com.aetherteam.aether.AetherTags;
import com.aetherteam.aether.block.AetherBlocks;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Tumbling holystone shard fired by Stonebreaker Shard. Its entity type is listed in
 * {@code aether:slider_damaging_projectiles}, so it can hurt the Slider.
 * Staggers what it hits and can pierce at high levels.
 */
public class StonebreakerShard extends AbstractMagicProjectile {
    public static final float DUNGEON_MOB_MULTIPLIER = 1.5f;
    public static final int STAGGER_TICKS = 30;

    private final Set<UUID> alreadyHit = new HashSet<>();

    public StonebreakerShard(EntityType<? extends StonebreakerShard> type, Level level) {
        super(type, level);
    }

    public StonebreakerShard(Level level, LivingEntity shooter) {
        this(ASEntities.STONEBREAKER_SHARD.get(), level);
        setOwner(shooter);
    }

    public static BlockState holystone() {
        return AetherBlocks.HOLYSTONE.get().defaultBlockState();
    }

    @Override
    public void trailParticles() {
        level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, holystone()), getX(), getY() + 0.2, getZ(), 0, 0, 0);
        if (tickCount % 2 == 0) {
            Vec3 back = getDeltaMovement().scale(-0.1);
            level().addParticle(ParticleTypes.WHITE_ASH, getX(), getY() + 0.2, getZ(), back.x, back.y, back.z);
        }
    }

    @Override
    public void impactParticles(double x, double y, double z) {
        MagicManager.spawnParticles(level(), new BlockParticleOption(ParticleTypes.BLOCK, holystone()), x, y, z, 28, 0.2, 0.2, 0.2, 0.25, false);
        MagicManager.spawnParticles(level(), ParticleTypes.POOF, x, y, z, 4, 0.1, 0.1, 0.1, 0.03, false);
        ASFx.blastwave(level(), ASFx.HOLYSTONE_GREY, new Vec3(x, y - 0.2, z), 1.2f);
    }

    @Override
    public float getSpeed() {
        return 1.6f;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.02;
    }

    @Override
    public Optional<net.minecraft.core.Holder<SoundEvent>> getImpactSound() {
        return Optional.of(net.minecraft.core.Holder.direct(SoundEvents.DEEPSLATE_BREAK));
    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult result) {
        super.onHitBlock(result);
        discard();
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult result) {
        Entity target = result.getEntity();
        if (!alreadyHit.add(target.getUUID())) {
            return;
        }
        super.onHitEntity(result);
        float amount = damage;
        if (target.getType().is(AetherTags.Entities.DUNGEON_ENTITIES)) {
            amount *= DUNGEON_MOB_MULTIPLIER;
        }
        if (DamageSources.applyDamage(target, amount, ASSpells.STONEBREAKER_SHARD.get().getDamageSource(this, getOwner()))
                && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffectRegistry.SLOWED, STAGGER_TICKS, 0));
        }
        consumeEntityImpact(result, true);
    }
}
