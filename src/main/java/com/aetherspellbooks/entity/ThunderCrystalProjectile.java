package com.aetherspellbooks.entity;

import com.aetherspellbooks.registry.ASEntities;
import com.aetherspellbooks.registry.ASSpells;
import com.aetherspellbooks.util.ASFx;
import com.aetherteam.aether.client.AetherSoundEvents;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import io.redspace.ironsspellbooks.particle.ZapParticleOption;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Homing crystal inspired by the Valkyrie Queen's thunder crystals.
 * Bursts into a small electric shock on impact, then arcs to nearby enemies.
 * Never summons real lightning (no fire).
 */
public class ThunderCrystalProjectile extends AbstractMagicProjectile {
    public static final float SHOCK_RADIUS = 1.5f;
    public static final float CHAIN_RANGE = 5f;
    public static final float CHAIN_DAMAGE_FACTOR = 0.6f;
    private static final int HOMING_TICKS = 40;

    private int chains;

    public ThunderCrystalProjectile(EntityType<? extends ThunderCrystalProjectile> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public ThunderCrystalProjectile(Level level, LivingEntity shooter) {
        this(ASEntities.THUNDER_CRYSTAL.get(), level);
        setOwner(shooter);
    }

    public void setChains(int chains) {
        this.chains = chains;
    }

    @Override
    public void tick() {
        if (tickCount == HOMING_TICKS) {
            stopEntityHoming();
        }
        super.tick();
    }

    @Override
    public void trailParticles() {
        Vec3 random = Utils.getRandomVec3(0.08);
        level().addParticle(ParticleHelper.ELECTRICITY, getX() + random.x, getY() + 0.25 + random.y, getZ() + random.z, 0, 0, 0);
        if (tickCount % 3 == 0) {
            level().addParticle(ParticleHelper.ELECTRIC_SPARKS, getX(), getY() + 0.25, getZ(), random.x, random.y, random.z);
        }
    }

    @Override
    public void impactParticles(double x, double y, double z) {
        MagicManager.spawnParticles(level(), ParticleHelper.ELECTRICITY, x, y, z, 35, 0.45, 0.45, 0.45, 0.7, false);
        MagicManager.spawnParticles(level(), ParticleHelper.ELECTRIC_SPARKS, x, y, z, 14, 0.2, 0.2, 0.2, 0.35, false);
        MagicManager.spawnParticles(level(), ParticleHelper.FOG_THUNDER_LIGHT, x, y, z, 3, 0.2, 0.2, 0.2, 0.02, false);
        ASFx.blastwave(level(), ASFx.THUNDER_YELLOW, new Vec3(x, y - 0.3, z), SHOCK_RADIUS * 1.5f);
    }

    @Override
    public float getSpeed() {
        return 0.9f;
    }

    @Override
    public Optional<net.minecraft.core.Holder<SoundEvent>> getImpactSound() {
        return Optional.of(AetherSoundEvents.ENTITY_THUNDER_CRYSTAL_EXPLODE);
    }

    @Override
    protected void onHit(@NotNull HitResult hitResult) {
        if (isRemoved()) {
            // an entity hit and a block hit can both be reported in the same tick
            return;
        }
        super.onHit(hitResult);
        if (!level().isClientSide) {
            detonate(hitResult.getLocation(), hitResult instanceof EntityHitResult entityHit ? entityHit.getEntity() : null);
            discard();
        }
    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult result) {
        // handled by onHit
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult result) {
        // handled by onHit
    }

    private boolean isValidTarget(LivingEntity target, Entity owner) {
        if (target == owner || !target.isAlive() || !target.isPickable() || target.isSpectator()) {
            return false;
        }
        return owner == null || !(target.isAlliedTo(owner) || owner.isAlliedTo(target));
    }

    /** Shocks around {@code center} (hitting {@code directHit} for full damage) and arcs onward. */
    public void detonate(Vec3 center, Entity directHit) {
        Entity owner = getOwner();
        var source = ASSpells.THUNDER_CRYSTAL.get().getDamageSource(this, owner);
        List<LivingEntity> struck = new ArrayList<>();
        if (directHit != null) {
            DamageSources.applyDamage(directHit, damage, source);
            if (directHit instanceof LivingEntity living) {
                struck.add(living);
            }
        }
        AABB area = new AABB(center, center).inflate(SHOCK_RADIUS);
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, area, t -> isValidTarget(t, owner))) {
            if (target != directHit) {
                DamageSources.applyDamage(target, damage * 0.5f, source);
                struck.add(target);
            }
        }
        // Arc to further enemies, each jump starting from the last one struck
        Vec3 from = center;
        for (int i = 0; i < chains; i++) {
            Vec3 origin = from;
            LivingEntity next = level().getEntitiesOfClass(LivingEntity.class, new AABB(origin, origin).inflate(CHAIN_RANGE),
                            t -> isValidTarget(t, owner) && !struck.contains(t))
                    .stream()
                    .min(Comparator.comparingDouble(t -> t.distanceToSqr(origin)))
                    .orElse(null);
            if (next == null) {
                break;
            }
            Vec3 to = next.getBoundingBox().getCenter();
            if (level() instanceof ServerLevel server) {
                server.sendParticles(new ZapParticleOption(to), origin.x, origin.y, origin.z, 1, 0, 0, 0, 0);
                server.sendParticles(ParticleHelper.ELECTRIC_SPARKS, to.x, to.y, to.z, 8, 0.2, 0.2, 0.2, 0.3);
            }
            DamageSources.applyDamage(next, damage * CHAIN_DAMAGE_FACTOR, source);
            struck.add(next);
            from = to;
        }
    }
}
