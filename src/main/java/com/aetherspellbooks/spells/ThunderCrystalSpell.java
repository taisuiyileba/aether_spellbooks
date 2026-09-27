package com.aetherspellbooks.spells;

import com.aetherspellbooks.entity.ThunderCrystalProjectile;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Launches homing thunder crystals, like the Valkyrie Queen.
 */
public class ThunderCrystalSpell extends AetherSpell {
    public static final float TARGET_RANGE = 32f;
    private static final float SPREAD_DEGREES = 14f;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.UNCOMMON)
            .setSchoolResource(SchoolRegistry.LIGHTNING_RESOURCE)
            .setMaxLevel(8)
            .setCooldownSeconds(10)
            .build();

    public ThunderCrystalSpell() {
        super("thunder_crystal");
        this.baseManaCost = 30;
        this.manaCostPerLevel = 5;
        this.baseSpellPower = 4;
        this.spellPowerPerLevel = 1;
        this.castTime = 0;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public CastType getCastType() {
        return CastType.INSTANT;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.AMETHYST_CLUSTER_BREAK);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(getDamage(spellLevel, caster), 1)),
                Component.translatable("ui.irons_spellbooks.projectile_count", getCrystalCount(spellLevel)),
                Component.translatable("ui.irons_spellbooks.radius", Utils.stringTruncation(ThunderCrystalProjectile.SHOCK_RADIUS, 1)),
                Component.translatable("ui.aether_spellbooks.chains", getChainCount(spellLevel))
        );
    }

    /** Extra enemies each crystal arcs to: 0 at level 1-2, then +1 every 3 levels. */
    public static int getChainCount(int spellLevel) {
        return spellLevel / 3;
    }

    public static int getCrystalCount(int spellLevel) {
        return 1 + spellLevel / 3;
    }

    public float getDamage(int spellLevel, LivingEntity caster) {
        return getSpellPower(spellLevel, caster);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData playerMagicData) {
        LivingEntity target = null;
        HitResult hit = Utils.raycastForEntity(level, caster, TARGET_RANGE, true, 0.35f);
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living) {
            target = living;
        } else if (caster instanceof Mob mob && mob.getTarget() != null && mob.distanceToSqr(mob.getTarget()) < TARGET_RANGE * TARGET_RANGE) {
            // spellcasting mobs home in on whatever they are fighting
            target = mob.getTarget();
        }
        int count = getCrystalCount(spellLevel);
        Vec3 look = caster.getLookAngle();
        Vec3 origin = caster.position().add(0, caster.getEyeHeight() - 0.2, 0);
        for (int i = 0; i < count; i++) {
            float yawOffset = (i - (count - 1) / 2f) * SPREAD_DEGREES;
            Vec3 direction = look.yRot(yawOffset * Mth.DEG_TO_RAD).add(0, 0.08 * i, 0).normalize();
            ThunderCrystalProjectile crystal = new ThunderCrystalProjectile(level, caster);
            crystal.setPos(origin.add(direction.scale(0.6)));
            crystal.shoot(direction);
            crystal.setDamage(getDamage(spellLevel, caster));
            crystal.setChains(getChainCount(spellLevel));
            if (target != null) {
                crystal.setHomingTarget(target);
            }
            level.addFreshEntity(crystal);
        }
        super.onCast(level, spellLevel, caster, castSource, playerMagicData);
    }
}
