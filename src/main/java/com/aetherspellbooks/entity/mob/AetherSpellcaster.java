package com.aetherspellbooks.entity.mob;

import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import io.redspace.ironsspellbooks.entity.mobs.goals.PatrolNearLocationGoal;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardRecoverGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Base class of the Aether's hostile spellcasters. They share Iron's Spells' wizard AI, never harm each other,
 * and spawn on the surface of Aether islands.
 */
public abstract class AetherSpellcaster extends AbstractSpellCastingMob implements Enemy {
    protected AetherSpellcaster(EntityType<? extends AbstractSpellCastingMob> type, Level level) {
        super(type, level);
        this.xpReward = 25;
    }

    /** Adds the spellcasting goal(s) at priorities 1-2. */
    protected abstract void registerSpellGoals();

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        registerSpellGoals();
        this.goalSelector.addGoal(3, new PatrolNearLocationGoal(this, 30, .75f));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(10, new WizardRecoverGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, AetherSpellcaster.class).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean isAlliedTo(Entity entity) {
        return super.isAlliedTo(entity)
                || entity instanceof AetherSpellcaster
                || entity instanceof IMagicSummon summon && summon.getSummoner() instanceof AetherSpellcaster;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    /** Natural spawns need a valid block below and open sky; any spawn needs a difficulty above peaceful. */
    public static <T extends Mob> boolean checkSpawnRules(EntityType<T> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (level.getDifficulty() == Difficulty.PEACEFUL || !Mob.checkMobSpawnRules(type, level, reason, pos, random)) {
            return false;
        }
        return reason != MobSpawnType.NATURAL || level.canSeeSky(pos);
    }
}
