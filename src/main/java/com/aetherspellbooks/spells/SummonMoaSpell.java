package com.aetherspellbooks.spells;

import com.aetherspellbooks.entity.SummonedMoa;
import com.aetherteam.aether.data.resources.registries.AetherMoaTypes;
import com.aetherteam.aether.api.registers.MoaType;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.SummonManager;
import io.redspace.ironsspellbooks.capabilities.magic.SummonedEntitiesCastData;
import com.aetherspellbooks.registry.ASParticles;
import com.aetherspellbooks.util.ASFx;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Summons a saddled spectral Moa and mounts the caster on it.
 * Higher levels summon Moa types with more mid-air jumps (blue 3, white 4, black 8).
 */
public class SummonMoaSpell extends AetherSummonSpell {
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
            .setMaxLevel(3)
            .setCooldownSeconds(90)
            .build();

    public SummonMoaSpell() {
        super("summon_moa");
        this.baseManaCost = 60;
        this.manaCostPerLevel = 20;
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 0;
        this.castTime = 20;
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
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.EVOKER_PREPARE_SUMMON);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(com.aetherteam.aether.client.AetherSoundEvents.ENTITY_MOA_AMBIENT.get());
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aether_spellbooks.moa_jumps", spellLevel == 1 ? 3 : spellLevel == 2 ? 4 : 8),
                Component.translatable("ui.irons_spellbooks.duration", Utils.timeFromTicks(getSummonDurationTicks(spellLevel, caster), 1))
        );
    }

    public static net.minecraft.resources.ResourceKey<MoaType> getMoaType(int spellLevel) {
        return switch (spellLevel) {
            case 1 -> AetherMoaTypes.BLUE;
            case 2 -> AetherMoaTypes.WHITE;
            default -> AetherMoaTypes.BLACK;
        };
    }

    @Override
    public int getSummonDurationTicks(int spellLevel, @Nullable LivingEntity caster) {
        return (90 + 30 * spellLevel) * 20;
    }

    @Override
    protected void summon(ServerLevel level, int spellLevel, LivingEntity caster, int durationTicks, SummonedEntitiesCastData castData) {
        SummonedMoa moa = new SummonedMoa(level);
        moa.moveTo(caster.getX(), caster.getY(), caster.getZ(), caster.getYRot(), 0);
        moa.setMoaTypeByKey(getMoaType(spellLevel));
        moa.setPlayerGrown(true);
        moa.setSaddled(true);
        level.addFreshEntity(moa);
        SummonManager.initSummon(caster, moa, durationTicks, castData);
        ASFx.ring(level, ASParticles.CLOUD_PUFF.get(), moa.position().add(0, 0.2, 0), 0.6, 24, 0.3);
        ASFx.burst(level, ASParticles.FEATHER.get(), moa.position().add(0, 1.4, 0), 24, 0.6, 0.08);
        ASFx.burst(level, ASParticles.SKY_SPARKLE.get(), moa.position().add(0, 1.0, 0), 12, 0.7, 0.03);
        ASFx.blastwave(level, ASFx.CLOUD_WHITE, moa.position(), 2f);
        if (caster instanceof Player player && !player.isPassenger()) {
            player.startRiding(moa);
        }
    }
}
