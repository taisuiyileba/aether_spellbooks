package com.aetherspellbooks.entity;

import com.aetherspellbooks.registry.ASEntities;
import com.aetherspellbooks.registry.ASSpells;
import com.aetherteam.aether.entity.monster.dungeon.FireMinion;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import io.redspace.ironsspellbooks.entity.mobs.goals.GenericCopyOwnerTargetGoal;
import io.redspace.ironsspellbooks.entity.mobs.goals.GenericFollowOwnerGoal;
import io.redspace.ironsspellbooks.entity.mobs.goals.GenericHurtByTargetGoal;
import io.redspace.ironsspellbooks.entity.mobs.goals.GenericOwnerHurtByTargetGoal;
import io.redspace.ironsspellbooks.entity.mobs.goals.GenericOwnerHurtTargetGoal;
import io.redspace.ironsspellbooks.entity.mobs.goals.GenericProtectOwnerTargetGoal;
import com.aetherspellbooks.util.ASFx;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.jetbrains.annotations.NotNull;

/**
 * A friendly Fire Minion bound to its summoner. Reuses the Aether's Fire Minion model and renderer.
 */
public class SummonedFireMinion extends FireMinion implements IMagicSummon {
    public static final int IGNITE_SECONDS = 4;
    public static final float BURST_RADIUS = 2.5f;

    public SummonedFireMinion(EntityType<? extends FireMinion> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public SummonedFireMinion(Level level) {
        this(ASEntities.SUMMONED_FIRE_MINION.get(), level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.4, true));
        this.goalSelector.addGoal(5, new GenericFollowOwnerGoal(this, this::getSummoner, 1.1, 10, 3, false, 24));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0f));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new GenericOwnerHurtByTargetGoal(this, this::getSummoner));
        this.targetSelector.addGoal(2, new GenericOwnerHurtTargetGoal(this, this::getSummoner));
        this.targetSelector.addGoal(3, new GenericCopyOwnerTargetGoal(this, this::getSummoner));
        this.targetSelector.addGoal(4, new GenericHurtByTargetGoal(this, entity -> entity == getSummoner()).setAlertOthers());
        this.targetSelector.addGoal(5, new GenericProtectOwnerTargetGoal(this, this::getSummoner));
    }

    @Override
    public boolean doHurtTarget(@NotNull Entity target) {
        boolean hit = Utils.doMeleeAttack(this, target, ASSpells.SUMMON_FIRE_MINION.get().getDamageSource(this, getSummoner()));
        if (hit) {
            // Sets the target on fire without placing fire blocks (fire blocks are banned in the Aether).
            target.igniteForSeconds(IGNITE_SECONDS);
        }
        return hit;
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
            burst();
            discard();
        }
    }

    @Override
    public void die(@NotNull DamageSource source) {
        this.onDeathHelper();
        if (!level().isClientSide && !isRemoved()) {
            burst();
        }
        super.die(source);
    }

    /**
     * Fire Minions go out with a bang: a burst of flame that scorches nearby enemies.
     */
    private void burst() {
        Vec3 center = position().add(0, 0.8, 0);
        MagicManager.spawnParticles(level(), ParticleHelper.FIRE, center.x, center.y, center.z, 40, 0.4, 0.6, 0.4, 0.1, false);
        MagicManager.spawnParticles(level(), ParticleHelper.FIERY_SPARKS, center.x, center.y, center.z, 16, 0.3, 0.3, 0.3, 0.4, false);
        MagicManager.spawnParticles(level(), ParticleTypes.LARGE_SMOKE, center.x, center.y, center.z, 10, 0.3, 0.4, 0.3, 0.02, false);
        ASFx.blastwave(level(), ASFx.SOLAR_ORANGE, position(), BURST_RADIUS);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 1f, 0.8f);
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.8f;
        var source = ASSpells.SUMMON_FIRE_MINION.get().getDamageSource(this, getSummoner());
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(BURST_RADIUS))) {
            if (target == this || isAlliedTo(target) || target.isSpectator() || target instanceof SummonedFireMinion) {
                continue;
            }
            if (DamageSources.applyDamage(target, damage, source)) {
                target.igniteForSeconds(IGNITE_SECONDS);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && random.nextInt(4) == 0) {
            level().addParticle(ParticleHelper.EMBERS, getRandomX(0.6), getRandomY(), getRandomZ(0.6), 0, 0.03, 0);
        }
    }

    @Override
    public void onRemovedFromLevel() {
        this.onRemovedHelper(this);
        super.onRemovedFromLevel();
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
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPreventingPlayerRest(@NotNull Player player) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean canAttack(@NotNull LivingEntity target) {
        return super.canAttack(target) && !isAlliedTo(target);
    }
}
