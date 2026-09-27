package com.aetherspellbooks.gametest;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.entity.StonebreakerShard;
import com.aetherspellbooks.entity.SummonedFireMinion;
import com.aetherspellbooks.entity.SummonedMoa;
import com.aetherspellbooks.registry.ASBlocks;
import com.aetherspellbooks.registry.ASItems;
import com.aetherspellbooks.registry.ASSpells;
import com.aetherteam.aether.api.AetherMoaTypes;
import com.aetherteam.aether.entity.AetherEntityTypes;
import com.aetherteam.aether.entity.monster.dungeon.boss.Slider;
import com.aetherteam.aether.entity.monster.dungeon.boss.SunSpirit;
import com.aetherteam.aether.entity.projectile.crystal.IceCrystal;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.entity.spells.magic_missile.MagicMissileProjectile;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.mojang.authlib.GameProfile;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Run with {@code gradlew runGameTestServer}. Development only; excluded from the release jar.
 */
@GameTestHolder(AetherSpellbooks.MODID)
@PrefixGameTestTemplate(false)
public class ASGameTests {
    private static final String EMPTY = "empty";

    /**
     * Vanilla's mock player goes through the full login, which trips modded networking on a channel-less
     * connection. A Forge FakePlayer added straight to the level avoids that and is still resolvable by
     * UUID, which Iron's Spells needs to look up summon owners.
     */
    private static ServerPlayer casterAt(GameTestHelper helper, double x, double y, double z, float yaw) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "aether-spellbooks-test"));
        Vec3 pos = helper.absoluteVec(new Vec3(x, y, z));
        player.moveTo(pos.x, pos.y, pos.z, yaw, 0);
        player.setYHeadRot(yaw);
        level.addNewPlayer(player);
        return player;
    }

    private static void cast(AbstractSpell spell, int level, ServerPlayer player) {
        spell.onCast(player.level(), level, player, CastSource.SPELLBOOK, MagicData.getPlayerMagicData(player));
    }

    @GameTest(template = EMPTY)
    public static void aercloudStepBuildsPlatform(GameTestHelper helper) {
        ServerPlayer player = casterAt(helper, 4.5, 5, 4.5, 0);
        cast(ASSpells.AERCLOUD_STEP.get(), 3, player);
        // level 3: 5x5 platform, blue centre
        helper.assertBlockPresent(ASBlocks.TEMPORARY_BLUE_AERCLOUD.get(), new BlockPos(4, 4, 4));
        helper.assertBlockPresent(ASBlocks.TEMPORARY_COLD_AERCLOUD.get(), new BlockPos(6, 4, 6));
        BlockPos abs = helper.absolutePos(new BlockPos(6, 4, 6));
        helper.assertTrue(helper.getLevel().getBlockTicks().hasScheduledTick(abs, ASBlocks.TEMPORARY_COLD_AERCLOUD.get()),
                "platform should schedule its own removal");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void stonebreakerShardCanHurtSlider(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = casterAt(helper, 1.5, 1, 4.5, -90);
        Slider slider = helper.spawn(AetherEntityTypes.SLIDER.get(), new BlockPos(5, 1, 4));

        // Control: a regular Iron's Spells projectile is deflected by the Slider
        MagicMissileProjectile missile = new MagicMissileProjectile(level, player);
        boolean missileHurt = slider.hurt(SpellRegistry.MAGIC_MISSILE_SPELL.get().getDamageSource(missile, player), 10);
        helper.assertFalse(missileHurt, "magic missile should not be able to hurt the Slider");

        StonebreakerShard shard = new StonebreakerShard(level, player);
        var source = ASSpells.STONEBREAKER_SHARD.get().getDamageSource(shard, player);
        // First accepted hit wakes the Slider and starts the boss fight (it heals to full, same as with a pickaxe)
        helper.assertTrue(slider.hurt(source, 10), "stonebreaker shard should be accepted by the Slider");
        helper.assertTrue(slider.isBossFight(), "the first shard hit should start the boss fight");
        // Subsequent hits deal damage
        slider.invulnerableTime = 0;
        float before = slider.getHealth();
        slider.hurt(source, 10);
        helper.assertTrue(slider.getHealth() < before, "stonebreaker shard should damage the awakened Slider, hp " + before + " -> " + slider.getHealth());
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void frostboundCrystalFreezesSunSpirit(GameTestHelper helper) {
        // Player at x=1 facing +X (yaw -90), Sun Spirit a few blocks east at the same height
        ServerPlayer player = casterAt(helper, 1.5, 2, 4.5, -90);
        SunSpirit spirit = helper.spawn(AetherEntityTypes.SUN_SPIRIT.get(), new BlockPos(6, 2, 4));
        spirit.setNoAi(true);
        helper.assertFalse(spirit.isFrozen(), "sun spirit starts unfrozen");

        // Control: Iron's Spells ice magic cannot hurt an unfrozen Sun Spirit without the optional data pack
        helper.assertTrue(spirit.isInvulnerableTo(SpellRegistry.ICICLE_SPELL.get().getDamageSource(player, player)),
                "unfrozen sun spirit should ignore ice magic by default");

        cast(ASSpells.FROSTBOUND_CRYSTAL.get(), 1, player);
        List<IceCrystal> crystals = helper.getLevel().getEntitiesOfClass(IceCrystal.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(10));
        helper.assertTrue(!crystals.isEmpty() && crystals.get(0).getOwner() == player, "crystal should be owned by the caster");
        helper.succeedWhen(() -> helper.assertTrue(spirit.isFrozen(), "sun spirit should be frozen by the crystal"));
    }

    @GameTest(template = EMPTY)
    public static void summonMoaMountsCaster(GameTestHelper helper) {
        ServerPlayer player = casterAt(helper, 4.5, 1, 4.5, 0);
        cast(ASSpells.SUMMON_MOA.get(), 2, player);
        helper.assertTrue(player.getVehicle() instanceof SummonedMoa, "caster should ride the summoned moa");
        SummonedMoa moa = (SummonedMoa) player.getVehicle();
        helper.assertTrue(moa.isSaddled(), "moa should be saddled");
        helper.assertTrue(moa.getMoaType() == AetherMoaTypes.WHITE.get(), "level 2 should summon a white moa");
        helper.assertTrue(moa.getMaxJumps() == 4, "white moa should have 4 jumps, got " + moa.getMaxJumps());
        helper.assertTrue(moa.getSummoner() == player, "moa should belong to the caster");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void summonFireMinionsAreAllied(GameTestHelper helper) {
        ServerPlayer player = casterAt(helper, 4.5, 1, 4.5, 0);
        cast(ASSpells.SUMMON_FIRE_MINION.get(), 3, player);
        List<SummonedFireMinion> minions = helper.getLevel().getEntitiesOfClass(SummonedFireMinion.class, player.getBoundingBox().inflate(6));
        helper.assertTrue(minions.size() == 2, "level 3 should summon 2 minions, got " + minions.size());
        for (SummonedFireMinion minion : minions) {
            helper.assertTrue(minion.getSummoner() == player && minion.isAlliedTo(player), "minion should be allied to its summoner");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void spellsAreAetherExclusiveByDefault(GameTestHelper helper) {
        for (var spell : ASSpells.SPELLS.getEntries()) {
            helper.assertFalse(spell.get().allowLooting(), spell.getId() + " should not appear in generic loot by default");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void goldDungeonRewardContainsSolarCodexAndScrolls(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootTable table = level.getServer().getLootData().getLootTable(ResourceLocation.fromNamespaceAndPath("aether", "chests/dungeon/gold/gold_dungeon_reward"));
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, helper.absoluteVec(Vec3.ZERO))
                .create(LootContextParamSets.CHEST);
        Set<AbstractSpell> goldSpells = Set.of(ASSpells.FROSTBOUND_CRYSTAL.get(), ASSpells.SUMMON_FIRE_MINION.get());
        int codex = 0;
        int scrolls = 0;
        for (int i = 0; i < 60; i++) {
            for (ItemStack stack : table.getRandomItems(params)) {
                if (stack.is(ASItems.SOLAR_CODEX.get())) {
                    codex++;
                }
                if (stack.is(ItemRegistry.SCROLL.get())) {
                    for (SpellSlot slot : ISpellContainer.get(stack).getActiveSpells()) {
                        if (goldSpells.contains(slot.getSpell())) {
                            scrolls++;
                        }
                    }
                }
            }
        }
        helper.assertTrue(codex > 0, "60 gold dungeon rewards should contain at least one Solar Codex");
        helper.assertTrue(scrolls > 0, "60 gold dungeon rewards should contain at least one gold-tier scroll");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void recipesAndOptionalPackAreRegistered(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        for (String recipe : List.of("ambrosium_ring", "zanite_focus_pendant")) {
            helper.assertTrue(server.getRecipeManager().byKey(AetherSpellbooks.id(recipe)).isPresent(), "recipe " + recipe + " should load");
        }
        String pack = AetherSpellbooks.MODID + ":" + com.aetherspellbooks.event.ASModEvents.ICE_MAGIC_IS_COLD_PACK;
        helper.assertTrue(server.getPackRepository().getAvailableIds().contains(pack), "optional pack should be available");
        helper.assertFalse(server.getPackRepository().getSelectedIds().contains(pack), "optional pack should be disabled by default");
        helper.succeed();
    }
}
