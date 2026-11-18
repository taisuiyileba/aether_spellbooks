package com.aetherspellbooks.entity;

import com.aetherspellbooks.registry.ASEntities;
import com.aetherspellbooks.registry.ASSpells;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

/** One growing row per heading. The clients receive the actual terrain profile as the row advances. */
public class ThornTrail extends OwnedSpellEntity {
    public static final int STEP_TICKS = 2;
    public static final int WITHER_TICKS = 18;
    private static final EntityDataAccessor<CompoundTag> GROWTH = SynchedEntityData.defineId(ThornTrail.class, EntityDataSerializers.COMPOUND_TAG);
    private Set<UUID> hit;
    private int remaining;
    private Vec3 step;
    private Vec3 previous;

    public ThornTrail(EntityType<? extends ThornTrail> type, Level level) {
        super(type, level);
    }

    public static void grow(ServerLevel level, LivingEntity caster, float yaw, int length, float power, Set<UUID> sharedHits) {
        ThornTrail trail = new ThornTrail(ASEntities.THORN_TRAIL.get(), level);
        trail.setOwner(caster);
        trail.setPos(caster.position());
        trail.setYRot(yaw);
        trail.step = Vec3.directionFromRotation(0, yaw);
        trail.previous = caster.position();
        trail.remaining = length;
        trail.damage = power;
        trail.hit = sharedHits;
        trail.lifetime = length * STEP_TICKS + WITHER_TICKS;
        if (trail.advance(level, caster)) {
            level.addFreshEntity(trail);
        }
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(GROWTH, new CompoundTag());
    }

    public int[] profile() {
        return entityData.get(GROWTH).getIntArray("heights");
    }

    public float growthAge(float partialTick) {
        return level().getGameTime() - entityData.get(GROWTH).getLong("start") + partialTick;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        int reach = profile().length + 2;
        return getBoundingBox().inflate(reach, reach + 1, reach);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || isRemoved() || remaining <= 0 || tickCount % STEP_TICKS != 0) {
            return;
        }
        LivingEntity owner = getOwner();
        if (owner == null || owner.level() != server || !owner.isAlive() || !advance(server, owner)) {
            remaining = 0;
            lifetime = tickCount + WITHER_TICKS;
        }
    }

    private boolean advance(ServerLevel level, LivingEntity caster) {
        Vec3 next = previous.add(step);
        if (!level.hasChunkAt(BlockPos.containing(next))) {
            return false;
        }
        Vec3 probe = next.add(0, 1.01, 0);
        if (clip(level, previous.add(0, 1.01, 0), probe).getType() != HitResult.Type.MISS) {
            return false;
        }
        BlockHitResult floor = clip(level, probe, next.add(0, -1.05, 0));
        if (floor.getType() == HitResult.Type.MISS || floor.isInside() || floor.getDirection() != Direction.UP) {
            return false;
        }
        Vec3 base = floor.getLocation();
        CompoundTag state = entityData.get(GROWTH);
        int[] old = profile();
        int[] heights = Arrays.copyOf(old, old.length + 1);
        heights[old.length] = (int) Math.round((base.y - getY()) * 256);
        CompoundTag updated = new CompoundTag();
        updated.putLong("start", old.length == 0 ? level.getGameTime() : state.getLong("start"));
        updated.putIntArray("heights", heights);
        entityData.set(GROWTH, updated);
        previous = base;
        remaining--;

        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(floor.getBlockPos())),
                base.x, base.y + 0.12, base.z, 5, 0.19, 0.1, 0.19, 0.13);
        if (old.length % 4 == 1) {
            level.playSound(null, base.x, base.y, base.z, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 0.4f, 0.88f + old.length * 0.035f);
        }
        AABB area = new AABB(base.x - 0.55, base.y, base.z - 0.55, base.x + 0.55, base.y + 1.3, base.z + 0.55);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, this::canAffect)) {
            if (hit.contains(target.getUUID()) || DamageSources.isFriendlyFireBetween(caster, target)
                    || clip(level, base.add(0, 0.65, 0), target.getBoundingBox().getCenter()).getType() != HitResult.Type.MISS) {
                continue;
            }
            hit.add(target.getUUID());
            if (DamageSources.applyDamage(target, damage, ASSpells.THORN_SPIKES.get().getDamageSource(this, caster))) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1), caster);
                level.playSound(null, base.x, base.y, base.z, SoundEvents.SWEET_BERRY_BUSH_BREAK, SoundSource.PLAYERS, 0.7f, 0.85f);
            }
        }
        return true;
    }

    private BlockHitResult clip(Level level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
    }
}
