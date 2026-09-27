package com.aetherspellbooks.spells;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ICastDataSerializable;
import io.redspace.ironsspellbooks.capabilities.magic.RecastInstance;
import io.redspace.ironsspellbooks.capabilities.magic.RecastResult;
import io.redspace.ironsspellbooks.capabilities.magic.SummonManager;
import io.redspace.ironsspellbooks.capabilities.magic.SummonedEntitiesCastData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Summon spells follow Iron's Spells' recast pattern: casting again dismisses the summons.
 */
public abstract class AetherSummonSpell extends AetherSpell {
    protected AetherSummonSpell(String id) {
        super(id);
    }

    /**
     * Spawns the summons. Implementations must call {@link SummonManager#initSummon} for each entity.
     */
    protected abstract void summon(ServerLevel level, int spellLevel, LivingEntity caster, int durationTicks, SummonedEntitiesCastData castData);

    public abstract int getSummonDurationTicks(int spellLevel, @Nullable LivingEntity caster);

    @Override
    public int getRecastCount(int spellLevel, @Nullable LivingEntity entity) {
        return 2;
    }

    @Override
    public ICastDataSerializable getEmptyCastData() {
        return new SummonedEntitiesCastData();
    }

    @Override
    public void onRecastFinished(ServerPlayer serverPlayer, RecastInstance recastInstance, RecastResult recastResult, ICastDataSerializable castDataSerializable) {
        if (SummonManager.recastFinishedHelper(serverPlayer, recastInstance, recastResult, castDataSerializable)) {
            super.onRecastFinished(serverPlayer, recastInstance, recastResult, castDataSerializable);
        }
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            var recasts = playerMagicData.getPlayerRecasts();
            if (!recasts.hasRecastForSpell(this)) {
                SummonedEntitiesCastData castData = new SummonedEntitiesCastData();
                int duration = getSummonDurationTicks(spellLevel, caster);
                summon(serverLevel, spellLevel, caster, duration, castData);
                RecastInstance recastInstance = new RecastInstance(getSpellId(), spellLevel, getRecastCount(spellLevel, caster), duration, castSource, castData);
                recasts.addRecast(recastInstance, playerMagicData);
            }
        }
        super.onCast(level, spellLevel, caster, castSource, playerMagicData);
    }
}
