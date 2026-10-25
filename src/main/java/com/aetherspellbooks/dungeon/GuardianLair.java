package com.aetherspellbooks.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One dungeon boss room, remembered from the first time a player walked into it: the boss's spawn point, the room the
 * Aether tracks for it and the blocks its death will unlock. While the boss is dead the lair is dormant and its soul can
 * be offered the boss's relic.
 */
public final class GuardianLair {
    private final GuardianKind kind;
    private final Vec3 origin;
    private final AABB room;
    private final RoomSnapshot snapshot;
    /** The Valkyrie Queen tears down her whole temple rather than only the boss room; kept so a revived Queen does too. */
    @Nullable
    private final AABB queenBounds;
    @Nullable
    private Component bossName;
    @Nullable
    private BlockPos vault;
    private boolean dormant;
    private int revivals;

    public GuardianLair(GuardianKind kind, Vec3 origin, AABB room, RoomSnapshot snapshot, @Nullable AABB queenBounds) {
        this.kind = kind;
        this.origin = origin;
        this.room = room;
        this.snapshot = snapshot;
        this.queenBounds = queenBounds;
    }

    public static BlockPos keyOf(Vec3 origin) {
        return BlockPos.containing(origin);
    }

    public BlockPos key() {
        return keyOf(origin);
    }

    public GuardianKind kind() {
        return kind;
    }

    public Vec3 origin() {
        return origin;
    }

    public AABB room() {
        return room;
    }

    public RoomSnapshot snapshot() {
        return snapshot;
    }

    @Nullable
    public AABB queenBounds() {
        return queenBounds;
    }

    @Nullable
    public Component bossName() {
        return bossName;
    }

    @Nullable
    public BlockPos vault() {
        return vault;
    }

    public boolean dormant() {
        return dormant;
    }

    public int revivals() {
        return revivals;
    }

    /** Where the soul (0.8 blocks tall) waits: floating at the height of the boss's middle, above its spawn point. */
    public Vec3 soulHome() {
        return origin.add(0, 0.9, 0);
    }

    void markDefeated(@Nullable Component bossName, @Nullable BlockPos vault) {
        this.bossName = bossName;
        if (vault != null) {
            this.vault = vault;
        }
        this.dormant = true;
    }

    void markRevived() {
        this.dormant = false;
        this.revivals++;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Kind", kind.id());
        tag.putDouble("OriginX", origin.x);
        tag.putDouble("OriginY", origin.y);
        tag.putDouble("OriginZ", origin.z);
        tag.put("Room", writeBox(room));
        tag.put("Snapshot", snapshot.save());
        if (queenBounds != null) {
            tag.put("QueenBounds", writeBox(queenBounds));
        }
        if (bossName != null) {
            tag.putString("BossName", Component.Serializer.toJson(bossName));
        }
        if (vault != null) {
            tag.put("Vault", NbtUtils.writeBlockPos(vault));
        }
        tag.putBoolean("Dormant", dormant);
        tag.putInt("Revivals", revivals);
        return tag;
    }

    static GuardianLair load(CompoundTag tag) {
        GuardianLair lair = new GuardianLair(GuardianKind.byId(tag.getString("Kind")),
                new Vec3(tag.getDouble("OriginX"), tag.getDouble("OriginY"), tag.getDouble("OriginZ")),
                readBox(tag.getCompound("Room")), RoomSnapshot.load(tag.getCompound("Snapshot")),
                tag.contains("QueenBounds") ? readBox(tag.getCompound("QueenBounds")) : null);
        if (tag.contains("BossName")) {
            lair.bossName = Component.Serializer.fromJson(tag.getString("BossName"));
        }
        if (tag.contains("Vault")) {
            lair.vault = NbtUtils.readBlockPos(tag.getCompound("Vault"));
        }
        lair.dormant = tag.getBoolean("Dormant");
        lair.revivals = tag.getInt("Revivals");
        return lair;
    }

    static CompoundTag writeBox(AABB box) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("MinX", box.minX);
        tag.putDouble("MinY", box.minY);
        tag.putDouble("MinZ", box.minZ);
        tag.putDouble("MaxX", box.maxX);
        tag.putDouble("MaxY", box.maxY);
        tag.putDouble("MaxZ", box.maxZ);
        return tag;
    }

    static AABB readBox(CompoundTag tag) {
        return new AABB(tag.getDouble("MinX"), tag.getDouble("MinY"), tag.getDouble("MinZ"),
                tag.getDouble("MaxX"), tag.getDouble("MaxY"), tag.getDouble("MaxZ"));
    }
}
