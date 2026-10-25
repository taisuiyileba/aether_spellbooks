package com.aetherspellbooks.dungeon;

import com.aetherteam.aether.block.dungeon.DoorwayBlock;
import com.aetherteam.aether.block.dungeon.TreasureDoorwayBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** Puts a boss room back the way it was before its boss died, without overwriting anything players built since. */
public final class RoomRestorer {
    private RoomRestorer() {
    }

    public static boolean isTreasureDoorway(BlockState state) {
        return state.getBlock() instanceof TreasureDoorwayBlock;
    }

    /** The locked walls and boss doorways, outermost first so the room visibly closes in on the soul. */
    public static List<RoomSnapshot.Entry> wallsOutsideIn(GuardianLair lair) {
        Vec3 center = lair.origin();
        return lair.snapshot().entries().stream()
                .filter(entry -> !isTreasureDoorway(entry.state()))
                .sorted(Comparator.comparingDouble((RoomSnapshot.Entry entry) -> entry.pos().distToCenterSqr(center)).reversed())
                .toList();
    }

    public static List<RoomSnapshot.Entry> treasureDoorways(GuardianLair lair) {
        return lair.snapshot().entries().stream().filter(entry -> isTreasureDoorway(entry.state())).toList();
    }

    /**
     * Restores one block if the spot still holds what the boss's death left there (or nothing at all). Boss doorways
     * come back open, as they are before a fight. Spots a creature now stands in are skipped.
     *
     * @return whether the block was placed
     */
    public static boolean restore(ServerLevel level, GuardianKind kind, RoomSnapshot.Entry entry) {
        BlockState current = level.getBlockState(entry.pos());
        BlockState original = entry.state();
        if (current == original) {
            return false;
        }
        Function<BlockState, BlockState> conversion = kind.conversions().get(original.getBlock());
        BlockState unlocked = conversion == null ? null : conversion.apply(original);
        boolean leftByDeath = current.isAir() || current.canBeReplaced() || (unlocked != null && current.is(unlocked.getBlock()));
        if (!leftByDeath || (current.hasBlockEntity() && !current.canBeReplaced())) {
            return false;
        }
        BlockState restored = original.getBlock() instanceof DoorwayBlock ? original.setValue(DoorwayBlock.INVISIBLE, true) : original;
        if (restored.isCollisionShapeFullBlock(level, entry.pos())
                && !level.getEntitiesOfClass(LivingEntity.class, new AABB(entry.pos()), entity -> entity.isAlive() && !entity.isSpectator()).isEmpty()) {
            return false;
        }
        return level.setBlock(entry.pos(), restored, Block.UPDATE_CLIENTS);
    }
}
