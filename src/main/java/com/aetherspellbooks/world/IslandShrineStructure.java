package com.aetherspellbooks.world;

import com.aetherspellbooks.registry.ASStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

import java.util.Optional;

/**
 * A single-template shrine placed on the surface of an Aether island.
 * <p>
 * Vanilla jigsaw structures project their start onto the heightmap, which over the void is the bottom of the world, so
 * they would hang in the sky beneath the islands. This type samples the ground under the centre and the corners of its
 * footprint first (at a few spots in the start chunk, keeping the flattest) and only generates where all of them are
 * solid, a few blocks thick (not an island's thin rim) and within {@code max_slope} blocks of each other. The template is then placed with the ordinary jigsaw machinery (so
 * pools, processors, terrain beards and the entities saved in the template all work as usual).
 */
public class IslandShrineStructure extends Structure {
    public static final Codec<IslandShrineStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            settingsCodec(instance),
            StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
            Codec.intRange(1, 32).fieldOf("footprint_radius").forGetter(s -> s.footprintRadius),
            Codec.intRange(0, 16).fieldOf("max_slope").forGetter(s -> s.maxSlope),
            Codec.intRange(1, 16).optionalFieldOf("min_ground_depth", 3).forGetter(s -> s.minGroundDepth),
            Codec.intRange(-8, 8).optionalFieldOf("sink", 0).forGetter(s -> s.sink)
    ).apply(instance, IslandShrineStructure::new));

    private final Holder<StructureTemplatePool> startPool;
    private final int footprintRadius;
    private final int maxSlope;
    private final int minGroundDepth;
    private final int sink;

    public IslandShrineStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int footprintRadius, int maxSlope,
                                 int minGroundDepth, int sink) {
        super(settings);
        this.startPool = startPool;
        this.footprintRadius = footprintRadius;
        this.maxSlope = maxSlope;
        this.minGroundDepth = minGroundDepth;
        this.sink = sink;
    }

    /** Name of the jigsaw block at the centre of the bottom layer of every shrine template. */
    public static final ResourceLocation CENTER = new ResourceLocation("aether_spellbooks", "center");

    /** Spots tried inside the start chunk (offsets from its middle); the flattest one that fits wins. */
    private static final int[][] CANDIDATES = {{0, 0}, {-4, -4}, {4, -4}, {-4, 4}, {4, 4}};

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        BlockPos best = null;
        int bestSlope = Integer.MAX_VALUE;
        for (int[] offset : CANDIDATES) {
            int x = chunk.getMiddleBlockX() + offset[0];
            int z = chunk.getMiddleBlockZ() + offset[1];
            int[] range = footprintHeights(context, x, z);
            if (range != null && range[1] - range[0] <= maxSlope && range[1] - range[0] < bestSlope) {
                bestSlope = range[1] - range[0];
                // stand on the lower ground; the terrain beard fills in under the rest. Jigsaw pieces put their layer 1 at
                // the given height (their ground level delta), so pass the air above the ground: layer 0 then replaces the top block
                best = new BlockPos(x, range[0] + 1 - sink, z);
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        // the template's centre jigsaw lands on the spot we checked, whichever way the template is rotated
        return JigsawPlacement.addPieces(context, startPool, Optional.of(CENTER), 1, best, false, Optional.empty(), 64);
    }

    /** Lowest and highest ground under the footprint (centre, corners and edge middles), or null if any is void or a thin crust. */
    private int[] footprintHeights(GenerationContext context, int x, int z) {
        int r = footprintRadius;
        int[][] samples = {{x, z}, {x - r, z - r}, {x + r, z - r}, {x - r, z + r}, {x + r, z + r}, {x - r, z}, {x + r, z}, {x, z - r}, {x, z + r}};
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int[] s : samples) {
            int y = groundHeight(context, s[0], s[1]);
            if (y == Integer.MIN_VALUE) {
                return null;
            }
            min = Math.min(min, y);
            max = Math.max(max, y);
            if (max - min > maxSlope) {
                return null;
            }
        }
        return new int[]{min, max};
    }

    /** Surface height at a column, or MIN_VALUE if it is void or only a thin crust. */
    private int groundHeight(GenerationContext context, int x, int z) {
        int bottom = context.heightAccessor().getMinBuildHeight();
        int y = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
        if (y <= bottom + 2) {
            return Integer.MIN_VALUE;
        }
        NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z, context.heightAccessor(), context.randomState());
        for (int d = 1; d <= minGroundDepth; d++) {
            if (column.getBlock(y - d).isAir() || !column.getBlock(y - d).getFluidState().isEmpty()) {
                return Integer.MIN_VALUE;
            }
        }
        return y;
    }

    @Override
    public StructureType<?> type() {
        return ASStructures.ISLAND_SHRINE.get();
    }
}
