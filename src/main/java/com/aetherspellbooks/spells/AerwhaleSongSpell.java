package com.aetherspellbooks.spells;

import com.aetherspellbooks.entity.SpectralAerwhale;
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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Calls a spectral Aerwhale that swims overhead from behind the caster along the way they face. Every half second it
 * sings: the song rolls down to the ground below, damaging and tossing up foes, and lifting the caster and allies
 * with Slow Falling and Jump Boost.
 */
public class AerwhaleSongSpell extends AetherSpell {
    public static final double SPEED = 0.5;
    public static final double PATH_LENGTH = 44;
    public static final double START_BEHIND = 10;
    public static final double HEIGHT = 6;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(SchoolRegistry.NATURE_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(35)
            .build();

    public AerwhaleSongSpell() {
        super("aerwhale_song");
        this.baseManaCost = 70;
        this.manaCostPerLevel = 10;
        this.baseSpellPower = 4;
        this.spellPowerPerLevel = 1;
        this.castTime = 25;
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
        return Optional.of(AetherSoundEvents.ENTITY_AERWHALE_AMBIENT.get());
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aether_spellbooks.damage_per_song", Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)),
                Component.translatable("ui.irons_spellbooks.radius", Utils.stringTruncation(getSongRadius(spellLevel), 1)),
                Component.translatable("ui.aether_spellbooks.songs", getLifetime() / SpectralAerwhale.SONG_INTERVAL - 1),
                Component.translatable("ui.aether_spellbooks.whale_blessing", Utils.timeFromTicks(SpectralAerwhale.BLESSING_TICKS, 1))
        );
    }

    public static float getSongRadius(int spellLevel) {
        return 3.5f + 0.25f * spellLevel;
    }

    public static int getLifetime() {
        return (int) (PATH_LENGTH / SPEED);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData playerMagicData) {
        if (!level.isClientSide) {
            Vec3 look = caster.getLookAngle().multiply(1, 0, 1);
            Vec3 heading = look.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : look.normalize();
            Vec3 start = caster.position().subtract(heading.scale(START_BEHIND)).add(0, HEIGHT, 0);
            SpectralAerwhale whale = new SpectralAerwhale(level, caster, heading, getLifetime());
            whale.setPos(start);
            whale.configure(getSpellPower(spellLevel, caster), getSongRadius(spellLevel), SPEED);
            level.addFreshEntity(whale);

            ASFx.burst(level, ASParticles.CLOUD_PUFF.get(), start, 30, 1.8, 0.04);
            ASFx.burst(level, ASParticles.SKY_SPARKLE.get(), start, 30, 2.0, 0.05);
            ASFx.ring(level, ASParticles.SKY_SPARKLE.get(), caster.position().add(0, 0.2, 0), 1.2, 20, 0.12);
        }
        super.onCast(level, spellLevel, caster, castSource, playerMagicData);
    }
}
