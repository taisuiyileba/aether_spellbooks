package com.aetherspellbooks.spells;

import com.aetherspellbooks.registry.ASParticles;
import com.aetherspellbooks.util.ASFx;
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
import io.redspace.ironsspellbooks.particle.SparkParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A Valkyrie's charge: dash forward on golden wings, striking everything in the path,
 * then glide down softly.
 */
public class ValkyrieLungeSpell extends AetherSpell {
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.UNCOMMON)
            .setSchoolResource(SchoolRegistry.HOLY_RESOURCE)
            .setMaxLevel(6)
            .setCooldownSeconds(8)
            .build();

    public ValkyrieLungeSpell() {
        super("valkyrie_lunge");
        this.baseManaCost = 30;
        this.manaCostPerLevel = 5;
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
        return SpellAnimations.ONE_HANDED_HORIZONTAL_SWING_ANIMATION;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.PLAYER_ATTACK_SWEEP);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(getDamage(spellLevel, caster), 1)),
                Component.translatable("ui.irons_spellbooks.distance", Utils.stringTruncation(getDistance(spellLevel), 0))
        );
    }

    public static float getDistance(int spellLevel) {
        return 6 + spellLevel;
    }

    public float getDamage(int spellLevel, LivingEntity caster) {
        return getSpellPower(spellLevel, caster);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel server) {
            Vec3 look = caster.getLookAngle();
            Vec3 direction = new Vec3(look.x, Math.max(-0.3, Math.min(0.3, look.y)), look.z).normalize();
            Vec3 start = caster.position().add(0, caster.getBbHeight() * 0.5, 0);
            Vec3 end = start.add(direction.scale(getDistance(spellLevel)));
            var clip = server.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (clip.getType() != HitResult.Type.MISS) {
                end = clip.getLocation().subtract(direction.scale(0.6));
            }
            double distance = start.distanceTo(end);

            // Strike everything along the path, once each
            float damage = getDamage(spellLevel, caster);
            Set<LivingEntity> struck = new HashSet<>();
            SparkParticleOptions goldSpark = new SparkParticleOptions(ASFx.VALKYRIE_GOLD);
            for (double d = 0; d <= distance; d += 0.5) {
                Vec3 p = start.add(direction.scale(d));
                for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(1.1))) {
                    if (target == caster || target.isAlliedTo(caster) || caster.isAlliedTo(target) || !struck.add(target)) {
                        continue;
                    }
                    if (DamageSources.applyDamage(target, damage, getDamageSource(caster))) {
                        target.knockback(0.6, -direction.x, -direction.z);
                        Vec3 c = target.getBoundingBox().getCenter();
                        server.sendParticles(goldSpark, c.x, c.y, c.z, 12, 0.2, 0.2, 0.2, 0.4);
                        server.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
                    }
                }
            }

            // Golden trail of light and feathers
            ASFx.line(server, ASParticles.SKY_SPARKLE.get(), start, end, (int) (distance * 3), 0.25);
            ASFx.line(server, ASParticles.FEATHER.get(), start, end, (int) (distance * 1.2), 0.4);
            ASFx.blastwave(server, ASFx.VALKYRIE_GOLD, caster.position(), 1.5f);

            // The dash itself
            caster.setDeltaMovement(direction.scale(0.45 * distance / 2.5).add(0, 0.12, 0));
            caster.hurtMarked = true;
            caster.resetFallDistance();
            caster.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 50, 0, false, false, true));
            server.playSound(null, caster.getX(), caster.getY(), caster.getZ(), AetherSoundEvents.ITEM_ARMOR_EQUIP_VALKYRIE.get(), SoundSource.PLAYERS, 1f, 1.2f);
        }
        super.onCast(level, spellLevel, caster, castSource, playerMagicData);
    }
}
