package com.aetherspellbooks.block;

import com.aetherspellbooks.registry.ASBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class TemporaryClouds {
    private TemporaryClouds() {
    }

    public static boolean isTemporaryCloud(BlockState state) {
        return state.is(ASBlocks.TEMPORARY_COLD_AERCLOUD.get())
                || state.is(ASBlocks.TEMPORARY_BLUE_AERCLOUD.get())
                || state.is(ASBlocks.TEMPORARY_GOLDEN_AERCLOUD.get());
    }

    /**
     * Places a temporary cloud if the position is free (air or an existing temporary cloud),
     * and schedules its removal.
     *
     * @return whether a cloud was placed
     */
    public static boolean place(Level level, BlockPos pos, Block cloud, int durationTicks) {
        BlockState existing = level.getBlockState(pos);
        if (!existing.isAir() && !isTemporaryCloud(existing)) {
            return false;
        }
        level.setBlock(pos, cloud.defaultBlockState(), Block.UPDATE_ALL);
        level.scheduleTick(pos, cloud, durationTicks);
        return true;
    }

    static void dissipate(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        TemporaryAercloudBlock.poof(level, pos);
        if (level.random.nextInt(4) == 0) {
            level.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 0.4f, 1.3f);
        }
    }
}
