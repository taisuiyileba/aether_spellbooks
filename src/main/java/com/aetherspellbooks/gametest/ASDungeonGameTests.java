package com.aetherspellbooks.gametest;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.config.ASConfig;
import com.aetherspellbooks.dungeon.DungeonKeyRewards;
import com.aetherspellbooks.dungeon.DungeonVaults;
import com.aetherspellbooks.dungeon.GuardianKind;
import com.aetherspellbooks.dungeon.GuardianLair;
import com.aetherspellbooks.dungeon.GuardianLairs;
import com.aetherspellbooks.dungeon.Guardians;
import com.aetherspellbooks.dungeon.RoomSnapshot;
import com.aetherspellbooks.entity.GuardianSoul;
import com.aetherspellbooks.mixin.VaultRewardsAccessor;
import com.aetherspellbooks.registry.ASBlocks;
import com.aetherspellbooks.registry.ASItems;
import com.aetherteam.aether.block.AetherBlocks;
import com.aetherteam.aether.block.dungeon.DoorwayBlock;
import com.aetherteam.aether.blockentity.TreasureChestBlockEntity;
import com.aetherteam.aether.entity.AetherEntityTypes;
import com.aetherteam.aether.entity.monster.dungeon.boss.Slider;
import com.aetherteam.aether.entity.monster.dungeon.boss.ValkyrieQueen;
import com.aetherteam.aether.entity.monster.dungeon.boss.goal.CrushGoal;
import com.aetherteam.aether.item.AetherItems;
import com.aetherteam.aether.loot.AetherLoot;
import com.aetherteam.nitrogen.entity.BossRoomTracker;
import com.mojang.authlib.GameProfile;
import io.redspace.ironsspellbooks.api.backwards_compat.blocks.vault.VaultBlock;
import io.redspace.ironsspellbooks.api.backwards_compat.blocks.vault.data.VaultBlockEntity;
import io.redspace.ironsspellbooks.api.backwards_compat.blocks.vault.data.VaultState;
import io.redspace.ironsspellbooks.registries.BlockRegistry;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import io.redspace.ironsspellbooks.entity.mobs.dead_king_boss.DeadKingSoulEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dungeon rewards and rematches against the real Aether boss/room code: a small sealed "boss room" of locked carved
 * stone with a boss doorway, a Treasure Chest under a treasure doorway and a Slider whose tracker covers the room.
 */
@GameTestHolder(AetherSpellbooks.MODID)
@PrefixGameTestTemplate(false)
public class ASDungeonGameTests {
    private static final BlockPos CHEST = new BlockPos(6, 1, 6);
    private static final BlockPos TREASURE_DOOR = new BlockPos(6, 2, 6);
    private static final BlockPos BOSS_DOOR = new BlockPos(4, 1, 8);
    private static final BlockPos WALL = new BlockPos(0, 2, 4);
    private static final BlockPos BOSS_AT = new BlockPos(3, 1, 3);

    private record Room(AABB bounds, Slider slider, List<ServerPlayer> players) {
    }

    private static AABB bounds(GameTestHelper helper) {
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        return new AABB(corner, corner.offset(9, 6, 9));
    }

    /** Forgets lairs, souls, items and players left over by earlier batches at this structure position. */
    private static void clean(GameTestHelper helper, AABB bounds) {
        ServerLevel level = helper.getLevel();
        GuardianLairs.get(level).forgetWithin(bounds.inflate(2));
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, bounds.inflate(3))) {
            level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
        level.getEntitiesOfClass(Entity.class, bounds.inflate(3), entity -> !(entity instanceof Player)).forEach(Entity::discard);
    }

    private static void finish(GameTestHelper helper, Room room) {
        clean(helper, room.bounds);
        helper.succeed();
    }

    private static void buildRoom(GameTestHelper helper, boolean withChest) {
        BlockState locked = AetherBlocks.LOCKED_CARVED_STONE.get().defaultBlockState();
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 0, z), locked);
            }
        }
        for (int y = 1; y <= 3; y++) {
            for (int z = 0; z <= 8; z++) {
                helper.setBlock(new BlockPos(0, y, z), locked);
            }
        }
        BlockState door = AetherBlocks.BOSS_DOORWAY_CARVED_STONE.get().defaultBlockState().setValue(DoorwayBlock.INVISIBLE, true);
        helper.setBlock(BOSS_DOOR, door);
        helper.setBlock(BOSS_DOOR.above(), door);
        helper.setBlock(TREASURE_DOOR, AetherBlocks.TREASURE_DOORWAY_CARVED_STONE.get());
        if (withChest) {
            treasureChest(helper, CHEST);
        }
    }

    private static void treasureChest(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, AetherBlocks.TREASURE_CHEST.get());
        RandomizableContainerBlockEntity.setLootTable(helper.getLevel(), helper.getLevel().random, helper.absolutePos(pos),
                AetherLoot.BRONZE_DUNGEON_REWARD);
    }

    private static ServerPlayer player(GameTestHelper helper, String name, Vec3 at) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        Vec3 pos = helper.absoluteVec(at);
        player.moveTo(pos.x, pos.y, pos.z, 0, 0);
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        helper.getLevel().addNewPlayer(player);
        return player;
    }

    /** A sealed room with a dungeon Slider, players already inside (so the room has been remembered). */
    private static Room room(GameTestHelper helper, int players, boolean withChest) {
        AABB bounds = bounds(helper);
        clean(helper, bounds);
        buildRoom(helper, withChest);
        Slider slider = helper.spawn(AetherEntityTypes.SLIDER.get(), BOSS_AT);
        slider.setNoAi(true);
        slider.setDungeon(new BossRoomTracker<>(slider, slider.position(), bounds, new ArrayList<>()));
        List<ServerPlayer> list = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            list.add(player(helper, "asb-dungeon-" + i, new Vec3(1.5, 1, 1.5 + i)));
        }
        slider.getDungeon().trackPlayers();
        return new Room(bounds, slider, list);
    }

    private static int count(GameTestHelper helper, AABB bounds, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds.inflate(4), entity -> entity.getItem().is(item))
                .stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static List<GuardianSoul> souls(GameTestHelper helper, AABB bounds) {
        return helper.getLevel().getEntitiesOfClass(GuardianSoul.class, bounds.inflate(2));
    }

    private static void openVault(GameTestHelper helper, BlockPos pos, ServerPlayer player) {
        BlockPos at = helper.absolutePos(pos);
        helper.getBlockState(pos).use(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), net.minecraft.core.Direction.NORTH, at, false));
    }

    private static void moveNearVault(GameTestHelper helper, ServerPlayer player, double x) {
        player.moveTo(helper.absoluteVec(new Vec3(x, 1, 4.5)));
    }

    private static void holdKey(ServerPlayer player) {
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).is(AetherItems.BRONZE_DUNGEON_KEY.get())) {
                player.getInventory().selected = slot;
                return;
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    @GameTest(template = "empty", batch = "dungeon")
    public static void bossDeathTurnsTreasureChestIntoVault(GameTestHelper helper) {
        Room room = room(helper, 1, true);
        helper.assertTrue(GuardianLairs.get(helper.getLevel()).get(GuardianLair.keyOf(room.slider.position())).isPresent(),
                "entering the room must remember the lair");
        room.slider.kill();
        helper.assertBlockPresent(ASBlocks.CARVED_VAULT.get(), CHEST);
        VaultBlockEntity vault = (VaultBlockEntity) helper.getBlockEntity(CHEST);
        helper.assertTrue(vault.getConfig().lootTable().equals(AetherLoot.BRONZE_DUNGEON_REWARD), "vault keeps the chest's loot table");
        helper.assertTrue(vault.getConfig().keyItem().is(AetherItems.BRONZE_DUNGEON_KEY.get()), "vault opens with the bronze key");
        helper.assertTrue(BlockRegistry.VAULT_BLOCK_ENTITY.get().isValid(helper.getBlockState(CHEST)), "vault must tick as Iron's Spells' vault");
        helper.assertBlockPresent(AetherBlocks.CARVED_STONE.get(), WALL);
        helper.assertTrue(count(helper, room.bounds, AetherItems.ZANITE_HELMET.get()) + count(helper, room.bounds, Items.ARROW) == 0,
                "the chest must not spill its loot when it is replaced");
        helper.assertTrue(room.players.get(0).getInventory().countItem(AetherItems.BRONZE_DUNGEON_KEY.get()) == 1,
                "one participant receives one key in their inventory");
        helper.assertTrue(count(helper, room.bounds, AetherItems.BRONZE_DUNGEON_KEY.get()) == 0, "the key is not dropped on the ground");
        helper.assertTrue(souls(helper, room.bounds).size() == 1, "the boss leaves its soul");
        finish(helper, room);
    }

    @GameTest(template = "empty", batch = "dungeon")
    public static void summonedBossLeavesEverythingAlone(GameTestHelper helper) {
        AABB bounds = bounds(helper);
        clean(helper, bounds);
        buildRoom(helper, true);
        Slider slider = helper.spawn(AetherEntityTypes.SLIDER.get(), BOSS_AT);
        slider.setNoAi(true);
        ServerPlayer player = player(helper, "asb-summoner", new Vec3(1.5, 1, 1.5));
        slider.kill();
        helper.assertBlockPresent(AetherBlocks.TREASURE_CHEST.get(), CHEST);
        helper.assertTrue(((TreasureChestBlockEntity) helper.getBlockEntity(CHEST)).getLocked(), "chest stays locked");
        helper.assertBlockPresent(AetherBlocks.LOCKED_CARVED_STONE.get(), WALL);
        helper.assertTrue(count(helper, bounds, AetherItems.BRONZE_DUNGEON_KEY.get()) == 1, "the Aether's single key");
        helper.assertTrue(souls(helper, bounds).isEmpty(), "no soul without a dungeon");
        finish(helper, new Room(bounds, slider, List.of(player)));
    }

    @GameTest(template = "empty", batch = "dungeon")
    public static void openedAndFilledChestsAreKept(GameTestHelper helper) {
        Room room = room(helper, 1, false);
        BlockPos opened = new BlockPos(6, 1, 2);
        BlockPos filled = new BlockPos(2, 1, 6);
        treasureChest(helper, opened);
        ((TreasureChestBlockEntity) helper.getBlockEntity(opened)).setLocked(false);
        helper.setBlock(filled, AetherBlocks.TREASURE_CHEST.get());
        ((TreasureChestBlockEntity) helper.getBlockEntity(filled)).setItem(0, new ItemStack(Items.DIAMOND));
        room.slider.kill();
        helper.assertBlockPresent(AetherBlocks.TREASURE_CHEST.get(), opened);
        helper.assertBlockPresent(AetherBlocks.TREASURE_CHEST.get(), filled);
        CompoundTag saved = helper.getBlockEntity(opened).saveWithoutMetadata();
        helper.assertTrue(saved.contains("LootTable"), "an unlocked chest keeps its own loot");
        helper.assertTrue(((TreasureChestBlockEntity) helper.getBlockEntity(filled)).getItem(0).is(Items.DIAMOND), "hand-filled chest untouched");
        helper.assertTrue(count(helper, room.bounds, AetherItems.BRONZE_DUNGEON_KEY.get()) == 1, "no vault, no extra keys");
        finish(helper, room);
    }

    @GameTest(template = "empty", batch = "dungeon")
    public static void oneKeyForEachParticipant(GameTestHelper helper) {
        Room room = room(helper, 3, false);
        helper.setBlock(CHEST, ASBlocks.CARVED_VAULT.get());
        room.slider.kill();
        room.players.forEach(player -> helper.assertTrue(player.getInventory().countItem(AetherItems.BRONZE_DUNGEON_KEY.get()) == 1,
                "each of the three participants receives their own key"));
        helper.assertTrue(count(helper, room.bounds, AetherItems.BRONZE_DUNGEON_KEY.get()) == 0, "no shared key drops to steal");
        finish(helper, room);
    }

    @GameTest(template = "empty", batch = "dungeon", timeoutTicks = 200)
    public static void relicRebuildsTheRoomAndCallsTheBossBack(GameTestHelper helper) {
        Room room = room(helper, 1, true);
        ServerLevel level = helper.getLevel();
        GuardianLair before = GuardianLairs.get(level).get(GuardianLair.keyOf(room.slider.position())).orElseThrow();
        Component name = room.slider.getBossName();
        room.slider.kill();
        helper.assertTrue(before.dormant(), "lair is dormant after the kill");
        GuardianSoul soul = souls(helper, room.bounds).get(0);
        helper.assertTrue(Guardians.offerRelic(level, room.players.get(0), GuardianKind.SLIDER, soul) == Guardians.OfferResult.STARTED,
                "relic on the soul starts the ritual");
        helper.runAfterDelay(GuardianSoul.RITUAL_LENGTH + 5, () -> {
            helper.assertBlockPresent(AetherBlocks.LOCKED_CARVED_STONE.get(), WALL);
            helper.assertBlockPresent(AetherBlocks.LOCKED_CARVED_STONE.get(), new BlockPos(5, 0, 5));
            helper.assertBlockPresent(AetherBlocks.BOSS_DOORWAY_CARVED_STONE.get(), BOSS_DOOR);
            helper.assertTrue(helper.getBlockState(BOSS_DOOR).getValue(DoorwayBlock.INVISIBLE), "boss doorway comes back open");
            helper.assertBlockPresent(AetherBlocks.TREASURE_DOORWAY_CARVED_STONE.get(), TREASURE_DOOR);
            helper.assertBlockPresent(ASBlocks.CARVED_VAULT.get(), CHEST);
            List<Slider> alive = level.getEntitiesOfClass(Slider.class, room.bounds, Slider::isAlive);
            helper.assertTrue(alive.size() == 1, "exactly one Slider returns");
            Slider revived = alive.get(0);
            helper.assertTrue(revived.getDungeon() != null && revived.getDungeon().roomBounds().equals(room.bounds), "same room");
            helper.assertTrue(revived.getBossName().getString().equals(name.getString()), "same name");
            helper.assertTrue(!before.dormant() && before.revivals() == 1, "lair is awake again");
            helper.assertTrue(souls(helper, room.bounds).isEmpty(), "the soul is spent");
            finish(helper, room);
        });
    }

    @GameTest(template = "empty", batch = "dungeon")
    public static void relicRefusals(GameTestHelper helper) {
        Room room = room(helper, 1, true);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = room.players.get(0);
        helper.assertTrue(Guardians.offerRelic(level, player, GuardianKind.SLIDER, null) == Guardians.OfferResult.ALREADY_AWAKE,
                "the boss is still alive");
        ServerPlayer outside = player(helper, "asb-outside", new Vec3(4.5, 8, 4.5));
        helper.assertTrue(Guardians.offerRelic(level, outside, GuardianKind.SLIDER, null) == Guardians.OfferResult.NO_LAIR, "outside any lair");
        room.slider.kill();
        helper.assertTrue(Guardians.offerRelic(level, player, GuardianKind.SUN_SPIRIT, null) == Guardians.OfferResult.WRONG_GUARDIAN,
                "the wrong relic");
        Vec3 chamber = helper.absoluteVec(new Vec3(5.6, 1, 6.5));
        player.moveTo(chamber.x, chamber.y, chamber.z);
        helper.assertTrue(Guardians.offerRelic(level, player, GuardianKind.SLIDER, null) == Guardians.OfferResult.CHAMBER_OCCUPIED,
                "someone stands in the treasure chamber");
        Vec3 safe = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
        player.moveTo(safe.x, safe.y, safe.z);
        souls(helper, room.bounds).forEach(Entity::discard);
        helper.assertTrue(Guardians.offerRelic(level, player, GuardianKind.SLIDER, null) == Guardians.OfferResult.STARTED,
                "a lost soul is recreated by the relic");
        helper.assertTrue(souls(helper, room.bounds).size() == 1, "one soul again");
        helper.assertTrue(Guardians.offerRelic(level, player, GuardianKind.SLIDER, null) == Guardians.OfferResult.BUSY, "ritual already running");
        finish(helper, room);
    }

    @GameTest(template = "empty", batch = "dungeon")
    public static void treasureChamberIsGuardedWithoutAVault(GameTestHelper helper) {
        Room room = room(helper, 1, true);
        ((TreasureChestBlockEntity) helper.getBlockEntity(CHEST)).setLocked(false);
        ServerLevel level = helper.getLevel();
        room.slider.kill();
        helper.assertBlockPresent(AetherBlocks.TREASURE_CHEST.get(), CHEST);
        ServerPlayer player = room.players.get(0);
        Vec3 chamber = helper.absoluteVec(new Vec3(5.6, 1, 6.5));
        player.moveTo(chamber.x, chamber.y, chamber.z);
        helper.assertTrue(Guardians.offerRelic(level, player, GuardianKind.SLIDER, null) == Guardians.OfferResult.CHAMBER_OCCUPIED,
                "an opened Treasure Chest still marks the chamber");
        finish(helper, room);
    }

    @GameTest(template = "empty", batch = "dungeon")
    public static void revivedBossDropsOnlyKeyAndAetherBossDrops(GameTestHelper helper) {
        Room room = room(helper, 2, true);
        ServerLevel level = helper.getLevel();
        GuardianLair lair = GuardianLairs.get(level).get(GuardianLair.keyOf(room.slider.position())).orElseThrow();
        room.slider.kill();
        level.getEntitiesOfClass(ItemEntity.class, room.bounds.inflate(4)).forEach(Entity::discard);
        Slider revived = (Slider) Guardians.reviveBoss(level, lair);
        helper.assertTrue(revived != null, "revived Slider spawned");
        revived.getDungeon().trackPlayers();
        revived.kill();
        room.players.forEach(player -> helper.assertTrue(player.getInventory().countItem(AetherItems.BRONZE_DUNGEON_KEY.get()) == 2,
                "each participant receives one key on each of the two kills"));
        helper.assertTrue(count(helper, room.bounds, AetherItems.BRONZE_DUNGEON_KEY.get()) == 0, "keys go directly to inventories");
        helper.assertTrue(count(helper, room.bounds, AetherBlocks.CARVED_STONE.get().asItem()) >= 7,
                "revived Slider still drops the Aether's carved stone");
        helper.assertTrue(count(helper, room.bounds, ASItems.GUARDIAN_SHARD.get()) == 0
                        && count(helper, room.bounds, ASItems.SLIDER_CODEX.get()) == 0,
                "revived boss does not drop vault rewards directly");
        finish(helper, room);
    }

    @GameTest(template = "empty", batch = "dungeon", timeoutTicks = 240)
    public static void personalVaultRewardsResetForTheNextBossFight(GameTestHelper helper) {
        Room room = room(helper, 2, true);
        ServerLevel level = helper.getLevel();
        GuardianLair lair = GuardianLairs.get(level).get(GuardianLair.keyOf(room.slider.position())).orElseThrow();
        ServerPlayer first = room.players.get(0);
        ServerPlayer second = room.players.get(1);
        moveNearVault(helper, first, 6.5);
        moveNearVault(helper, second, 5.5);
        room.slider.kill();
        first.getInventory().add(new ItemStack(AetherItems.BRONZE_DUNGEON_KEY.get()));
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(first.getInventory().countItem(AetherItems.BRONZE_DUNGEON_KEY.get()) == 2
                            && second.getInventory().countItem(AetherItems.BRONZE_DUNGEON_KEY.get()) == 1,
                    "two personal keys plus one spare key for the first player");
            holdKey(first);
            holdKey(second);
            openVault(helper, CHEST, first);
            helper.assertTrue(first.getInventory().countItem(AetherItems.BRONZE_DUNGEON_KEY.get()) == 1, "opening consumes exactly one key");
            holdKey(first);
            helper.assertTrue(DungeonVaults.rewardedPlayers(level, helper.absolutePos(CHEST)).contains(first.getUUID()),
                    "opening records the first player's UUID");
            int firstShards = count(helper, room.bounds, ASItems.GUARDIAN_SHARD.get());
            helper.assertTrue(firstShards >= 2, "all loot is ejected at once");
            openVault(helper, CHEST, second);
            helper.assertTrue(second.getMainHandItem().getCount() == 1, "a busy vault does not consume the second player's key");
            VaultBlockEntity vault = (VaultBlockEntity) helper.getBlockEntity(CHEST);
            vault.load(vault.saveWithoutMetadata());
            helper.assertTrue(DungeonVaults.rewardedPlayers(level, helper.absolutePos(CHEST)).contains(first.getUUID()),
                    "the per-player lock survives saving and reloading");
            helper.runAfterDelay(15, () -> {
                openVault(helper, CHEST, first);
                helper.assertTrue(first.getMainHandItem().getCount() == 1, "a spare key cannot bypass the first player's lock");
                helper.assertTrue(count(helper, room.bounds, ASItems.GUARDIAN_SHARD.get()) == firstShards,
                        "a rejected repeat opening gives no extra loot");
                openVault(helper, CHEST, second);
                helper.assertTrue(second.getMainHandItem().isEmpty(), "the second player can claim independently");
                helper.assertTrue(DungeonVaults.rewardedPlayers(level, helper.absolutePos(CHEST)).size() == 2,
                        "both players have claimed this round");
                helper.assertTrue(count(helper, room.bounds, ASItems.GUARDIAN_SHARD.get()) >= firstShards + 2,
                        "the second player receives a separate loot roll");
                helper.assertTrue(Guardians.offerRelic(level, first, GuardianKind.SLIDER, souls(helper, room.bounds).get(0))
                        == Guardians.OfferResult.STARTED, "the real revival ritual starts");
                helper.runAfterDelay(GuardianSoul.RITUAL_LENGTH + 5, () -> {
                    helper.assertTrue(DungeonVaults.rewardedPlayers(level, helper.absolutePos(CHEST)).isEmpty(),
                            "boss revival resets every player's reward history");
                    Slider revived = level.getEntitiesOfClass(Slider.class, room.bounds, Slider::isAlive).get(0);
                    helper.assertTrue(!lair.dormant(), "the revived boss guards this lair again");
                    revived.getDungeon().trackPlayers();
                    revived.kill();
                    helper.assertTrue(first.getInventory().countItem(AetherItems.BRONZE_DUNGEON_KEY.get()) == 2
                                    && second.getInventory().countItem(AetherItems.BRONZE_DUNGEON_KEY.get()) == 1,
                            "the next kill distributes a new key to each participant");
                    helper.runAfterDelay(25, () -> {
                        holdKey(first);
                        openVault(helper, CHEST, first);
                        helper.assertTrue(first.getInventory().countItem(AetherItems.BRONZE_DUNGEON_KEY.get()) == 1,
                                "the first player can open again in the next round");
                        finish(helper, room);
                    });
                });
            });
        });
    }

    @GameTest(template = "empty", batch = "dungeon")
    public static void mergedEjectionEndsOnTheRarestItem(GameTestHelper helper) {
        List<ItemStack> merged = DungeonVaults.mergeForEjection(List.of(new ItemStack(Items.ARROW, 10), new ItemStack(ASItems.SLIDER_CODEX.get()),
                new ItemStack(Items.ARROW, 5), new ItemStack(AetherItems.ZANITE_GEMSTONE.get(), 3), new ItemStack(Items.ARROW, 60)));
        helper.assertTrue(merged.size() == 4, "75 arrows -> 64 + 11, plus the gemstones and the codex: " + merged);
        helper.assertTrue(merged.stream().filter(stack -> stack.is(Items.ARROW)).mapToInt(ItemStack::getCount).sum() == 75, "no arrow lost");
        helper.assertTrue(merged.get(merged.size() - 1).is(ASItems.SLIDER_CODEX.get()), "the epic codex comes out last");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dungeon")
    public static void revivedQueenAcceptsTheChallengeAtOnce(GameTestHelper helper) {
        AABB bounds = bounds(helper);
        clean(helper, bounds);
        ServerLevel level = helper.getLevel();
        AABB temple = bounds.inflate(20);
        GuardianLair lair = new GuardianLair(GuardianKind.VALKYRIE_QUEEN, helper.absoluteVec(new Vec3(4.5, 1, 4.5)), bounds,
                RoomSnapshot.capture(level, bounds, GuardianKind.VALKYRIE_QUEEN.conversions()), temple);
        GuardianLairs.get(level).put(lair);
        GuardianLairs.get(level).markDefeated(lair, Component.literal("Asbjorg, Queen of the Valkyries"), null);
        ValkyrieQueen queen = (ValkyrieQueen) Guardians.reviveBoss(level, lair);
        helper.assertTrue(queen != null && queen.isReady(), "the relic stands in for the medals");
        helper.assertTrue(queen.getBossName().getString().equals("Asbjorg, Queen of the Valkyries"), "she keeps her name");
        helper.assertTrue(queen.getDungeon() != null && queen.getDungeon().roomBounds().equals(bounds), "she guards the same room");
        CompoundTag saved = queen.saveWithoutId(new CompoundTag());
        helper.assertTrue(saved.getDouble("DungeonBoundsMinX") == temple.minX, "and tears down the whole temple again");
        clean(helper, bounds);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dungeon", timeoutTicks = 60)
    public static void ironsVaultEjectionFollowsConfigSwitch(GameTestHelper helper) {
        AABB bounds = bounds(helper);
        clean(helper, bounds);
        BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, BlockRegistry.BONE_VAULT_BLOCK.get());
        ServerPlayer player = player(helper, "asb-bone-vault", new Vec3(4.5, 1, 2.5));
        helper.succeedWhen(() -> {
            BlockState state = helper.getBlockState(pos);
            helper.assertTrue(state.getValue(VaultBlock.STATE) == VaultState.ACTIVE, "bone vault wakes up for a nearby player");
            VaultBlockEntity vault = (VaultBlockEntity) helper.getBlockEntity(pos);
            BlockPos at = helper.absolutePos(pos);
            ServerLevel level = helper.getLevel();

            // When config is enabled, bursts out all loot and sets state to EJECTING with 0 items left in queue
            boolean original = ASConfig.MODIFY_IRONS_VAULTS.get();
            try {
                ASConfig.MODIFY_IRONS_VAULTS.set(true);
                ItemStack key = new ItemStack(ItemRegistry.BONE_KEY.get(), 1);
                VaultBlockEntity.Server.tryInsertKey(level, at, state, vault.getConfig(), vault.getServerData(), vault.getSharedData(), player, key);
                helper.assertTrue(key.isEmpty(), "bone key was consumed");
                helper.assertTrue(helper.getBlockState(pos).getValue(VaultBlock.STATE) == VaultState.EJECTING, "bone vault entered ejecting shutter state");
                VaultRewardsAccessor accessor = (VaultRewardsAccessor) vault.getServerData();
                helper.assertTrue(accessor.aetherSpellbooks$getItemsToEject().isEmpty(), "instant ejection cleared queue so all items burst out at once");
                helper.assertTrue(accessor.aetherSpellbooks$getRewardedPlayers().contains(player.getUUID()), "player is recorded as rewarded for the bone vault");
            } finally {
                ASConfig.MODIFY_IRONS_VAULTS.set(original);
            }
            clean(helper, bounds);
        });
    }

    @GameTest(template = "empty", batch = "dungeon", timeoutTicks = 60)
    public static void revivingBossRefreshesArenaVaults(GameTestHelper helper) {
        AABB bounds = bounds(helper);
        clean(helper, bounds);
        BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, BlockRegistry.BONE_VAULT_BLOCK.get());
        ServerPlayer player = player(helper, "asb-refresh-test", new Vec3(4.5, 1, 2.5));
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(pos);
        VaultBlockEntity vault = (VaultBlockEntity) helper.getBlockEntity(pos);
        VaultRewardsAccessor accessor = (VaultRewardsAccessor) vault.getServerData();
        accessor.aetherSpellbooks$getRewardedPlayers().add(player.getUUID());
        helper.assertTrue(accessor.aetherSpellbooks$getRewardedPlayers().contains(player.getUUID()), "player starts locked out");

        BlockPos customPos = new BlockPos(5, 1, 4);
        helper.setBlock(customPos, ASBlocks.CARVED_VAULT.get());
        VaultBlockEntity custom = (VaultBlockEntity) helper.getBlockEntity(customPos);
        custom.getServerData().addToRewardedPlayers(player);
        boolean originalCompatibility = ASConfig.MODIFY_IRONS_VAULTS.get();
        boolean originalReset = ASConfig.RESET_VAULTS_ON_REVIVAL.get();
        try {
            ASConfig.RESET_VAULTS_ON_REVIVAL.set(true);
            ASConfig.MODIFY_IRONS_VAULTS.set(false);
            DungeonVaults.refreshVaultsInRoom(level, bounds);
            helper.assertTrue(DungeonVaults.rewardedPlayers(level, helper.absolutePos(customPos)).isEmpty(), "our vault resets by default");
            helper.assertTrue(accessor.aetherSpellbooks$getRewardedPlayers().contains(player.getUUID()), "disabled compatibility preserves Iron's vault history");
            custom.getServerData().addToRewardedPlayers(player);
            ASConfig.MODIFY_IRONS_VAULTS.set(true);
            ASConfig.RESET_VAULTS_ON_REVIVAL.set(false);
            DungeonVaults.refreshVaultsAround(level, at, 8);
            helper.assertTrue(DungeonVaults.rewardedPlayers(level, helper.absolutePos(customPos)).contains(player.getUUID()), "disabled reset preserves our history");
            helper.assertTrue(accessor.aetherSpellbooks$getRewardedPlayers().contains(player.getUUID()), "disabled reset preserves Iron's history too");
            ASConfig.RESET_VAULTS_ON_REVIVAL.set(true);
            DungeonVaults.refreshVaultsAround(level, at, 8);
            helper.assertTrue(DungeonVaults.rewardedPlayers(level, helper.absolutePos(customPos)).isEmpty(), "our vault refreshes again");
            helper.assertTrue(accessor.aetherSpellbooks$getRewardedPlayers().isEmpty(), "enabled compatibility refreshes the original vault");
        } finally {
            ASConfig.MODIFY_IRONS_VAULTS.set(originalCompatibility);
            ASConfig.RESET_VAULTS_ON_REVIVAL.set(originalReset);
        }
        clean(helper, bounds);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dungeon")
    public static void keyDistributionCanKeepTheAethersSingleDrop(GameTestHelper helper) {
        Room room = room(helper, 3, true);
        boolean original = ASConfig.KEY_PER_PARTICIPANT.get();
        try {
            ASConfig.KEY_PER_PARTICIPANT.set(false);
            room.slider.kill();
            helper.assertTrue(count(helper, room.bounds, AetherItems.BRONZE_DUNGEON_KEY.get()) == 1, "disabled distribution keeps the original single drop");
            room.players.forEach(player -> helper.assertTrue(player.getInventory().countItem(AetherItems.BRONZE_DUNGEON_KEY.get()) == 0,
                    "disabled distribution gives no extra inventory keys"));
        } finally {
            ASConfig.KEY_PER_PARTICIPANT.set(original);
            clean(helper, room.bounds);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dungeon")
    public static void keysReachParticipantsInAnotherDimension(GameTestHelper helper) {
        Room room = room(helper, 2, true);
        ServerLevel level = helper.getLevel();
        ServerLevel other = level.getServer().getLevel(Level.NETHER);
        ServerPlayer moved = room.players.get(1);
        try {
            level.removePlayerImmediately(moved, Entity.RemovalReason.CHANGED_DIMENSION);
            moved.setServerLevel(other);
            moved.revive();
            other.addNewPlayer(moved);
            room.slider.kill();
            room.players.forEach(player -> helper.assertTrue(player.getInventory().countItem(AetherItems.BRONZE_DUNGEON_KEY.get()) == 1,
                    "both the local and the other-dimension participant receive a key"));
            helper.assertTrue(DungeonKeyRewards.get(level) == DungeonKeyRewards.get(other), "pending keys are shared across dimensions");
        } finally {
            other.removePlayerImmediately(moved, Entity.RemovalReason.DISCARDED);
            clean(helper, room.bounds);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dungeon")
    public static void offlineParticipantKeepsTheirKeyUntilReturning(GameTestHelper helper) {
        Room room = room(helper, 1, true);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = room.players.get(0);
        level.removePlayerImmediately(player, Entity.RemovalReason.CHANGED_DIMENSION);
        room.slider.kill();
        DungeonKeyRewards rewards = DungeonKeyRewards.get(level);
        helper.assertTrue(rewards.hasPending(player.getUUID()), "the offline participant keeps a pending key");
        DungeonKeyRewards reloaded = DungeonKeyRewards.load(rewards.save(new CompoundTag()));
        helper.assertTrue(reloaded.hasPending(player.getUUID()), "pending keys survive a world save/reload");
        player.revive();
        level.addNewPlayer(player);
        rewards.deliver(player);
        rewards.deliver(player);
        helper.assertTrue(player.getInventory().countItem(AetherItems.BRONZE_DUNGEON_KEY.get()) == 1, "rejoining delivers exactly once");
        helper.assertTrue(!rewards.hasPending(player.getUUID()), "a delivered key is removed from pending rewards");
        finish(helper, room);
    }

    @GameTest(template = "empty", batch = "dungeon")
    public static void fullInventoryDoesNotLoseOrDuplicateKeys(GameTestHelper helper) {
        Room room = room(helper, 1, true);
        ServerPlayer player = room.players.get(0);
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        }
        room.slider.kill();
        DungeonKeyRewards rewards = DungeonKeyRewards.get(helper.getLevel());
        helper.assertTrue(rewards.hasPending(player.getUUID()), "a full inventory keeps its key pending");
        helper.assertTrue(count(helper, room.bounds, AetherItems.BRONZE_DUNGEON_KEY.get()) == 0, "the key never spills into shared drops");
        player.getInventory().setItem(0, ItemStack.EMPTY);
        player.tickCount = 20;
        com.aetherspellbooks.event.DungeonEvents.onPlayerTick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(
                net.minecraftforge.event.TickEvent.Phase.END, player));
        helper.assertTrue(player.getInventory().countItem(AetherItems.BRONZE_DUNGEON_KEY.get()) == 1, "freeing a slot delivers the key on the player tick");
        rewards.deliver(player);
        helper.assertTrue(player.getInventory().countItem(AetherItems.BRONZE_DUNGEON_KEY.get()) == 1, "repeated delivery does not duplicate the key");
        finish(helper, room);
    }

    @GameTest(template = "empty", batch = "dungeon")
    public static void sliderCannotCrushAnyDungeonVault(GameTestHelper helper) {
        AABB bounds = bounds(helper);
        clean(helper, bounds);
        ServerLevel level = helper.getLevel();
        boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        try {
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
            Slider slider = helper.spawn(AetherEntityTypes.SLIDER.get(), new BlockPos(4, 1, 3));
            slider.setNoAi(true);
            slider.setDungeon(new BossRoomTracker<>(slider, slider.position(), bounds, new ArrayList<>()));
            slider.setAwake(true);
            slider.horizontalCollision = true;
            CrushGoal crush = new CrushGoal(slider);
            helper.assertTrue(crush.canUse(), "the real crush goal is triggered by a collision");
            for (var block : ASBlocks.VAULTS) {
                BlockPos pos = new BlockPos(4, 1, 4);
                helper.setBlock(pos, block.get());
                crush.start();
                helper.assertBlockPresent(block.get(), pos);
            }
        } finally {
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, level.getServer());
            clean(helper, bounds);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dungeon", timeoutTicks = 160)
    public static void queenRitualRestoresTheWholeTemple(GameTestHelper helper) {
        AABB temple = bounds(helper);
        clean(helper, temple);
        ServerLevel level = helper.getLevel();
        BlockPos outside = new BlockPos(7, 2, 4);
        helper.setBlock(WALL, AetherBlocks.LOCKED_ANGELIC_STONE.get());
        helper.setBlock(outside, AetherBlocks.LOCKED_ANGELIC_STONE.get());
        ValkyrieQueen queen = helper.spawn(AetherEntityTypes.VALKYRIE_QUEEN.get(), new BlockPos(2, 1, 4));
        queen.setNoAi(true);
        AABB room = new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(5, 6, 9)));
        queen.setDungeon(new BossRoomTracker<>(queen, queen.position(), room, new ArrayList<>()));
        queen.setDungeonBounds(temple);
        ServerPlayer player = player(helper, "asb-temple", new Vec3(1.5, 1, 1.5));
        queen.getDungeon().trackPlayers();
        queen.kill();
        helper.assertBlockPresent(AetherBlocks.ANGELIC_STONE.get(), outside);
        GuardianSoul soul = souls(helper, temple).get(0);
        helper.assertTrue(Guardians.offerRelic(level, player, GuardianKind.VALKYRIE_QUEEN, soul) == Guardians.OfferResult.STARTED,
                "the Queen's ritual starts");
        helper.runAfterDelay(GuardianSoul.RITUAL_LENGTH + 5, () -> {
            helper.assertBlockPresent(AetherBlocks.LOCKED_ANGELIC_STONE.get(), outside);
            clean(helper, temple);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "dungeon")
    public static void deadKingRevivalResetsVaultsOnlyWhenBothOptionsAllowIt(GameTestHelper helper) {
        AABB bounds = bounds(helper);
        clean(helper, bounds);
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, BlockRegistry.BONE_VAULT_BLOCK.get());
        VaultBlockEntity vault = (VaultBlockEntity) helper.getBlockEntity(pos);
        ServerPlayer player = player(helper, "asb-king-revival", new Vec3(4.5, 1, 2.5));
        boolean originalCompatibility = ASConfig.MODIFY_IRONS_VAULTS.get();
        boolean originalReset = ASConfig.RESET_VAULTS_ON_REVIVAL.get();
        try {
            for (int scenario = 0; scenario < 3; scenario++) {
                ASConfig.MODIFY_IRONS_VAULTS.set(scenario != 0);
                ASConfig.RESET_VAULTS_ON_REVIVAL.set(scenario != 1);
                vault.getServerData().addToRewardedPlayers(player);
                Vec3 at = helper.absoluteVec(new Vec3(4.5, 1, 5.5));
                DeadKingSoulEntity soul = new DeadKingSoulEntity(level, at, at);
                level.addFreshEntity(soul);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.DEAD_KING_PHYLACTERY.get()));
                soul.interactAt(player, Vec3.ZERO, InteractionHand.MAIN_HAND);
                helper.assertTrue(DungeonVaults.rewardedPlayers(level, helper.absolutePos(pos)).isEmpty() == (scenario == 2),
                        "the real Dead King soul interaction resets history only when compatibility and revival reset are both enabled");
            }
        } finally {
            ASConfig.MODIFY_IRONS_VAULTS.set(originalCompatibility);
            ASConfig.RESET_VAULTS_ON_REVIVAL.set(originalReset);
            clean(helper, bounds);
        }
        helper.succeed();
    }
}
