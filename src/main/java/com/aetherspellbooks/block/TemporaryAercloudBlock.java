package com.aetherspellbooks.block;

import com.aetherteam.aether.block.natural.AercloudBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A cold/golden aercloud that removes itself after a scheduled tick. Has no loot table and no item.
 */
public class TemporaryAercloudBlock extends AercloudBlock {
    public TemporaryAercloudBlock(Properties properties) {
        super(properties);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        TemporaryClouds.dissipate(level, pos);
    }

    static void poof(ServerLevel level, BlockPos pos) {
        level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.2, 0.3, 0.01);
    }
}
