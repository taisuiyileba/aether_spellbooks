package com.aetherspellbooks.entity;

import com.aetherspellbooks.registry.ASEntities;
import com.aetherspellbooks.util.ASFx;
import com.aetherteam.aether.client.AetherSoundEvents;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Optional;

/**
 * A miniature Sun Spirit that hovers above its caster and rains homing sunfire bolts on nearby foes.
 */
public class SolarOrb extends OwnedSpellEntity {
    public static final float RANGE = 16f;
    private int fireInterval = 16;

    public SolarOrb(EntityType<? extends SolarOrb> type, Level level) {
        super(type, level);
    }

    public SolarOrb(Level level, LivingEntity owner) {
        this(ASEntities.SOLAR_ORB.get(), level);
        setOwner(owner);
        setPos(anchor(owner));
    }

    public void setFireInterval(int ticks) {
        this.fireInterval = Math.max(4, ticks);
    }

    private static Vec3 anchor(LivingEntity owner) {
        Vec3 back = owner.getLookAngle().multiply(1, 0, 1).normalize().scale(-0.6);
        return owner.position().add(back.x, owner.getBbHeight() + 1.4, back.z);
    }

    @Override
    public void tick() {
        super.tick();
        LivingEntity owner = getOwner();
        if (owner != null) {
            Vec3 target = anchor(owner).add(0, Math.sin(tickCount * 0.12) * 0.15, 0);
            setPos(position().lerp(target, 0.35));
        }
        if (level().isClientSide) {
            if (random.nextInt(2) == 0) {
                level().addParticle(ParticleHelper.EMBERS, getX() + (random.nextDouble() - 0.5) * 0.8, getY() + 0.3 + (random.nextDouble() - 0.5) * 0.8,
                        getZ() + (random.nextDouble() - 0.5) * 0.8, 0, 0.02, 0);
            }
            return;
        }
        if (owner != null && tickCount > 10 && tickCount % fireInterval == 0) {
            findTarget(owner).ifPresent(this::shootAt);
        }
    }

    private Optional<LivingEntity> findTarget(LivingEntity owner) {
        if (owner instanceof Mob mob) {
            // a hostile caster's sun only fires at what its caster is fighting
            LivingEntity target = mob.getTarget();
            return target != null && canAffect(target) && distanceToSqr(target) < RANGE * RANGE && target.hasLineOfSight(this)
                    ? Optional.of(target) : Optional.empty();
        }
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(RANGE), t -> canAffect(t)
                        && (t instanceof Enemy || (t instanceof Mob mob && mob.getTarget() == owner) || t == owner.getLastHurtMob())
                        && t.hasLineOfSight(this))
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr));
    }

    private void shootAt(LivingEntity target) {
        LivingEntity owner = getOwner();
        SolarBoltProjectile bolt = new SolarBoltProjectile(level(), owner);
        Vec3 from = position().add(0, 0.3, 0);
        bolt.setPos(from);
        Vec3 direction = target.getBoundingBox().getCenter().subtract(from).normalize().add(0, 0.25, 0).normalize();
        bolt.shoot(direction);
        bolt.setDamage(damage);
        bolt.setHomingTarget(target);
        level().addFreshEntity(bolt);
        level().playSound(null, getX(), getY(), getZ(), AetherSoundEvents.ENTITY_SUN_SPIRIT_SHOOT_FIRE.get(), SoundSource.NEUTRAL, 0.6f, 1.3f + random.nextFloat() * 0.2f);
    }

    @Override
    protected void onExpire() {
        ASFx.burst(level(), ParticleHelper.FIRE, position().add(0, 0.3, 0), 30, 0.4, 0.08);
        ASFx.blastwave(level(), ASFx.SOLAR_ORANGE, position(), 2f);
    }
}
