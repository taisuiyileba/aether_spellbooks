package com.aetherspellbooks.spells;

import com.aetherspellbooks.entity.ThornTrail;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class ThornSpikesSpell extends AetherSpell {
    private final DefaultConfig config = new DefaultConfig()
            .setMinRarity(SpellRarity.UNCOMMON).setSchoolResource(SchoolRegistry.NATURE_RESOURCE)
            .setMaxLevel(5).setCooldownSeconds(10).build();

    public ThornSpikesSpell() {
        super("thorn_spikes");
        baseManaCost = 30;
        manaCostPerLevel = 5;
        baseSpellPower = 7;
        spellPowerPerLevel = 2;
        castTime = 8;
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
        return SpellAnimations.TOUCH_GROUND_ANIMATION;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.AZALEA_BREAK);
    }

    private static int range(int spellLevel) {
        return 6 + spellLevel;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)),
                Component.translatable("ui.irons_spellbooks.distance", range(spellLevel)));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity caster, MagicData magicData) {
        Vec3 feet = caster.position();
        if (clip(level, caster, feet.add(0, 0.1, 0), feet.add(0, -1.05, 0)).getType() == HitResult.Type.MISS) {
            if (caster instanceof Player player) {
                player.displayClientMessage(Component.translatable("ui.aether_spellbooks.thorn_spikes.needs_ground"), true);
            }
            return false;
        }
        return super.checkPreCastConditions(level, spellLevel, caster, magicData);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource source, MagicData magicData) {
        if (level instanceof ServerLevel server) {
            Set<UUID> hit = new HashSet<>();
            float damage = getSpellPower(spellLevel, caster);
            for (int angle = -18; angle <= 18; angle += 18) {
                ThornTrail.grow(server, caster, caster.getYRot() + angle, range(spellLevel), damage, hit);
            }
        }
        super.onCast(level, spellLevel, caster, source, magicData);
    }

    private static BlockHitResult clip(Level level, LivingEntity caster, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
    }
}
