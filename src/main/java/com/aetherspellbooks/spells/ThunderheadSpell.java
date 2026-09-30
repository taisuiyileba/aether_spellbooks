package com.aetherspellbooks.spells;

import com.aetherspellbooks.entity.StormCloud;
import com.aetherspellbooks.registry.ASParticles;
import com.aetherspellbooks.util.ASFx;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Gathers a thundercloud over the spot the caster aims at (like the Valkyrie Queen's storms). It drifts after nearby
 * enemies and calls lightning down on those beneath it; each bolt forks to one more foe, wet targets take extra damage.
 * One cloud per caster.
 */
public class ThunderheadSpell extends AetherSpell {
    public static final float RANGE = 28f;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(SchoolRegistry.LIGHTNING_RESOURCE)
            .setMaxLevel(6)
            .setCooldownSeconds(30)
            .build();

    public ThunderheadSpell() {
        super("thunderhead");
        this.baseManaCost = 60;
        this.manaCostPerLevel = 10;
        this.baseSpellPower = 5;
        this.spellPowerPerLevel = 1;
        this.castTime = 12;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.CHARGE_RAISED_HAND;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SpellAnimations.CAST_T_POSE;
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundRegistry.THUNDERSTORM_PREPARE.get());
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundRegistry.LIGHTNING_CAST.get());
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)),
                Component.translatable("ui.aether_spellbooks.strike_interval", Utils.stringTruncation(getStrikeInterval(spellLevel) / 20f, 2)),
                Component.translatable("ui.irons_spellbooks.duration", Utils.timeFromTicks(getDurationTicks(spellLevel), 1)),
                Component.translatable("ui.aether_spellbooks.chains", 1),
                Component.translatable("ui.aether_spellbooks.wet_bonus", (int) ((StormCloud.WET_MULTIPLIER - 1) * 100))
        );
    }

    public static int getDurationTicks(int spellLevel) {
        return (5 + spellLevel) * 20;
    }

    public static int getStrikeInterval(int spellLevel) {
        return 30 - 2 * spellLevel;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel server) {
            level.getEntitiesOfClass(StormCloud.class, caster.getBoundingBox().inflate(64), cloud -> cloud.getOwner() == caster).forEach(StormCloud::discard);
            Vec3 target = aimPoint(server, caster);
            // gather the cloud above the target, under any ceiling
            Vec3 top = target.add(0, StormCloud.HOVER_HEIGHT, 0);
            var ceiling = server.clip(new ClipContext(target.add(0, 0.5, 0), top, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            Vec3 at = ceiling.getType() == HitResult.Type.MISS ? top : ceiling.getLocation().subtract(0, 1, 0);

            StormCloud cloud = new StormCloud(level, caster);
            cloud.setPos(at);
            cloud.setDamage(getSpellPower(spellLevel, caster));
            cloud.setLifetime(getDurationTicks(spellLevel));
            cloud.setStrikeInterval(getStrikeInterval(spellLevel));
            cloud.setRadius(4.5f + 0.25f * spellLevel);
            level.addFreshEntity(cloud);

            ASFx.burst(level, ASParticles.STORM_PUFF.get(), at, 24, 1.2, 0.03);
            ASFx.burst(level, ParticleHelper.ELECTRICITY, at, 10, 0.8, 0.05);
            ASFx.line(level, ParticleHelper.ELECTRIC_SPARKS, caster.getEyePosition(), at, 12, 0.1);
        }
        super.onCast(level, spellLevel, caster, castSource, playerMagicData);
    }

    /** Where the caster is looking: the first block or entity within range, then down to the ground below it. */
    private static Vec3 aimPoint(ServerLevel level, LivingEntity caster) {
        HitResult hit = Utils.raycastForEntity(level, caster, RANGE, true);
        Vec3 point = hit.getLocation();
        var down = level.clip(new ClipContext(point.add(0, 0.5, 0), point.subtract(0, 16, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, caster));
        return down.getType() == HitResult.Type.MISS ? point : down.getLocation();
    }
}
