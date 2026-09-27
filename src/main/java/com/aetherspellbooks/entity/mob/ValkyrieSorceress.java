package com.aetherspellbooks.entity.mob;

import com.aetherspellbooks.registry.ASParticles;
import com.aetherspellbooks.registry.ASSpells;
import com.aetherspellbooks.util.ASFx;
import com.aetherteam.aether.client.AetherSoundEvents;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardAttackGoal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A Valkyrie who traded her lance for lightning. She glides on her wings instead of falling,
 * leaps up to foes standing above her and fights with the Aether's thunder and holy magic.
 */
public class ValkyrieSorceress extends AetherSpellcaster {
    /** Vertical speed is damped while airborne, like the Aether's own Valkyries. */
    private static final double GLIDE_DAMPING = 0.6;
    private int leapCooldown = 60;

    // Client-side wing animation state
    private float wingSpread;
    private float wingSpreadO;
    private float flapPhase;
    private float flapPhaseO;

    public ValkyrieSorceress(EntityType<? extends AbstractSpellCastingMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder prepareAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0)
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.MOVEMENT_SPEED, .26);
    }

    @Override
    protected void registerSpellGoals() {
        this.goalSelector.addGoal(2, new WizardAttackGoal(this, 1.25f, 30, 60)
                .setSpells(
                        List.of(ASSpells.THUNDER_CRYSTAL.get(), ASSpells.THUNDER_CRYSTAL.get(), ASSpells.ZEPHYR_BLAST.get(), SpellRegistry.GUIDING_BOLT_SPELL.get()),
                        List.of(ASSpells.GRAVITITE_SURGE.get()),
                        List.of(ASSpells.VALKYRIE_LUNGE.get()),
                        List.of(SpellRegistry.HEAL_SPELL.get()))
                .setSpellQuality(.25f, .55f)
                .setSingleUseSpell(SpellRegistry.FORTIFY_SPELL.get(), 40, 100, 2, 4)
                .setDrinksPotions());
    }

    @Override
    public void aiStep() {
        super.aiStep();
        Vec3 motion = getDeltaMovement();
        boolean gliding = !onGround() && motion.y < 0 && !isInWater() && !isPassenger();
        if (gliding) {
            setDeltaMovement(motion.multiply(1, GLIDE_DAMPING, 1));
            resetFallDistance();
        }
        if (level().isClientSide) {
            tickWings();
            if (gliding && random.nextInt(4) == 0) {
                Vec3 back = Vec3.directionFromRotation(0, yBodyRot).scale(-0.35);
                level().addParticle(ASParticles.FEATHER.get(), getX() + back.x + (random.nextDouble() - 0.5) * 1.4, getY() + 1.3,
                        getZ() + back.z + (random.nextDouble() - 0.5) * 1.4, 0, -0.02, 0);
            }
        }
    }

    private void tickWings() {
        wingSpreadO = wingSpread;
        flapPhaseO = flapPhase;
        // the client only learns onGround from movement packets, so also look for ground right below her
        boolean grounded = onGround() || !level().noCollision(this, getBoundingBox().move(0, -0.08, 0));
        float target = isCasting() ? 1f : grounded ? 0f : 0.75f;
        wingSpread += (target - wingSpread) * 0.15f;
        flapPhase += 0.08f + 0.32f * wingSpread;
    }

    /** 0 = folded at rest, 1 = fully spread (casting); interpolated for rendering. */
    public float getWingSpread(float partialTick) {
        return wingSpreadO + (wingSpread - wingSpreadO) * partialTick;
    }

    public float getFlapPhase(float partialTick) {
        return flapPhaseO + (flapPhase - flapPhaseO) * partialTick;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (leapCooldown > 0) {
            leapCooldown--;
            return;
        }
        LivingEntity target = getTarget();
        if (target != null && onGround() && !isCasting() && target.getY() > getY() + 2.5 && distanceToSqr(target) < 20 * 20 && hasLineOfSight(target)) {
            leapToward(target);
        }
    }

    /** A wing-assisted leap up onto the ledge the target stands on. */
    private void leapToward(LivingEntity target) {
        Vec3 horizontal = target.position().subtract(position()).multiply(1, 0, 1);
        double distance = horizontal.length();
        Vec3 push = distance > 0.01 ? horizontal.scale(Math.min(0.8, distance * 0.08) / distance) : Vec3.ZERO;
        double rise = Math.min(1.3, 0.6 + (target.getY() - getY()) * 0.08);
        setDeltaMovement(push.x, rise, push.z);
        hasImpulse = true;
        leapCooldown = 80 + random.nextInt(60);
        if (level() instanceof ServerLevel server) {
            ASFx.blastwave(server, ASFx.VALKYRIE_GOLD, position(), 1.4f);
            ASFx.burst(server, ASParticles.FEATHER.get(), position().add(0, 1, 0), 10, 0.5, 0.05);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_FLAP, getSoundSource(), 0.8f, 1.4f);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return AetherSoundEvents.ENTITY_VALKYRIE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return AetherSoundEvents.ENTITY_VALKYRIE_DEATH.get();
    }
}
