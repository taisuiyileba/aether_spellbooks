package com.aetherspellbooks.dungeon;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.config.ASConfig;
import com.aetherspellbooks.entity.GuardianSoul;
import com.aetherteam.aether.entity.monster.dungeon.boss.Slider;
import com.aetherteam.aether.entity.monster.dungeon.boss.ValkyrieQueen;
import com.aetherteam.nitrogen.entity.BossMob;
import com.aetherteam.nitrogen.entity.BossRoomTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Dungeon bosses that belong to a dungeon (the Aether gave them a {@link BossRoomTracker}): who fought them, what their
 * death leaves behind (vault, keys, soul) and how a relic calls them back. A boss without a tracker - spawn egg,
 * /summon, another mod - is never touched.
 */
public final class Guardians {
    private static final String PARTICIPANTS = AetherSpellbooks.MODID + ":fight_participants";
    private static final String DEATH_HANDLED = AetherSpellbooks.MODID + ":death_handled";
    private static final String VAULT = AetherSpellbooks.MODID + ":vault";
    private static final String REVIVED = AetherSpellbooks.MODID + ":revived";

    private Guardians() {
    }

    @Nullable
    @SuppressWarnings("rawtypes")
    public static BossRoomTracker<?> tracker(Mob boss) {
        return boss instanceof BossMob bossMob ? bossMob.getDungeon() : null;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // During the fight

    /** A player walked into the boss room: remember the room (once) and the player as a participant. */
    public static void onPlayerEntered(ServerLevel level, Mob boss, ServerPlayer player) {
        GuardianKind kind = GuardianKind.of(boss).orElse(null);
        BossRoomTracker<?> tracker = tracker(boss);
        if (kind == null || tracker == null || player.isSpectator()) {
            return;
        }
        rememberLair(level, boss, kind, tracker);
        ListTag participants = boss.getPersistentData().getList(PARTICIPANTS, Tag.TAG_INT_ARRAY);
        if (participants.stream().noneMatch(tag -> NbtUtils.loadUUID(tag).equals(player.getUUID()))) {
            participants.add(NbtUtils.createUUID(player.getUUID()));
            boss.getPersistentData().put(PARTICIPANTS, participants);
        }
    }

    public static void onFightStarted(ServerLevel level, Mob boss) {
        GuardianKind kind = GuardianKind.of(boss).orElse(null);
        BossRoomTracker<?> tracker = tracker(boss);
        if (kind != null && tracker != null) {
            rememberLair(level, boss, kind, tracker);
        }
    }

    /** The fight was reset (everyone left or died): the next attempt has its own participants. */
    public static void onFightStopped(Mob boss) {
        boss.getPersistentData().remove(PARTICIPANTS);
    }

    /**
     * Captures the room while it is still sealed. The Aether only tears it down on death, and by the time the death event
     * fires the doors are already gone, so this happens as soon as a player is inside.
     */
    private static void rememberLair(ServerLevel level, Mob boss, GuardianKind kind, BossRoomTracker<?> tracker) {
        GuardianLairs lairs = GuardianLairs.get(level);
        if (lairs.get(GuardianLair.keyOf(tracker.originCoordinates())).isPresent()) {
            return;
        }
        AABB temple = queenBounds(boss);
        RoomSnapshot snapshot = RoomSnapshot.capture(level, temple != null ? temple : tracker.roomBounds(), kind.conversions());
        if (snapshot == null || snapshot.size() == 0) {
            return;
        }
        lairs.put(new GuardianLair(kind, tracker.originCoordinates(), tracker.roomBounds(), snapshot, temple));
        AetherSpellbooks.LOGGER.debug("Remembered the {} lair at {} ({} sealed blocks)", kind.id(), tracker.originCoordinates(), snapshot.size());
    }

    /** The Valkyrie Queen keeps the bounds of her whole temple only in her save data. */
    @Nullable
    private static AABB queenBounds(Mob boss) {
        if (!(boss instanceof ValkyrieQueen)) {
            return null;
        }
        CompoundTag tag = boss.saveWithoutId(new CompoundTag());
        return tag.contains("DungeonBoundsMinX") ? new AABB(tag.getDouble("DungeonBoundsMinX"), tag.getDouble("DungeonBoundsMinY"),
                tag.getDouble("DungeonBoundsMinZ"), tag.getDouble("DungeonBoundsMaxX"), tag.getDouble("DungeonBoundsMaxY"),
                tag.getDouble("DungeonBoundsMaxZ")) : null;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Death and drops

    /**
     * Runs after the Aether has unlocked the room (its {@code die} tears the room down before the death event): the
     * Treasure Chest becomes a vault and the boss leaves its soul.
     */
    public static void onDeath(ServerLevel level, Mob boss) {
        GuardianKind kind = GuardianKind.of(boss).orElse(null);
        BossRoomTracker<?> tracker = tracker(boss);
        if (kind == null || tracker == null || boss.getPersistentData().getBoolean(DEATH_HANDLED)) {
            return;
        }
        boss.getPersistentData().putBoolean(DEATH_HANDLED, true);
        List<BlockPos> vaults = ASConfig.treasureVaults()
                ? DungeonVaults.convertTreasureChests(level, tracker.roomBounds())
                : DungeonVaults.findVaults(level, tracker.roomBounds());
        if (!vaults.isEmpty()) {
            boss.getPersistentData().putLong(VAULT, vaults.get(0).asLong());
        }

        Optional<GuardianLair> lair = GuardianLairs.get(level).get(GuardianLair.keyOf(tracker.originCoordinates()));
        if (ASConfig.guardianRevival() && lair.isPresent() && lair.get().kind() == kind) {
            Component name = boss instanceof BossMob<?> bossMob ? bossMob.getBossName() : null;
            GuardianLairs.get(level).markDefeated(lair.get(), name, vaults.isEmpty() ? null : vaults.get(0));
            GuardianSoul soul = new GuardianSoul(level, kind, lair.get());
            soul.moveTo(boss.getBoundingBox().getCenter().subtract(0, soul.getBbHeight() * 0.5, 0));
            level.addFreshEntity(soul);
        }
    }

    /**
     * When the boss room holds a vault, replaces the single key drop with one inventory reward per participant.
     * The Aether's other boss drops stay intact. Pending rewards survive logout, death and full inventories.
     */
    public static void onDrops(ServerLevel level, Mob boss, DamageSource source, Collection<ItemEntity> drops) {
        GuardianKind kind = GuardianKind.of(boss).orElse(null);
        if (kind == null || !boss.getPersistentData().getBoolean(DEATH_HANDLED)
                || !ASConfig.keyPerParticipant() || !boss.getPersistentData().contains(VAULT)) {
            return;
        }
        Set<UUID> participants = participants(level, boss);
        if (participants.isEmpty()) {
            return;
        }
        drops.removeIf(item -> item.getItem().is(kind.tier().key()));
        DungeonKeyRewards rewards = DungeonKeyRewards.get(level);
        participants.forEach(id -> rewards.award(id, new ItemStack(kind.tier().key())));
        for (ServerLevel dimension : level.getServer().getAllLevels()) {
            for (ServerPlayer player : dimension.players()) {
                if (participants.contains(player.getUUID())) {
                    rewards.deliver(player);
                    if (rewards.hasPending(player.getUUID())) {
                        player.displayClientMessage(Component.translatable("message.aether_spellbooks.guardian.keys_pending"), true);
                    }
                }
            }
        }
    }

    private static Set<UUID> participants(ServerLevel level, Mob boss) {
        Set<UUID> ids = new LinkedHashSet<>();
        boss.getPersistentData().getList(PARTICIPANTS, Tag.TAG_INT_ARRAY).forEach(tag -> ids.add(NbtUtils.loadUUID(tag)));
        BossRoomTracker<?> tracker = tracker(boss);
        for (ServerLevel dimension : level.getServer().getAllLevels()) {
            for (ServerPlayer player : dimension.players()) {
                if (player.isSpectator()) {
                    ids.remove(player.getUUID());
                } else if (tracker != null && tracker.dungeonPlayers().contains(player.getUUID())) {
                    ids.add(player.getUUID());
                }
            }
        }
        return ids;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Revival

    public enum OfferResult {
        STARTED, DISABLED, NO_LAIR, WRONG_GUARDIAN, ALREADY_AWAKE, CHAMBER_OCCUPIED, BUSY;

        public boolean success() {
            return this == STARTED;
        }
    }

    /**
     * A player used {@code kind}'s relic, either on a soul or anywhere inside a dormant lair. The soul is brought back if
     * it was lost, so a cleared entity can never strand a dungeon.
     */
    public static OfferResult offerRelic(ServerLevel level, Player player, GuardianKind kind, @Nullable GuardianSoul clicked) {
        if (!ASConfig.guardianRevival()) {
            return OfferResult.DISABLED;
        }
        GuardianLairs lairs = GuardianLairs.get(level);
        Optional<GuardianLair> found = clicked != null ? lairs.get(clicked.lairKey()) : lairs.containing(player.position());
        if (found.isEmpty() || !found.get().dormant()) {
            return found.isEmpty() ? OfferResult.NO_LAIR : OfferResult.ALREADY_AWAKE;
        }
        GuardianLair lair = found.get();
        if (lair.kind() != kind) {
            return OfferResult.WRONG_GUARDIAN;
        }
        if (!level.getEntitiesOfClass(Mob.class, lair.room(), mob -> mob.isAlive() && mob.getType() == kind.entityType()).isEmpty()) {
            return OfferResult.ALREADY_AWAKE;
        }
        if (treasureChamberOccupied(level, lair)) {
            return OfferResult.CHAMBER_OCCUPIED;
        }
        GuardianSoul soul = clicked != null ? clicked : soulOf(level, lair);
        if (soul == null) {
            soul = new GuardianSoul(level, kind, lair);
            soul.moveTo(lair.soulHome());
            level.addFreshEntity(soul);
        }
        return soul.beginRitual(player) ? OfferResult.STARTED : OfferResult.BUSY;
    }

    @Nullable
    private static GuardianSoul soulOf(ServerLevel level, GuardianLair lair) {
        return level.getEntitiesOfClass(GuardianSoul.class, lair.room().inflate(8), soul -> soul.lairKey().equals(lair.key()))
                .stream().findFirst().orElse(null);
    }

    public static Component offerMessage(OfferResult result, GuardianKind kind) {
        return switch (result) {
            case STARTED -> Component.translatable("message.aether_spellbooks.guardian.called", kind.bossName()).withStyle(ChatFormatting.GOLD);
            case DISABLED -> Component.translatable("message.aether_spellbooks.guardian.disabled").withStyle(ChatFormatting.GRAY);
            case NO_LAIR -> Component.translatable("message.aether_spellbooks.guardian.no_lair").withStyle(ChatFormatting.GRAY);
            case WRONG_GUARDIAN -> Component.translatable("message.aether_spellbooks.guardian.wrong_relic", kind.bossName()).withStyle(ChatFormatting.GRAY);
            case ALREADY_AWAKE -> Component.translatable("message.aether_spellbooks.guardian.awake").withStyle(ChatFormatting.GRAY);
            case CHAMBER_OCCUPIED -> Component.translatable("message.aether_spellbooks.guardian.chamber_occupied").withStyle(ChatFormatting.RED);
            case BUSY -> Component.translatable("message.aether_spellbooks.guardian.busy").withStyle(ChatFormatting.GRAY);
        };
    }

    /**
     * Restoring the room also closes the treasure doorway; nobody may be shut inside. The chamber is approximated by the
     * box spanning the treasure doorway blocks and what they guard: the vault, or the Treasure Chest when no vault was
     * made (conversion turned off, or a chest opened before this mod). Without either, two blocks around the doorway.
     */
    public static boolean treasureChamberOccupied(ServerLevel level, GuardianLair lair) {
        AABB chamber = treasureChamber(level, lair);
        return chamber != null && !level.getEntitiesOfClass(Player.class, chamber, player -> !player.isSpectator()).isEmpty();
    }

    @Nullable
    public static AABB treasureChamber(ServerLevel level, GuardianLair lair) {
        AABB doors = null;
        for (RoomSnapshot.Entry entry : lair.snapshot().entries()) {
            if (RoomRestorer.isTreasureDoorway(entry.state())) {
                doors = doors == null ? new AABB(entry.pos()) : doors.minmax(new AABB(entry.pos()));
            }
        }
        if (doors == null) {
            // no treasure doorway in this room: nothing gets sealed
            return null;
        }
        BlockPos guarded = lair.vault() != null ? lair.vault() : DungeonVaults.treasureChestIn(level, lair.room());
        return guarded == null ? doors.inflate(2) : doors.minmax(new AABB(guarded)).inflate(0.5, 0, 0.5);
    }

    /** The end of the ritual: the boss returns at its spawn point, with its old name and room. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Mob reviveBoss(ServerLevel level, GuardianLair lair) {
        GuardianKind kind = lair.kind();
        Mob boss = kind.entityType().create(level);
        if (boss == null) {
            return null;
        }
        Vec3 origin = lair.origin();
        boss.moveTo(origin.x, origin.y, origin.z, 0, 0);
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(origin)), MobSpawnType.TRIGGERED, null, null);
        BossMob bossMob = (BossMob) boss;
        if (lair.bossName() != null) {
            bossMob.setBossName(lair.bossName());
        }
        bossMob.setDungeon(new BossRoomTracker(boss, origin, lair.room(), new ArrayList<>()));
        if (boss instanceof ValkyrieQueen queen) {
            if (lair.queenBounds() != null) {
                queen.setDungeonBounds(lair.queenBounds());
            }
        } else if (boss instanceof Slider slider) {
            slider.setAwake(false);
        }
        boss.getPersistentData().putBoolean(REVIVED, true);
        if (!level.addFreshEntity(boss)) {
            return null;
        }
        if (boss instanceof ValkyrieQueen queen) {
            // the relic stands in for the ten Victory Medals: she accepts the challenge at once
            queen.readyUp();
        }
        GuardianLairs.get(level).markRevived(lair);
        DungeonVaults.refreshVaultsInRoom(level, lair.room());
        return boss;
    }
}
