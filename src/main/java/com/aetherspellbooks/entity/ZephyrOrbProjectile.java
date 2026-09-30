package com.aetherspellbooks.entity;

import com.aetherspellbooks.registry.ASEntities;
import com.aetherspellbooks.registry.ASParticles;
import com.aetherspellbooks.registry.ASSpells;
import com.aetherspellbooks.util.ASFx;
import com.aetherteam.aether.client.AetherSoundEvents;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
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

import java.util.Optional;

/**
 * A compressed ball of Zephyr wind. Bursts on impact, blasting everything nearby away,
 * which is especially dangerous next to the edge of a floating island.
 */
public class ZephyrOrbProjectile extends AbstractMagicProjectile {
    private float radius = 2.5f;
    private float knockback = 1.2f;

    public ZephyrOrbProjectile(EntityType<? extends ZephyrOrbProjectile> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public ZephyrOrbProjectile(Level level, LivingEntity shooter) {
        this(ASEntities.ZEPHYR_ORB.get(), level);
        setOwner(shooter);
    }

    public void configure(float radius, float knockback) {
        this.radius = radius;
        this.knockback = knockback;
    }

    @Override
    public void trailParticles() {
        Vec3 random = Utils.getRandomVec3(0.15);
        Vec3 back = getDeltaMovement().scale(-0.15);
        level().addParticle(ASParticles.CLOUD_PUFF.get(), getX() + random.x, getY() + 0.3 + random.y, getZ() + random.z, back.x, back.y, back.z);
    }

    @Override
    public void impactParticles(double x, double y, double z) {
        Vec3 pos = new Vec3(x, y, z);
        ASFx.ring(level(), ASParticles.CLOUD_PUFF.get(), pos, 0.4, 24, 0.35);
        ASFx.burst(level(), ASParticles.CLOUD_PUFF.get(), pos, 12, 0.4, 0.05);
        ASFx.blastwave(level(), ASFx.CLOUD_WHITE, pos.subtract(0, 0.3, 0), radius);
    }

    @Override
    public float getSpeed() {
        return 1.25f;
    }

    @Override
    public Optional<net.minecraft.core.Holder<SoundEvent>> getImpactSound() {
        return Optional.of(AetherSoundEvents.ENTITY_CLOUD_CRYSTAL_EXPLODE);
    }

    @Override
    protected void onHit(@NotNull HitResult hitResult) {
        if (isRemoved()) {
            return;
        }
        super.onHit(hitResult);
        if (!level().isClientSide) {
            detonate(hitResult.getLocation());
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

    /** Bursts at {@code center}, damaging and blasting away everything nearby. */
    public void detonate(Vec3 center) {
        Entity owner = getOwner();
        var source = ASSpells.ZEPHYR_BLAST.get().getDamageSource(this, owner);
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius))) {
            if (target == owner || target.isSpectator() || (owner != null && (target.isAlliedTo(owner) || owner.isAlliedTo(target)))) {
                continue;
            }
            double distance = target.position().distanceTo(center);
            if (distance > radius) {
                continue;
            }
            DamageSources.applyDamage(target, damage, source);
            Vec3 away = target.position().subtract(center).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? getDeltaMovement().multiply(1, 0, 1).normalize() : away.normalize();
            double strength = knockback * (1 - 0.5 * distance / radius) * (1 - target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
            target.setDeltaMovement(target.getDeltaMovement().add(away.scale(strength)).add(0, 0.35 + strength * 0.25, 0));
            target.hurtMarked = true;
        }
    }
}
