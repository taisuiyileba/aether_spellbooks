package com.aetherspellbooks.registry;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.block.TemporaryAercloudBlock;
import com.aetherspellbooks.block.TemporaryBlueAercloudBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ASBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, AetherSpellbooks.MODID);

    public static final DeferredHolder<Block, Block> TEMPORARY_COLD_AERCLOUD = BLOCKS.register("temporary_cold_aercloud",
            () -> new TemporaryAercloudBlock(cloudProperties(MapColor.SNOW)));
    public static final DeferredHolder<Block, Block> TEMPORARY_BLUE_AERCLOUD = BLOCKS.register("temporary_blue_aercloud",
            () -> new TemporaryBlueAercloudBlock(cloudProperties(MapColor.COLOR_LIGHT_BLUE)));
    public static final DeferredHolder<Block, Block> TEMPORARY_GOLDEN_AERCLOUD = BLOCKS.register("temporary_golden_aercloud",
            () -> new TemporaryAercloudBlock(cloudProperties(MapColor.COLOR_YELLOW)));

    // Mirrors the Aether's aercloud properties, but without drops and removable by pistons.
    private static BlockBehaviour.Properties cloudProperties(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(0.2f)
                .sound(SoundType.WOOL)
                .noOcclusion()
                .dynamicShape()
                .noLootTable()
                .pushReaction(PushReaction.DESTROY)
                .isRedstoneConductor(ASBlocks::never)
                .isSuffocating(ASBlocks::never)
                .isViewBlocking(ASBlocks::never);
    }

    private static boolean never(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
