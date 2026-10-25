package com.aetherspellbooks.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** The boss lairs of one dimension, keyed by the boss's spawn block. Saved with the level, not with any entity. */
public class GuardianLairs extends SavedData {
    private static final String NAME = "aether_spellbooks_guardian_lairs";

    private final Map<BlockPos, GuardianLair> lairs = new LinkedHashMap<>();

    public static GuardianLairs get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(GuardianLairs::new, GuardianLairs::load), NAME);
    }

    public Optional<GuardianLair> get(BlockPos key) {
        return Optional.ofNullable(lairs.get(key));
    }

    public void put(GuardianLair lair) {
        lairs.put(lair.key(), lair);
        setDirty();
    }

    /** The lair whose room contains {@code pos}; the closest one if rooms ever overlapped. */
    public Optional<GuardianLair> containing(Vec3 pos) {
        return lairs.values().stream()
                .filter(lair -> lair.room().contains(pos))
                .min(Comparator.comparingDouble(lair -> lair.origin().distanceToSqr(pos)));
    }

    /** Drops every lair whose boss spawned inside {@code area} (GameTests reuse the same structure positions). */
    public void forgetWithin(AABB area) {
        if (lairs.values().removeIf(lair -> area.contains(lair.origin()))) {
            setDirty();
        }
    }

    public void markDefeated(GuardianLair lair, @Nullable Component bossName, @Nullable BlockPos vault) {
        lair.markDefeated(bossName, vault);
        setDirty();
    }

    public void markRevived(GuardianLair lair) {
        lair.markRevived();
        setDirty();
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        lairs.values().forEach(lair -> list.add(lair.save(registries)));
        tag.put("Lairs", list);
        return tag;
    }

    private static GuardianLairs load(CompoundTag tag, HolderLookup.Provider registries) {
        GuardianLairs data = new GuardianLairs();
        for (Tag lairTag : tag.getList("Lairs", Tag.TAG_COMPOUND)) {
            GuardianLair lair = GuardianLair.load((CompoundTag) lairTag, registries);
            data.lairs.put(lair.key(), lair);
        }
        return data;
    }
}
