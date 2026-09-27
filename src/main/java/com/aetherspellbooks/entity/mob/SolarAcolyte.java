package com.aetherspellbooks.entity.mob;

import com.aetherspellbooks.registry.ASSpells;
import com.aetherspellbooks.util.ASFx;
import com.aetherteam.aether.client.AetherSoundEvents;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import io.redspace.ironsspellbooks.entity.mobs.goals.SpellBarrageGoal;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardAttackGoal;
import io.redspace.ironsspellbooks.util.ParticleHelper;
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
 * A devotee of the Sun Spirit. Fights with fire, kindles miniature suns and calls fire minions to its side.
 * Its sun halo and burning eyes are drawn by the client.
 */
public class SolarAcolyte extends AetherSpellcaster {
    public SolarAcolyte(EntityType<? extends AbstractSpellCastingMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder prepareAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0)
                .add(Attributes.MAX_HEALTH, 50.0)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.MOVEMENT_SPEED, .25);
    }

    @Override
    protected void registerSpellGoals() {
        // Every so often, kindle a miniature Sun Spirit that rains bolts on the target
        this.goalSelector.addGoal(1, new SpellBarrageGoal(this, ASSpells.SOLAR_FLARE.get(), 1, 2, 400, 600, 1));
        this.goalSelector.addGoal(2, new WizardAttackGoal(this, 1.2f, 25, 55)
                .setSpells(
                        List.of(SpellRegistry.FIREBOLT_SPELL.get(), SpellRegistry.FIREBOLT_SPELL.get(), SpellRegistry.FIREBOLT_SPELL.get(), SpellRegistry.BLAZE_STORM_SPELL.get()),
                        List.of(SpellRegistry.HEAT_SURGE_SPELL.get()),
                        List.of(SpellRegistry.BURNING_DASH_SPELL.get()),
                        List.of())
                .setSpellQuality(.3f, .55f)
                .setSingleUseSpell(ASSpells.SUMMON_FIRE_MINION.get(), 60, 140, 1, 2));
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide && random.nextInt(3) == 0) {
            // embers drifting up from the robe and cupped hands
            Vec3 side = Vec3.directionFromRotation(0, yBodyRot + 90).scale(random.nextBoolean() ? 0.4 : -0.4);
            level().addParticle(ParticleHelper.EMBERS, getX() + side.x, getY() + 0.9 + random.nextDouble() * 0.3, getZ() + side.z,
                    0, 0.03 + random.nextDouble() * 0.02, 0);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) {
            // its borrowed sunfire escapes in a harmless flare
            Vec3 center = position().add(0, getBbHeight() * 0.5, 0);
            ASFx.blastwave(server, ASFx.SOLAR_ORANGE, position(), 2.5f);
            ASFx.burst(server, ParticleHelper.FIRE, center, 30, 0.4, 0.12);
            ASFx.burst(server, ParticleHelper.EMBERS, center, 20, 0.5, 0.08);
            server.playSound(null, getX(), getY(), getZ(), AetherSoundEvents.ENTITY_SUN_SPIRIT_SHOOT_FIRE.get(), getSoundSource(), 1f, 0.6f);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BLAZE_BURN;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ILLUSIONER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ILLUSIONER_DEATH;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }
}
