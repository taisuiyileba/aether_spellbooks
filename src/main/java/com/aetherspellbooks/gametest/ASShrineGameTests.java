package com.aetherspellbooks.gametest;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.entity.mob.AetherSpellcaster;
import com.aetherspellbooks.registry.ASEntities;
import com.aetherteam.aether.data.resources.registries.AetherDimensions;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * The spellcaster shrines: registered, their templates carry the casters and the loot chest, the chests roll loot, and
 * they actually generate on Aether islands. Own batch, because placing a template brings hostile casters. Development only.
 */
@GameTestHolder(AetherSpellbooks.MODID)
@PrefixGameTestTemplate(false)
public class ASShrineGameTests {
    private static final String EMPTY = "empty";
    private static final String BATCH = "aether_spellbooks_shrines";
    private static final Map<String, EntityType<?>> SHRINES = Map.of(
            "valkyrie_sanctum", ASEntities.VALKYRIE_SORCERESS.get(),
            "solar_altar", ASEntities.SOLAR_ACOLYTE.get());

    private static ResourceLocation id(String path) {
        return AetherSpellbooks.id(path);
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void shrineTemplatesHoldCastersAndLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int offset = 0;
        for (var shrine : SHRINES.entrySet()) {
            StructureTemplate template = level.getStructureManager().get(id(shrine.getKey()))
                    .orElseThrow(() -> new AssertionError("missing template " + shrine.getKey()));
            // well above the test grid, side by side
            BlockPos origin = helper.absolutePos(new BlockPos(offset, 40, 0));
            offset += template.getSize().getX() + 4;
            StructurePlaceSettings settings = new StructurePlaceSettings().setFinalizeEntities(true);
            template.placeInWorld(level, origin, origin, settings, level.getRandom(), 2);
            BoundingBox box = template.getBoundingBox(settings, origin);
            AABB area = AABB.of(box).inflate(1);

            List<AetherSpellcaster> casters = level.getEntitiesOfClass(AetherSpellcaster.class, area);
            long matching = casters.stream().filter(c -> c.getType() == shrine.getValue()).count();
            helper.assertTrue(matching == 2, shrine.getKey() + " should hold two " + EntityType.getKey(shrine.getValue()) + ", found " + matching);
            helper.assertTrue(casters.stream().allMatch(c -> c.isPersistenceRequired()), "shrine casters must not despawn");

            String expected = AetherSpellbooks.MODID + ":chests/" + shrine.getKey();
            boolean chest = BlockPos.betweenClosedStream(box).anyMatch(pos -> {
                BlockEntity be = level.getBlockEntity(pos);
                return be instanceof ChestBlockEntity && expected.equals(be.saveWithoutMetadata().getString("LootTable"));
            });
            helper.assertTrue(chest, shrine.getKey() + " should have a chest with " + expected);
            casters.forEach(AetherSpellcaster::discard);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void shrineChestsRollLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (String shrine : SHRINES.keySet()) {
            LootTable table = level.getServer().getLootData().getLootTable(id("chests/" + shrine));
            helper.assertTrue(table != LootTable.EMPTY, "missing chest loot table for " + shrine);
            LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
            List<ItemStack> items = table.getRandomItems(params);
            helper.assertTrue(items.size() >= 4, shrine + " chest should hold at least 4 stacks, got " + items);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void shrinesGenerateOnAetherIslands(GameTestHelper helper) {
        ServerLevel aether = helper.getLevel().getServer().getLevel(AetherDimensions.AETHER_LEVEL);
        helper.assertTrue(aether != null, "the Aether dimension should exist");
        var registry = aether.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (String shrine : SHRINES.keySet()) {
            Holder<Structure> holder = registry.getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, id(shrine)));
            Pair<BlockPos, Holder<Structure>> found = aether.getChunkSource().getGenerator()
                    .findNearestMapStructure(aether, HolderSet.direct(holder), BlockPos.ZERO, 100, false);
            helper.assertTrue(found != null, shrine + " should generate somewhere near the Aether's origin");
            AetherSpellbooks.LOGGER.info("shrine test: nearest {} at {}", shrine, found.getFirst());
        }
        helper.succeed();
    }
}
