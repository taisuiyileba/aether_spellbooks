package com.aetherspellbooks.block;

import com.aetherteam.aether.block.natural.BlueAercloudBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A bouncy blue aercloud that removes itself after a scheduled tick. Has no loot table and no item.
 */
public class TemporaryBlueAercloudBlock extends BlueAercloudBlock {
    public TemporaryBlueAercloudBlock(Properties properties) {
        super(properties);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        TemporaryClouds.dissipate(level, pos);
    }
}
