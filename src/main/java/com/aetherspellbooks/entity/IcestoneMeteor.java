package com.aetherspellbooks.entity;

import com.aetherspellbooks.registry.ASEntities;
import com.aetherspellbooks.registry.ASParticles;
import com.aetherspellbooks.registry.ASSpells;
import com.aetherspellbooks.util.ASFx;
import com.aetherteam.aether.block.AetherBlocks;
import com.aetherteam.aether.client.AetherSoundEvents;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FrostedIceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * A tumbling boulder of the Aether's icestone called down from the sky by Icestone Meteor. It shatters on impact:
 * foes in the blast take damage (less toward the edge), are frozen and chilled and thrown back; like icestone itself,
 * it freezes the water around the crater (into frosted ice that melts again).
 */
public class IcestoneMeteor extends AbstractMagicProjectile {
    public static final float EDGE_DAMAGE = 0.6f;
    private float radius = 3.5f;
    private int chillTicks = 100;
    private boolean shattered;

    public IcestoneMeteor(EntityType<? extends IcestoneMeteor> type, Level level) {
        super(type, level);
        setNoGravity(false);
    }

    public IcestoneMeteor(Level level, LivingEntity shooter) {
        this(ASEntities.ICESTONE_METEOR.get(), level);
        setOwner(shooter);
    }

    public void configure(float radius, int chillTicks) {
        this.radius = radius;
        this.chillTicks = chillTicks;
    }

    public static BlockState icestone() {
        return AetherBlocks.ICESTONE.get().defaultBlockState();
    }

    @Override
    public void trailParticles() {
        Vec3 jitter = Utils.getRandomVec3(0.4);
        level().addParticle(ParticleHelper.SNOWFLAKE, getX() + jitter.x, getY() + 0.5 + jitter.y, getZ() + jitter.z, 0, 0, 0);
        level().addParticle(ParticleHelper.SNOW_DUST, getX() + jitter.z, getY() + 0.5 + jitter.x, getZ() + jitter.y, 0, 0.02, 0);
        if (tickCount % 2 == 0) {
            level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, icestone()), getX(), getY() + 0.5, getZ(), 0, 0, 0);
            level().addParticle(ASParticles.CLOUD_PUFF.get(), getX(), getY() + 0.5, getZ(), 0, 0.02, 0);
        }
    }

    @Override
    public void impactParticles(double x, double y, double z) {
    }

    @Override
    public float getSpeed() {
        return 1.1f;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05;
    }

    @Override
    public Optional<net.minecraft.core.Holder<SoundEvent>> getImpactSound() {
        return Optional.of(SoundRegistry.ICE_BLOCK_IMPACT);
    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult result) {
        super.onHitBlock(result);
        shatter(result.getLocation());
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult result) {
        super.onHitEntity(result);
        shatter(result.getLocation());
    }

    public void shatter(Vec3 at) {
        if (shattered || !(level() instanceof ServerLevel server)) {
            return;
        }
        shattered = true;
        LivingEntity owner = getOwner() instanceof LivingEntity living ? living : null;
        AABB area = new AABB(at, at).inflate(radius, radius * 0.8, radius);
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, area, t -> t.isAlive() && !t.isSpectator() && t != owner
                && (owner == null || !(t.isAlliedTo(owner) || owner.isAlliedTo(t))))) {
            double distance = target.getBoundingBox().getCenter().distanceTo(at);
            if (distance > radius + 0.5) {
                continue;
            }
            float falloff = 1 - (1 - EDGE_DAMAGE) * (float) Mth.clamp(distance / radius, 0, 1);
            if (DamageSources.applyDamage(target, damage * falloff, ASSpells.ICESTONE_METEOR.get().getDamageSource(this, owner))) {
                target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + chillTicks / 2));
                target.addEffect(new MobEffectInstance(MobEffectRegistry.CHILLED, chillTicks, 0));
                Vec3 away = target.position().subtract(at).multiply(1, 0, 1);
                away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize();
                target.setDeltaMovement(target.getDeltaMovement().add(away.scale(0.55 * falloff)).add(0, 0.35 * falloff, 0));
                target.hurtMarked = true;
            }
        }
        freezeWater(server, BlockPos.containing(at));

        // shards of icestone, frost and snow thrown out of the crater
        BlockParticleOption shard = new BlockParticleOption(ParticleTypes.BLOCK, icestone());
        server.sendParticles(shard, at.x, at.y + 0.5, at.z, 90, radius * 0.35, 0.4, radius * 0.35, 0.4);
        server.sendParticles(ParticleHelper.SNOWFLAKE, at.x, at.y + 0.6, at.z, 50, radius * 0.4, 0.6, radius * 0.4, 0.12);
        server.sendParticles(ParticleHelper.ICY_FOG, at.x, at.y + 0.3, at.z, 20, radius * 0.4, 0.2, radius * 0.4, 0.02);
        server.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.4, at.z, 16, radius * 0.3, 0.2, radius * 0.3, 0.06);
        ASFx.ring(server, ParticleHelper.SNOW_DUST, at.add(0, 0.2, 0), 0.8, 36, 0.5);
        ASFx.blastwave(server, ASFx.FROST_CYAN, at, radius);
        ASFx.blastwave(server, ASFx.CLOUD_WHITE, at, radius * 0.6f);
        ASFx.shake(server, at, 16, 12);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2f, 1.35f);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.5f, 0.7f);
        server.playSound(null, at.x, at.y, at.z, AetherSoundEvents.ENTITY_ICE_CRYSTAL_EXPLODE.get(), SoundSource.PLAYERS, 1.2f, 0.8f);
        discard();
    }

    /** Like icestone, freeze still water at the surface around the crater (frosted ice melts back over time). */
    private void freezeWater(ServerLevel server, BlockPos center) {
        int r = Mth.ceil(radius + 1);
        BlockState frosted = Blocks.FROSTED_ICE.defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -2, -r), center.offset(r, 1, r))) {
            if (pos.distSqr(center) > r * r) {
                continue;
            }
            BlockState state = server.getBlockState(pos);
            if (state.is(Blocks.WATER) && state.getFluidState().isSource() && server.getBlockState(pos.above()).isAir()) {
                server.setBlockAndUpdate(pos, frosted.setValue(FrostedIceBlock.AGE, 0));
                server.scheduleTick(pos.immutable(), Blocks.FROSTED_ICE, Mth.nextInt(random, 60, 120));
            }
        }
    }
}
