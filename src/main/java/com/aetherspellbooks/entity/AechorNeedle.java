package com.aetherspellbooks.entity;

import com.aetherspellbooks.registry.ASSpells;
import com.aetherteam.aether.effect.AetherEffects;
import com.aetherteam.aether.entity.projectile.PoisonNeedle;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public class AechorNeedle extends PoisonNeedle {
    private static final DustColorTransitionOptions SAP = new DustColorTransitionOptions(
            new Vector3f(0.78f, 1f, 0.70f), new Vector3f(0.25f, 0.53f, 0.54f), 0.59f);

    public AechorNeedle(EntityType<? extends AechorNeedle> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && entity instanceof LivingEntity living && getOwner() instanceof LivingEntity owner
                && AechorFlower.isEnemy(owner, living);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (!level().isClientSide && hit.getEntity() instanceof LivingEntity target && getOwner() instanceof LivingEntity owner
                && DamageSources.applyDamage(target, (float) getBaseDamage(), ASSpells.AECHOR_FLOWER.get().getDamageSource(this, owner))) {
            target.addEffect(new MobEffectInstance(AetherEffects.INEBRIATION, 40, 0), owner);
            Vec3 impact = hit.getLocation();
            ((ServerLevel) level()).sendParticles(SAP, impact.x, impact.y, impact.z, 9, 0.13, 0.12, 0.13, 0.08);
            ((ServerLevel) level()).sendParticles(ParticleTypes.CRIT, impact.x, impact.y, impact.z, 4, 0.1, 0.1, 0.1, 0.14);
        }
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        discard();
    }

    @Override
    public void tick() {
        Vec3 before = position();
        super.tick();
        if (level().isClientSide && !isRemoved()) {
            for (int i = 0; i < 3; i++) {
                Vec3 point = before.lerp(position(), i / 3.0);
                level().addParticle(SAP, point.x, point.y, point.z, 0, 0.004, 0);
            }
        }
        if (!level().isClientSide && tickCount >= 12) {
            discard();
        }
    }
}
