package com.aetherspellbooks.gametest;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.entity.mob.AetherSpellcaster;
import com.aetherspellbooks.entity.mob.SolarAcolyte;
import com.aetherspellbooks.entity.mob.ValkyrieSorceress;
import com.aetherspellbooks.registry.ASEntities;
import com.aetherteam.aether.data.resources.registries.AetherBiomes;
import com.aetherteam.aether.entity.miscellaneous.CloudMinion;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Behaviour tests for the spellcasting mobs. They run in their own batch so no test players from other
 * batches are around to draw the casters' aggro. Development only.
 */
@GameTestHolder(AetherSpellbooks.MODID)
@PrefixGameTestTemplate(false)
public class ASMobGameTests {
    private static final String EMPTY = "empty";
    private static final String BATCH = "aether_spellbooks_mobs";

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static Husk husk(GameTestHelper helper, int x, int z) {
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(x, 1, z));
        husk.setPersistenceRequired();
        return husk;
    }

    /**
     * Earlier batches leave their fake players and those players' summons in the world; the casters would
     * rightly pick fights with them instead of the test target, so clear them out first.
     */
    private static void clearLeftovers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (ServerPlayer player : List.copyOf(level.players())) {
            if (player instanceof FakePlayer) {
                level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            }
        }
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof IMagicSummon summon && !(summon.getSummoner() instanceof AetherSpellcaster) || entity instanceof CloudMinion) {
                entity.discard();
            }
        }
    }

    private static <T extends AetherSpellcaster> T caster(GameTestHelper helper, EntityType<T> type, int x, int y, int z) {
        clearLeftovers(helper);
        T caster = helper.spawn(type, new BlockPos(x, y, z));
        caster.setPersistenceRequired();
        return caster;
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 400)
    public static void valkyrieSorceressCastsAtHerTarget(GameTestHelper helper) {
        floor(helper);
        ValkyrieSorceress sorceress = caster(helper, ASEntities.VALKYRIE_SORCERESS.get(), 1, 1, 4);
        Husk husk = husk(helper, 6, 4);
        float max = husk.getHealth();
        sorceress.setTarget(husk);
        helper.succeedWhen(() -> helper.assertTrue(husk.getHealth() < max, "the sorceress should strike the husk with her spells"));
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 400)
    public static void solarAcolyteCastsAtItsTarget(GameTestHelper helper) {
        floor(helper);
        SolarAcolyte acolyte = caster(helper, ASEntities.SOLAR_ACOLYTE.get(), 1, 1, 4);
        Husk husk = husk(helper, 6, 4);
        float max = husk.getHealth();
        acolyte.setTarget(husk);
        helper.succeedWhen(() -> helper.assertTrue(husk.getHealth() < max || husk.isOnFire(), "the acolyte should burn the husk"));
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 100)
    public static void valkyrieSorceressGlidesDown(GameTestHelper helper) {
        floor(helper);
        ValkyrieSorceress sorceress = caster(helper, ASEntities.VALKYRIE_SORCERESS.get(), 4, 7, 4);
        double startY = sorceress.getY();
        helper.runAfterDelay(10, () -> helper.assertTrue(sorceress.getY() > startY - 2.0,
                "she should glide, not fall (dropped " + (startY - sorceress.getY()) + " blocks in 10 ticks)"));
        helper.succeedWhen(() -> {
            helper.assertTrue(sorceress.onGround(), "she should land");
            helper.assertTrue(sorceress.getHealth() == sorceress.getMaxHealth(), "she should take no fall damage");
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void spellcastersSpawnInAetherBiomes(GameTestHelper helper) {
        Biome meadow = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getOrThrow(AetherBiomes.SKYROOT_MEADOW);
        for (EntityType<?> type : List.of(ASEntities.VALKYRIE_SORCERESS.get(), ASEntities.SOLAR_ACOLYTE.get())) {
            boolean spawns = meadow.getMobSettings().getMobs(type.getCategory()).unwrap().stream().anyMatch(data -> data.type == type);
            helper.assertTrue(spawns, EntityType.getKey(type) + " should be added to the Skyroot Meadow spawns");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void spellcastersHaveLoot(GameTestHelper helper) {
        floor(helper);
        SolarAcolyte acolyte = caster(helper, ASEntities.SOLAR_ACOLYTE.get(), 4, 1, 4);
        ValkyrieSorceress sorceress = caster(helper, ASEntities.VALKYRIE_SORCERESS.get(), 2, 1, 2);
        helper.assertTrue(acolyte.isAlliedTo(sorceress) && sorceress.isAlliedTo(acolyte), "Aether spellcasters should be allies");
        var lootData = helper.getLevel().getServer().getLootData();
        helper.assertTrue(lootData.getLootTable(sorceress.getType().getDefaultLootTable()) != LootTable.EMPTY, "the sorceress needs a loot table");
        LootTable table = lootData.getLootTable(acolyte.getType().getDefaultLootTable());
        LootParams params = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.THIS_ENTITY, acolyte)
                .withParameter(LootContextParams.ORIGIN, acolyte.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, helper.getLevel().damageSources().generic())
                .create(LootContextParamSets.ENTITY);
        List<ItemStack> drops = table.getRandomItems(params);
        helper.assertTrue(drops.stream().anyMatch(stack -> stack.getItem().builtInRegistryHolder().key().location().getPath().equals("golden_amber")),
                "the acolyte always drops golden amber, got " + drops);
        acolyte.discard();
        sorceress.discard();
        helper.succeed();
    }
}
