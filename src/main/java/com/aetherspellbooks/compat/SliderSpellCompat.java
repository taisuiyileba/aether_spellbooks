package com.aetherspellbooks.compat;

import com.aetherspellbooks.config.ASConfig;
import com.aetherteam.aether.AetherTags;
import com.aetherteam.aether.block.AetherBlocks;
import com.aetherteam.aether.entity.monster.dungeon.boss.Slider;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.entity.spells.spectral_hammer.SpectralHammer;
import io.redspace.ironsspellbooks.entity.spells.thrown_item.ThrownItemProjectile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ItemAbilities;
import org.jetbrains.annotations.Nullable;

/** Narrow compatibility with the Slider's mining-tool rules, without bypassing its damage pipeline. */
public final class SliderSpellCompat {
    public static final double HAMMER_RANGE = 16;
    // Iron's Spells 3.16.3: body pivot y=7, head's lowest contact corner (y=17.55,z=-7).
    // At impact the body translates (0,5,1), rotates X by -50 degrees, and renders at 2/16 scale.
    // Align the contact corner, not the head centre, so the head does not sink inside the Slider.
    private static final double HAMMER_HEAD_HEIGHT = (7 + 5 + 10.55 * Math.cos(Math.toRadians(50))
            - 7 * Math.sin(Math.toRadians(50))) / 8;
    private static final double HAMMER_HEAD_FORWARD = (10.55 * Math.sin(Math.toRadians(50))
            + 7 * Math.cos(Math.toRadians(50)) - 1) / 8;

    private SliderSpellCompat() {}

    public static boolean isMiningTool(ItemStack stack) {
        return !stack.isEmpty() && (stack.canPerformAction(ItemAbilities.PICKAXE_DIG)
                || stack.is(AetherTags.Items.SLIDER_DAMAGING_ITEMS)
                || stack.isCorrectToolForDrops(AetherBlocks.CARVED_STONE.get().defaultBlockState()));
    }

    /** Null means that the Aether should handle this source exactly as before. */
    @Nullable
    public static LivingEntity compatibleAttacker(Slider slider, DamageSource source) {
        if (ASConfig.thrownToolsDamageSlider()
                && source.getDirectEntity() instanceof ThrownItemProjectile thrown
                && isMiningTool(thrown.getThrownItem())
                && thrown.getOwner() instanceof LivingEntity owner) {
            return owner;
        }
        if (ASConfig.spectralHammerDamageSlider()
                && source instanceof SpellDamageSource spellSource
                && spellSource.spell() == SpellRegistry.SPECTRAL_HAMMER_SPELL.get()
                && source.getDirectEntity() instanceof SpectralHammer hammer
                && source.getEntity() instanceof LivingEntity caster
                && ((SliderHammer) hammer).aetherSpellbooks$isStrikeFor(slider, caster)) {
            return caster;
        }
        return null;
    }

    public static boolean canAttackFrom(Slider slider, LivingEntity caster) {
        return slider.level().getDifficulty() != Difficulty.PEACEFUL
                && (slider.getDungeon() == null || slider.getDungeon().isPlayerWithinRoomInterior(caster));
    }

    /** Respect blocks and pickable entities; the invulnerable cosmetic hammer cannot block another cast. */
    @Nullable
    public static Slider findHammerTarget(LivingEntity caster) {
        if (!ASConfig.spectralHammerDamageSlider() || !caster.isAlive() || caster.isSpectator()
                || caster instanceof ServerPlayer player && player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) {
            return null;
        }
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(HAMMER_RANGE));
        BlockHitResult block = caster.level().clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double distance = eye.distanceToSqr(block.getLocation());
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(caster, eye, end,
                caster.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1),
                entity -> entity.isAlive() && entity.isPickable() && !entity.isSpectator()
                        && !(entity instanceof SpectralHammer), distance);
        return hit != null && hit.getEntity() instanceof Slider slider && canAttackFrom(slider, caster) ? slider : null;
    }

    public static float hammerDamage(int spellLevel, @Nullable LivingEntity caster) {
        AbstractSpell spell = SpellRegistry.SPECTRAL_HAMMER_SPELL.get();
        return (float) (ASConfig.sliderHammerBaseDamage()
                + Math.max(0, spellLevel - 1) * ASConfig.sliderHammerDamagePerLevel())
                * spell.getEntityPowerMultiplier(caster);
    }

    public static void spawnHammer(LivingEntity caster, Slider target, int spellLevel) {
        Vec3 impact = hammerImpact(target);
        Vec3 toward = new Vec3(target.getX() - caster.getX(), 0, target.getZ() - caster.getZ());
        float yaw = toward.lengthSqr() < 1.0E-6 ? caster.getYRot()
                : (float) Math.toDegrees(Math.atan2(-toward.x, toward.z));
        Vec3 forward = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
        // MISS retains the original swing and sounds but skips its block-removal branch.
        SpectralHammer hammer = new SpectralHammer(caster.level(), caster,
                BlockHitResult.miss(impact, Direction.UP, BlockPos.containing(impact)), 0, 0);
        // The entity origin is near the handle, not the hammer head. Place the animated
        // head at the upper surface, and initialize previous rotations to avoid a spawn-frame turn.
        hammer.setPos(impact.subtract(forward.scale(HAMMER_HEAD_FORWARD)).add(0, -HAMMER_HEAD_HEIGHT, 0));
        hammer.setYRot(yaw);
        hammer.setYBodyRot(yaw);
        hammer.setYHeadRot(yaw);
        hammer.yRotO = hammer.yBodyRotO = hammer.yHeadRotO = yaw;
        hammer.setXRot(0);
        hammer.xRotO = 0;
        ((SliderHammer) hammer).aetherSpellbooks$prepareStrike(caster, target, hammerDamage(spellLevel, caster));
        caster.level().addFreshEntity(hammer);
    }

    public static Vec3 hammerImpact(Slider target) {
        return new Vec3(target.getX(), target.getBoundingBox().maxY - 0.05, target.getZ());
    }

    public static boolean unobstructed(LivingEntity caster, Vec3 impact) {
        BlockHitResult hit = caster.level().clip(new ClipContext(caster.getEyePosition(), impact,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }
}
