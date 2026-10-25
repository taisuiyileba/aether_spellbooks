package com.aetherspellbooks.mixin;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

/** Lets the dungeon vaults join Iron's Spells' vault block entity type (see {@code DungeonVaults#bindToVaultBlockEntity}). */
@Mixin(BlockEntityType.class)
public interface BlockEntityTypeAccessor {
    @Accessor("validBlocks")
    Set<Block> aetherSpellbooks$getValidBlocks();

    @Mutable
    @Accessor("validBlocks")
    void aetherSpellbooks$setValidBlocks(Set<Block> blocks);
}
