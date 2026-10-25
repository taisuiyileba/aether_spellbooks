package com.aetherspellbooks.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Every block of a boss room that the boss's death would change (locked stone, boss and treasure doorways), captured
 * while the room is still intact so a revived boss gets the same sealed room back.
 */
public final class RoomSnapshot {
    private static final int AXIS_BITS = 10;
    private static final int AXIS_MASK = (1 << AXIS_BITS) - 1;

    public record Entry(BlockPos pos, BlockState state) {
    }

    private final BlockPos corner;
    private final List<BlockState> palette;
    private final int[] positions;
    private final int[] states;

    private RoomSnapshot(BlockPos corner, List<BlockState> palette, int[] positions, int[] states) {
        this.corner = corner;
        this.palette = palette;
        this.positions = positions;
        this.states = states;
    }

    /**
     * The blocks the Aether's {@code BossRoomTracker.modifyRoom} would visit: from the truncated minimum to the
     * truncated maximum of the room bounds, both inclusive.
     */
    public static BoundingBox roomBox(AABB room) {
        return new BoundingBox((int) room.minX, (int) room.minY, (int) room.minZ, (int) room.maxX, (int) room.maxY, (int) room.maxZ);
    }

    /** Null if the room is too large to pack (more than 1023 blocks along an axis). */
    public static RoomSnapshot capture(Level level, AABB room, Map<Block, Function<BlockState, BlockState>> conversions) {
        BoundingBox box = roomBox(room);
        if (box.getXSpan() > AXIS_MASK || box.getYSpan() > AXIS_MASK || box.getZSpan() > AXIS_MASK) {
            return null;
        }
        BlockPos corner = new BlockPos(box.minX(), box.minY(), box.minZ());
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> index = new HashMap<>();
        List<Integer> positions = new ArrayList<>();
        List<Integer> states = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockState state = level.getBlockState(pos);
            Function<BlockState, BlockState> conversion = conversions.get(state.getBlock());
            if (conversion == null || conversion.apply(state) == null) {
                continue;
            }
            positions.add(pack(pos.subtract(corner)));
            states.add(index.computeIfAbsent(state, s -> {
                palette.add(s);
                return palette.size() - 1;
            }));
        }
        return new RoomSnapshot(corner, palette, positions.stream().mapToInt(Integer::intValue).toArray(),
                states.stream().mapToInt(Integer::intValue).toArray());
    }

    public int size() {
        return positions.length;
    }

    public List<Entry> entries() {
        List<Entry> entries = new ArrayList<>(positions.length);
        for (int i = 0; i < positions.length; i++) {
            entries.add(new Entry(corner.offset(unpack(positions[i])), palette.get(states[i])));
        }
        return entries;
    }

    private static int pack(BlockPos relative) {
        return relative.getX() | relative.getY() << AXIS_BITS | relative.getZ() << AXIS_BITS * 2;
    }

    private static BlockPos unpack(int packed) {
        return new BlockPos(packed & AXIS_MASK, packed >> AXIS_BITS & AXIS_MASK, packed >> AXIS_BITS * 2 & AXIS_MASK);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Corner", NbtUtils.writeBlockPos(corner));
        ListTag paletteTag = new ListTag();
        palette.forEach(state -> paletteTag.add(NbtUtils.writeBlockState(state)));
        tag.put("Palette", paletteTag);
        tag.putIntArray("Positions", positions);
        tag.putIntArray("States", states);
        return tag;
    }

    public static RoomSnapshot load(CompoundTag tag) {
        List<BlockState> palette = new ArrayList<>();
        for (Tag stateTag : tag.getList("Palette", Tag.TAG_COMPOUND)) {
            palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), (CompoundTag) stateTag));
        }
        int[] positions = tag.getIntArray("Positions");
        int[] states = tag.getIntArray("States");
        if (positions.length != states.length) {
            positions = new int[0];
            states = new int[0];
        }
        return new RoomSnapshot(NbtUtils.readBlockPos(tag.getCompound("Corner")), palette, positions, states);
    }
}
