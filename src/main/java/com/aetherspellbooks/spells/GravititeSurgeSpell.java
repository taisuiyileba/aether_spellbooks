package com.aetherspellbooks.spells;

import com.aetherspellbooks.registry.ASParticles;
import com.aetherspellbooks.util.ASFx;
import com.aetherspellbooks.util.ASScheduler;
import com.aetherteam.aether.client.AetherSoundEvents;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.particle.FallingBlockParticleOption;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Like gravitite ore floating free of the ground: flings nearby enemies into the air,
 * then reverses gravity and slams them back down.
 */
public class GravititeSurgeSpell extends AetherSpell {
    public static final int SLAM_DELAY_TICKS = 16;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(6)
            .setCooldownSeconds(16)
            .build();

    public GravititeSurgeSpell() {
        super("gravitite_surge");
        this.baseManaCost = 45;
        this.manaCostPerLevel = 8;
        this.baseSpellPower = 6;
        this.spellPowerPerLevel = 2;
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
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.CHARGE_RAISED_HAND;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SpellAnimations.SELF_CAST_TWO_HANDS;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.EVOKER_CAST_SPELL);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(getDamage(spellLevel, caster), 1)),
                Component.translatable("ui.irons_spellbooks.radius", Utils.stringTruncation(getRadius(spellLevel), 1))
        );
    }

    public static float getRadius(int spellLevel) {
        return 3f + 0.5f * spellLevel;
    }

    /** Mobs only cast it when their target is inside the radius. */
    @Override
    public boolean shouldAIStopCasting(int spellLevel, Mob mob, LivingEntity target) {
        float radius = getRadius(spellLevel) * 0.9f;
        return target == null || mob.distanceToSqr(target) > radius * radius;
    }

    public float getDamage(int spellLevel, LivingEntity caster) {
        return getSpellPower(spellLevel, caster);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel server) {
            float radius = getRadius(spellLevel);
            float damage = getDamage(spellLevel, caster);
            double lift = 0.95 + 0.04 * spellLevel;
            Vec3 center = caster.position();

            List<LivingEntity> targets = server.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius, 2, radius),
                    t -> t != caster && t.isAlive() && !t.isSpectator() && !t.isAlliedTo(caster) && !caster.isAlliedTo(t)
                            && t.distanceToSqr(caster) <= radius * radius);

            // Rise: pink shockwave, motes lifting off the ground, torn-up blocks under each target
            ASFx.blastwave(server, ASFx.GRAVITITE_PINK, center, radius);
            ASFx.ring(server, ASParticles.GRAVITY_MOTE.get(), center.add(0, 0.2, 0), radius * 0.6, 32, 0.25);
            ASFx.burst(server, ASParticles.GRAVITY_MOTE.get(), center.add(0, 0.5, 0), 20, radius * 0.4, 0.05);
            server.playSound(null, caster.getX(), caster.getY(), caster.getZ(), AetherSoundEvents.ITEM_ARMOR_EQUIP_GRAVITITE.get(), SoundSource.PLAYERS, 1.2f, 0.7f);
            for (LivingEntity target : targets) {
                double resist = 1 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
                target.setDeltaMovement(target.getDeltaMovement().multiply(0.2, 0, 0.2).add(0, lift * Math.max(0.3, resist), 0));
                target.hurtMarked = true;
                BlockState ground = server.getBlockState(target.blockPosition().below());
                if (!ground.isAir()) {
                    Vec3 feet = target.position();
                    for (int i = 0; i < 4; i++) {
                        server.sendParticles(new FallingBlockParticleOption(ground, new Vec3(Utils.getRandomScaled(0.15), 0.4 + server.random.nextFloat() * 0.3, Utils.getRandomScaled(0.15))),
                                feet.x, feet.y + 0.1, feet.z, 1, 0.3, 0, 0.3, 0);
                    }
                }
                ASFx.helix(server, ASParticles.GRAVITY_MOTE.get(), target.position(), target.getBbWidth() * 0.8, target.getBbHeight() + 0.5, 12, 2);
            }

            // Fall: gravity reverses and slams everything down
            ASScheduler.schedule(SLAM_DELAY_TICKS, () -> {
                boolean any = false;
                for (LivingEntity target : targets) {
                    if (!target.isAlive() || target.level() != server) {
                        continue;
                    }
                    any = true;
                    target.setDeltaMovement(target.getDeltaMovement().multiply(0.1, 0, 0.1).add(0, -2.4, 0));
                    target.hurtMarked = true;
                    DamageSources.applyDamage(target, damage, getDamageSource(caster));
                    Vec3 pos = target.position();
                    ASFx.burst(server, ASParticles.GRAVITY_MOTE.get(), pos.add(0, target.getBbHeight() * 0.5, 0), 16, 0.4, 0.2);
                    Vec3 landing = Utils.moveToRelativeGroundLevel(server, pos, 12);
                    BlockState ground = server.getBlockState(net.minecraft.core.BlockPos.containing(landing).below());
                    if (!ground.isAir()) {
                        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), landing.x, landing.y + 0.1, landing.z, 30, 0.6, 0.1, 0.6, 0.2);
                        ASFx.blastwave(server, ASFx.GRAVITITE_PINK, landing, 1.8f);
                    }
                }
                if (any) {
                    ASFx.shake(server, center, radius + 8, 12);
                    server.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 1.5f);
                    server.playSound(null, center.x, center.y, center.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 0.6f);
                }
            });
        }
        super.onCast(level, spellLevel, caster, castSource, playerMagicData);
    }
}
