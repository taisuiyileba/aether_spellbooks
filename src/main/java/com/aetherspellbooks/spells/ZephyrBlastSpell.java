package com.aetherspellbooks.spells;

import com.aetherspellbooks.entity.ZephyrOrbProjectile;
import com.aetherspellbooks.registry.ASParticles;
import com.aetherspellbooks.util.ASFx;
import com.aetherteam.aether.client.AetherSoundEvents;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Spits a ball of compressed wind like a Zephyr. It bursts on impact and hurls everything nearby away.
 */
public class ZephyrBlastSpell extends AetherSpell {
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.UNCOMMON)
            .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
            .setMaxLevel(8)
            .setCooldownSeconds(6)
            .build();

    public ZephyrBlastSpell() {
        super("zephyr_blast");
        this.baseManaCost = 20;
        this.manaCostPerLevel = 4;
        this.baseSpellPower = 3;
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
        return Optional.of(AetherSoundEvents.ENTITY_ZEPHYR_SHOOT.get());
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)),
                Component.translatable("ui.irons_spellbooks.radius", Utils.stringTruncation(getRadius(spellLevel), 1)),
                Component.translatable("ui.aether_spellbooks.knockback", Utils.stringTruncation(getKnockback(spellLevel), 1))
        );
    }

    public static float getRadius(int spellLevel) {
        return 2.5f + 0.25f * spellLevel;
    }

    public static float getKnockback(int spellLevel) {
        return 1.2f + 0.15f * spellLevel;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData playerMagicData) {
        ZephyrOrbProjectile orb = new ZephyrOrbProjectile(level, caster);
        Vec3 look = caster.getLookAngle();
        orb.setPos(caster.position().add(0, caster.getEyeHeight() - 0.3, 0).add(look.scale(0.6)));
        orb.shoot(look);
        orb.setDamage(getSpellPower(spellLevel, caster));
        orb.configure(getRadius(spellLevel), getKnockback(spellLevel));
        level.addFreshEntity(orb);
        ASFx.burst(level, ASParticles.CLOUD_PUFF.get(), caster.getEyePosition().add(look.scale(0.8)), 6, 0.15, 0.08);
        super.onCast(level, spellLevel, caster, castSource, playerMagicData);
    }
}
