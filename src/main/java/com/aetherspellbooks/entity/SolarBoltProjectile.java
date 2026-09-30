package com.aetherspellbooks.entity;

import com.aetherspellbooks.registry.ASEntities;
import com.aetherspellbooks.registry.ASSpells;
import com.aetherspellbooks.util.ASFx;
import com.aetherteam.aether.client.AetherSoundEvents;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Homing sunfire bolt fired by the Solar Flare orb. Ignites what it hits (no fire blocks).
 */
public class SolarBoltProjectile extends AbstractMagicProjectile {
    public static final int IGNITE_SECONDS = 2;

    public SolarBoltProjectile(EntityType<? extends SolarBoltProjectile> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public SolarBoltProjectile(Level level, LivingEntity shooter) {
        this(ASEntities.SOLAR_BOLT.get(), level);
        setOwner(shooter);
    }

    @Override
    public void trailParticles() {
        Vec3 random = Utils.getRandomVec3(0.05);
        level().addParticle(ParticleHelper.FIRE, getX() + random.x, getY() + 0.2 + random.y, getZ() + random.z, 0, 0, 0);
        if (tickCount % 2 == 0) {
            level().addParticle(ParticleHelper.EMBERS, getX(), getY() + 0.2, getZ(), random.x, random.y, random.z);
        }
    }

    @Override
    public void impactParticles(double x, double y, double z) {
        MagicManager.spawnParticles(level(), ParticleHelper.FIRE, x, y, z, 25, 0.25, 0.25, 0.25, 0.12, false);
        MagicManager.spawnParticles(level(), ParticleHelper.FIERY_SPARKS, x, y, z, 10, 0.2, 0.2, 0.2, 0.3, false);
        ASFx.blastwave(level(), ASFx.SOLAR_ORANGE, new Vec3(x, y - 0.3, z), 1.3f);
    }

    @Override
    public float getSpeed() {
        return 0.75f;
    }

    @Override
    public Optional<net.minecraft.core.Holder<SoundEvent>> getImpactSound() {
        return Optional.of(AetherSoundEvents.ENTITY_FIRE_CRYSTAL_EXPLODE);
    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult result) {
        super.onHitBlock(result);
        discard();
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult result) {
        super.onHitEntity(result);
        if (DamageSources.applyDamage(result.getEntity(), damage, ASSpells.SOLAR_FLARE.get().getDamageSource(this, getOwner()))) {
            result.getEntity().igniteForSeconds(IGNITE_SECONDS);
        }
        discard();
    }
}
