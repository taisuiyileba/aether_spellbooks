package com.aetherspellbooks.spells;

import com.aetherspellbooks.registry.ASParticles;
import com.aetherspellbooks.util.ASFx;
import com.aetherspellbooks.util.CloudSentinels;
import com.aetherteam.aether.entity.miscellaneous.CloudMinion;
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
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Calls two of the Aether's cloud minions to the caster's shoulders. They fire cloud crystals at
 * whatever the caster aims at, and loose an extra volley whenever the caster casts a spell.
 * Player casters only (cloud minions follow players).
 */
public class CloudSentinelsSpell extends AetherSpell {
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.UNCOMMON)
            .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(40)
            .build();

    public CloudSentinelsSpell() {
        super("cloud_sentinels");
        this.baseManaCost = 40;
        this.manaCostPerLevel = 10;
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 0;
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
        return Optional.of(SoundEvents.AMETHYST_BLOCK_RESONATE);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aether_spellbooks.shots_per_second", Utils.stringTruncation(20f / getFireInterval(spellLevel), 1)),
                Component.translatable("ui.irons_spellbooks.duration", Utils.timeFromTicks(getDurationTicks(spellLevel), 1))
        );
    }

    public static int getDurationTicks(int spellLevel) {
        return (20 + 10 * spellLevel) * 20;
    }

    public static int getFireInterval(int spellLevel) {
        return 40 - 4 * spellLevel;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData playerMagicData) {
        if (!level.isClientSide && caster instanceof Player player) {
            CloudSentinels.dismiss(player);
            List<CloudMinion> minions = new ArrayList<>();
            for (HumanoidArm side : HumanoidArm.values()) {
                CloudMinion minion = new CloudMinion(level, player, side);
                minion.setLifeSpan(getDurationTicks(spellLevel));
                level.addFreshEntity(minion);
                minions.add(minion);
                ASFx.burst(level, ASParticles.CLOUD_PUFF.get(), minion.position(), 10, 0.3, 0.03);
                ASFx.burst(level, ASParticles.SKY_SPARKLE.get(), minion.position(), 6, 0.3, 0.02);
            }
            CloudSentinels.track(player, minions, getFireInterval(spellLevel));
        }
        super.onCast(level, spellLevel, caster, castSource, playerMagicData);
    }
}
