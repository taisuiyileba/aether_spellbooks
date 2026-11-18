package com.aetherspellbooks.spells;

import com.aetherspellbooks.entity.AechorFlower;
import com.aetherspellbooks.registry.ASEntities;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

public class AechorFlowerSpell extends AetherSpell {
    private final DefaultConfig config = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE).setSchoolResource(SchoolRegistry.NATURE_RESOURCE)
            .setMaxLevel(3).setCooldownSeconds(35).build();

    public AechorFlowerSpell() {
        super("aechor_flower");
        baseManaCost = 55;
        manaCostPerLevel = 10;
        baseSpellPower = 2;
        spellPowerPerLevel = 1;
        castTime = 20;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return config;
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
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.FLOWERING_AZALEA_PLACE);
    }

    private static int duration(int spellLevel) {
        return (6 + 2 * spellLevel) * 20;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)),
                Component.translatable("ui.irons_spellbooks.distance", AechorFlower.RANGE),
                Component.translatable("ui.irons_spellbooks.duration", Utils.timeFromTicks(duration(spellLevel), 1)));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource source, MagicData magicData) {
        if (level instanceof ServerLevel server) {
            server.getEntities(ASEntities.AECHOR_FLOWER.get(), flower -> flower.getOwner() == caster).forEach(AechorFlower::discard);
            AechorFlower flower = new AechorFlower(level, caster);
            flower.setDamage(getSpellPower(spellLevel, caster));
            flower.setLifetime(duration(spellLevel));
            server.addFreshEntity(flower);
        }
        super.onCast(level, spellLevel, caster, source, magicData);
    }
}
