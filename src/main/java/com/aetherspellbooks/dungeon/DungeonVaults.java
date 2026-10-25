package com.aetherspellbooks.dungeon;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.config.ASConfig;
import com.aetherspellbooks.mixin.BlockEntityTypeAccessor;
import com.aetherspellbooks.mixin.VaultRewardsAccessor;
import com.aetherspellbooks.registry.ASBlocks;
import com.aetherteam.aether.block.dungeon.TreasureChestBlock;
import com.aetherteam.aether.blockentity.TreasureChestBlockEntity;
import io.redspace.ironsspellbooks.api.backwards_compat.blocks.vault.VaultBlock;
import io.redspace.ironsspellbooks.api.backwards_compat.blocks.vault.data.VaultBlockEntity;
import io.redspace.ironsspellbooks.api.backwards_compat.blocks.vault.data.VaultServerData;
import io.redspace.ironsspellbooks.api.backwards_compat.blocks.vault.data.VaultSharedData;
import io.redspace.ironsspellbooks.api.backwards_compat.blocks.vault.data.VaultState;
import io.redspace.ironsspellbooks.registries.BlockRegistry;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Turning dungeon Treasure Chests into personal reward vaults and resetting them for rematches. */
public final class DungeonVaults {
    public static final int EJECT_SHUTTER_TICKS = 10;

    private DungeonVaults() {
    }

    /**
     * Iron's Spells' vault block entity type only ticks and syncs the blocks it was built with; our reskins share it, so
     * they are added to its valid blocks once registries are done.
     */
    public static void bindToVaultBlockEntity() {
        BlockEntityTypeAccessor type = (BlockEntityTypeAccessor) BlockRegistry.VAULT_BLOCK_ENTITY.get();
        Set<Block> blocks = new HashSet<>(type.aetherSpellbooks$getValidBlocks());
        ASBlocks.VAULTS.forEach(vault -> blocks.add(vault.get()));
        type.aetherSpellbooks$setValidBlocks(Set.copyOf(blocks));
    }

    /**
     * Replaces every still-locked Treasure Chest in the room whose loot has not been rolled yet with the vault of its
     * dungeon, keeping the chest's loot table and facing. Opened chests, hand-filled chests and chests of dungeon kinds
     * we do not know stay as they are.
     *
     * @return every one of our vaults in the room afterwards, converted now or earlier
     */
    public static List<BlockPos> convertTreasureChests(ServerLevel level, AABB room) {
        List<BlockPos> vaults = new ArrayList<>();
        for (BlockEntity blockEntity : blockEntitiesIn(level, room)) {
            BlockPos pos = blockEntity.getBlockPos();
            if (blockEntity instanceof TreasureChestBlockEntity chest && convert(level, chest)) {
                vaults.add(pos);
            } else if (isDungeonVault(level, pos)) {
                vaults.add(pos);
            }
        }
        return vaults;
    }

    public static List<BlockPos> findVaults(ServerLevel level, AABB room) {
        return blockEntitiesIn(level, room).stream().map(BlockEntity::getBlockPos).filter(pos -> isDungeonVault(level, pos)).toList();
    }

    private static boolean convert(ServerLevel level, TreasureChestBlockEntity chest) {
        if (!chest.getLocked()) {
            return false;
        }
        VaultTier tier = VaultTier.byKind(chest.getKind()).orElse(null);
        CompoundTag saved = chest.saveWithoutMetadata();
        ResourceLocation lootTable = ResourceLocation.tryParse(saved.getString("LootTable"));
        if (tier == null || !saved.contains("LootTable", Tag.TAG_STRING) || lootTable == null
                || !saved.getList("Items", Tag.TAG_COMPOUND).isEmpty()) {
            return false;
        }
        BlockPos pos = chest.getBlockPos();
        BlockState chestState = level.getBlockState(pos);
        // the chest would roll and spill its loot while being removed otherwise
        chest.setLootTable(null, 0L);
        BlockState vaultState = tier.block().defaultBlockState();
        if (chestState.hasProperty(TreasureChestBlock.FACING)) {
            vaultState = vaultState.setValue(VaultBlock.FACING, chestState.getValue(TreasureChestBlock.FACING));
        }
        level.setBlock(pos, vaultState, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof VaultBlockEntity vault) {
            vault.setConfig(tier.config(lootTable));
            vault.setChanged();
        }
        Vec3 center = Vec3.atCenterOf(pos);
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 24, 0.35, 0.35, 0.35, 0.04);
        level.playSound(null, pos, SoundRegistry.VAULT_ACTIVATE.get(), SoundSource.BLOCKS, 1f, 0.9f);
        AetherSpellbooks.LOGGER.debug("Treasure Chest at {} became a {} vault ({})", pos, tier, lootTable);
        return true;
    }

    /** Any Treasure Chest left in the room (opened or not); null if there is none. */
    @Nullable
    public static BlockPos treasureChestIn(ServerLevel level, AABB room) {
        return blockEntitiesIn(level, room).stream().filter(entity -> entity instanceof TreasureChestBlockEntity)
                .map(BlockEntity::getBlockPos).findFirst().orElse(null);
    }

    public static boolean isDungeonVault(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof DungeonVaultBlock && level.getBlockEntity(pos) instanceof VaultBlockEntity;
    }

    /** Players who have already taken their reward from the vault at {@code pos}; empty if there is no vault. */
    public static Set<UUID> rewardedPlayers(ServerLevel level, @Nullable BlockPos pos) {
        if (pos != null && level.getBlockEntity(pos) instanceof VaultBlockEntity vault) {
            VaultServerData data = vault.getServerData();
            if (data != null) {
                return Set.copyOf(((VaultRewardsAccessor) data).aetherSpellbooks$getRewardedPlayers());
            }
        }
        return Set.of();
    }

    /** Clears per-player reward history when a boss revival resets this vault. */
    public static void clearRewardedPlayers(VaultServerData data) {
        if (data != null) {
            ((VaultRewardsAccessor) data).aetherSpellbooks$getRewardedPlayers().clear();
        }
    }

    /** Whether an opened vault should burst out all its loot at once instead of spitting out one item per second. */
    public static boolean shouldEjectAll(BlockState state) {
        if (state.getBlock() instanceof DungeonVaultBlock) {
            return true;
        }
        return ASConfig.modifyIronsVaults() && state.getBlock() instanceof VaultBlock;
    }

    /**
     * Refreshes all vaults within a room or bounding box (e.g. after a boss revival ritual), clearing rewarded
     * players so they can be unlocked again.
     */
    public static void refreshVaultsInRoom(ServerLevel level, AABB room) {
        if (!ASConfig.resetVaultsOnRevival()) {
            return;
        }
        for (BlockEntity be : blockEntitiesIn(level, room)) {
            if (be instanceof VaultBlockEntity vault && mayRefresh(vault)) {
                clearRewardedPlayers(vault.getServerData());
                wakeVault(level, vault);
            }
        }
    }

    /**
     * Refreshes all vaults in a horizontal radius around an arena center (e.g. Catacombs throne room after Dead King revival).
     */
    public static void refreshVaultsAround(ServerLevel level, BlockPos center, int radius) {
        if (!ASConfig.resetVaultsOnRevival()) {
            return;
        }
        AABB box = new AABB(center).inflate(radius, 32, radius);
        for (BlockEntity be : blockEntitiesIn(level, box)) {
            if (be instanceof VaultBlockEntity vault && mayRefresh(vault)) {
                clearRewardedPlayers(vault.getServerData());
                wakeVault(level, vault);
            }
        }
    }

    private static boolean mayRefresh(VaultBlockEntity vault) {
        return vault.getBlockState().getBlock() instanceof DungeonVaultBlock || ASConfig.modifyIronsVaults();
    }

    private static void wakeVault(ServerLevel level, VaultBlockEntity vault) {
        VaultServerData serverData = vault.getServerData();
        if (serverData != null) {
            VaultRewardsAccessor accessor = (VaultRewardsAccessor) serverData;
            accessor.aetherSpellbooks$pauseStateUpdatingUntil(level.getGameTime());
        }
        vault.setChanged();
        BlockState state = vault.getBlockState();
        level.sendBlockUpdated(vault.getBlockPos(), state, state, Block.UPDATE_ALL);
    }

    /**
     * Bursts out every rolled reward stack at the moment the key unlocks the vault, instead of spitting out one stack
     * per second. The top shutter opens briefly ({@link VaultState#EJECTING}) and then closes again on its next tick.
     */
    public static void ejectAll(ServerLevel level, BlockPos pos, BlockState state, VaultServerData serverData,
                                VaultSharedData sharedData, List<ItemStack> items) {
        VaultRewardsAccessor accessor = (VaultRewardsAccessor) serverData;
        sharedData.setDisplayItem(items.isEmpty() ? ItemStack.EMPTY : items.get(items.size() - 1));
        accessor.aetherSpellbooks$setItemsToEject(List.of());
        accessor.aetherSpellbooks$pauseStateUpdatingUntil(level.getGameTime() + EJECT_SHUTTER_TICKS);
        level.setBlock(pos, state.setValue(VaultBlock.STATE, VaultState.EJECTING), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundRegistry.VAULT_INSERT_ITEM.get(), SoundSource.BLOCKS, 1f, 1f);
        level.playSound(null, pos, SoundRegistry.VAULT_OPEN_SHUTTER.get(), SoundSource.BLOCKS, 1f, 1f);
        level.playSound(null, pos, SoundRegistry.VAULT_EJECT_ITEM.get(), SoundSource.BLOCKS, 1f, 1.1f);
        level.levelEvent(3017, pos, 0);
        Vec3 ejectPos = Vec3.atBottomCenterOf(pos).relative(Direction.UP, 1.2);
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                DefaultDispenseItemBehavior.spawnItem(level, stack.copy(), 2, Direction.UP, ejectPos);
            }
        }
    }

    private static List<BlockEntity> blockEntitiesIn(ServerLevel level, AABB room) {
        BoundingBox box = RoomSnapshot.roomBox(room);
        List<BlockEntity> found = new ArrayList<>();
        for (int cx = SectionPos.blockToSectionCoord(box.minX()); cx <= SectionPos.blockToSectionCoord(box.maxX()); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(box.minZ()); cz <= SectionPos.blockToSectionCoord(box.maxZ()); cz++) {
                for (Map.Entry<BlockPos, BlockEntity> entry : level.getChunk(cx, cz).getBlockEntities().entrySet()) {
                    if (box.isInside(entry.getKey())) {
                        found.add(entry.getValue());
                    }
                }
            }
        }
        // the map is live; callers replace blocks while walking this list
        return found;
    }

    /** Merges equal stacks before burst ejection and chooses a rare reward for the display item. */
    public static List<ItemStack> mergeForEjection(List<ItemStack> items) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack stack : items) {
            ItemStack rest = stack.copy();
            for (ItemStack existing : merged) {
                if (rest.isEmpty()) {
                    break;
                }
                if (ItemStack.isSameItemSameTags(existing, rest) && existing.getCount() < existing.getMaxStackSize()) {
                    int moved = Math.min(rest.getCount(), existing.getMaxStackSize() - existing.getCount());
                    existing.grow(moved);
                    rest.shrink(moved);
                }
            }
            if (!rest.isEmpty()) {
                merged.add(rest);
            }
        }
        merged.sort(Comparator.comparingInt(stack -> stack.getRarity().ordinal()));
        return merged;
    }

}
