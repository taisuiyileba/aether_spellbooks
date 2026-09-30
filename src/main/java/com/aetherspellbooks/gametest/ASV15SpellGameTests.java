package com.aetherspellbooks.gametest;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.entity.IcestoneMeteor;
import com.aetherspellbooks.entity.RadiantJavelin;
import com.aetherspellbooks.entity.SpectralAerwhale;
import com.aetherspellbooks.entity.StormCloud;
import com.aetherspellbooks.registry.ASSpells;
import com.aetherspellbooks.spells.SolarFlareSpell;
import com.aetherspellbooks.spells.ValkyrieLungeSpell;
import com.mojang.authlib.GameProfile;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Behaviour tests for the v1.5 changes: the Solar Flare rebalance, the Valkyrie Lunge rework and the four new spells.
 * Development only.
 */
@GameTestHolder(AetherSpellbooks.MODID)
@PrefixGameTestTemplate(false)
public class ASV15SpellGameTests {
    private static final String EMPTY = "empty";

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

    @GameTest(template = EMPTY)
    public static void solarFlareDamageBudget(GameTestHelper helper) {
        ServerPlayer player = casterAt(helper, 4.5, 1, 4.5, 0);
        AbstractSpell flare = ASSpells.SOLAR_FLARE.get();
        for (int level = 1; level <= 3; level++) {
            helper.assertTrue(SolarFlareSpell.getDurationTicks(level) == (5 + 2 * level) * 20,
                    "level " + level + " should last " + (5 + 2 * level) + " seconds");
            float bolt = flare.getSpellPower(level, player);
            float total = bolt * SolarFlareSpell.getDurationTicks(level) / (float) SolarFlareSpell.getFireInterval(level);
            helper.assertTrue(total < 110, "level " + level + " should stay under ~110 total bolt damage, was " + total);
        }
        helper.assertTrue(flare.getSpellPower(3, player) <= 6.01f, "level 3 bolt damage should be 6");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void valkyrieLungeRecastBrandAndVerdict(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = casterAt(helper, 1.5, 1, 4.5, -90);
        Vec3 start = player.position();
        Husk inPath = husk(helper, 5, 4);
        // beside the start, outside the dash path but inside the verdict shockwave
        Husk beside = husk(helper, 1, 2);
        AbstractSpell lunge = ASSpells.VALKYRIE_LUNGE.get();
        float max = inPath.getHealth();

        cast(lunge, 1, player);
        float firstHit = max - inPath.getHealth();
        helper.assertTrue(firstHit > 0, "the first dash should strike the husk in its path");
        helper.assertTrue(ValkyrieLungeSpell.isBranded(helper.getLevel(), inPath), "a struck foe should be branded");
        helper.assertTrue(MagicData.getPlayerMagicData(player).getPlayerRecasts().hasRecastForSpell(lunge), "the dash should be recastable");
        helper.assertTrue(beside.getHealth() == beside.getMaxHealth(), "the first dash has no shockwave");

        helper.runAfterDelay(25, () -> {
            // dash through the same foe again (past its hurt cooldown): the brand is consumed for bonus damage
            player.teleportTo(start.x, start.y, start.z);
            player.setDeltaMovement(Vec3.ZERO);
            float before = inPath.getHealth();
            cast(lunge, 1, player);
            float secondHit = before - inPath.getHealth();
            helper.assertTrue(secondHit > firstHit * 1.4f, "the branded foe should take bonus damage: " + firstHit + " then " + secondHit);
            helper.assertFalse(ValkyrieLungeSpell.isBranded(helper.getLevel(), inPath), "the brand should be consumed");
            helper.runAfterDelay(20, () -> {
                helper.assertTrue(beside.getHealth() < beside.getMaxHealth(), "the last dash should end in a shockwave around the caster");
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void radiantJavelinPiercesAndErupts(GameTestHelper helper) {
        floor(helper);
        for (int y = 1; y < 5; y++) {
            for (int z = 0; z < 9; z++) {
                helper.setBlock(new BlockPos(8, y, z), Blocks.STONE);
            }
        }
        ServerPlayer player = casterAt(helper, 0.5, 1, 4.5, -90);
        player.setXRot(4);
        Husk near = husk(helper, 3, 4);
        Husk far = husk(helper, 5, 4);
        // next to the wall, out of the javelin's line: only the eruption reaches it
        Husk byWall = husk(helper, 7, 6);
        float max = near.getHealth();
        cast(ASSpells.RADIANT_JAVELIN.get(), 1, player);
        helper.succeedWhen(() -> {
            helper.assertTrue(near.getHealth() < max && far.getHealth() < max, "the javelin should pierce both husks in line");
            helper.assertTrue(byWall.getHealth() < max, "the eruption where it lands should strike nearby foes");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(RadiantJavelin.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16)).isEmpty(),
                    "the javelin should be gone after erupting");
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void thunderheadStrikesFoesBelow(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = casterAt(helper, 1.5, 1, 1.5, 0);
        Husk husk = husk(helper, 5, 5);
        float max = husk.getHealth();
        StormCloud cloud = new StormCloud(helper.getLevel(), player);
        cloud.setPos(husk.position().add(0, StormCloud.HOVER_HEIGHT, 0));
        cloud.setDamage(4);
        cloud.setStrikeInterval(10);
        cloud.setLifetime(200);
        helper.getLevel().addFreshEntity(cloud);
        helper.succeedWhen(() -> helper.assertTrue(husk.getHealth() < max, "the cloud should strike the husk beneath it"));
    }

    @GameTest(template = EMPTY)
    public static void aerwhaleSongHurtsFoesAndLiftsAllies(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = casterAt(helper, 3.5, 1, 4.5, -90);
        Husk husk = husk(helper, 5, 4);
        float max = husk.getHealth();
        SpectralAerwhale whale = new SpectralAerwhale(helper.getLevel(), player, new Vec3(1, 0, 0), 60);
        whale.setPos(helper.absoluteVec(new Vec3(4.5, 7, 4.5)));
        whale.configure(4, 4, 0);
        helper.getLevel().addFreshEntity(whale);
        whale.sing();
        helper.assertTrue(husk.getHealth() < max, "the song should hurt the husk below");
        helper.assertTrue(husk.getDeltaMovement().y > 0.4, "the song should toss the husk up");
        helper.assertTrue(player.hasEffect(MobEffects.SLOW_FALLING) && player.hasEffect(MobEffects.JUMP), "the caster below should be blessed");
        helper.assertFalse(husk.hasEffect(MobEffects.SLOW_FALLING), "foes should not be blessed");
        whale.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void icestoneMeteorShattersAndFreezesWater(GameTestHelper helper) {
        floor(helper);
        helper.setBlock(new BlockPos(6, 0, 4), Blocks.WATER);
        ServerPlayer player = casterAt(helper, 0.5, 1, 0.5, 0);
        Husk husk = husk(helper, 4, 4);
        float max = husk.getHealth();
        IcestoneMeteor meteor = new IcestoneMeteor(helper.getLevel(), player);
        meteor.setDamage(10);
        meteor.configure(3.5f, 100);
        meteor.setPos(husk.position());
        helper.getLevel().addFreshEntity(meteor);
        meteor.shatter(husk.position());
        helper.assertTrue(husk.getHealth() < max, "the blast should damage the husk");
        helper.assertTrue(husk.hasEffect(MobEffectRegistry.CHILLED), "the husk should be chilled");
        helper.assertTrue(husk.getTicksFrozen() > 0, "the husk should be frozen");
        helper.assertBlockPresent(Blocks.FROSTED_ICE, new BlockPos(6, 0, 4));
        helper.assertTrue(meteor.isRemoved(), "the meteor should be gone after shattering");
        helper.succeed();
    }
}
