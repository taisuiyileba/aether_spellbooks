package com.aetherspellbooks.gametest;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.entity.AechorFlower;
import com.aetherspellbooks.entity.AetherWhirlwind;
import com.aetherspellbooks.entity.SolarBoltProjectile;
import com.aetherspellbooks.entity.SolarOrb;
import com.aetherspellbooks.entity.StonebreakerShard;
import com.aetherspellbooks.entity.ThunderCrystalProjectile;
import com.aetherspellbooks.entity.ZephyrOrbProjectile;
import com.aetherspellbooks.registry.ASSpells;
import com.aetherspellbooks.util.CloudSentinels;
import com.aetherteam.aether.effect.AetherEffects;
import com.aetherteam.aether.entity.miscellaneous.CloudMinion;
import com.mojang.authlib.GameProfile;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Behaviour tests for the v1.1 spells and the reworked v1.0 spells. Development only.
 */
@GameTestHolder(AetherSpellbooks.MODID)
@PrefixGameTestTemplate(false)
public class ASSpellGameTests {
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

    /** A floor so mobs stand still on solid ground; husks do not burn in daylight. */
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

    @GameTest(template = EMPTY, batch = "nature_spells")
    public static void thornFanClimbsStepsButStopsAtWalls(GameTestHelper helper) {
        floor(helper);
        for (int z = 0; z < 9; z++) {
            for (int x = 4; x < 9; x++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
            helper.setBlock(new BlockPos(7, 2, z), Blocks.STONE);
            helper.setBlock(new BlockPos(7, 3, z), Blocks.STONE);
        }
        ServerPlayer player = casterAt(helper, 1.5, 1, 4.5, -90);
        Husk near = husk(helper, 2, 4);
        Husk uphill = helper.spawn(EntityType.HUSK, new BlockPos(5, 2, 5));
        Husk sheltered = helper.spawn(EntityType.HUSK, new BlockPos(8, 2, 4));
        for (Husk target : List.of(near, uphill, sheltered)) {
            target.setNoAi(true);
        }
        cast(ASSpells.THORN_SPIKES.get(), 3, player);
        float afterFirst = near.getHealth();
        helper.assertTrue(afterFirst < near.getMaxHealth(), "First row should hit immediately");
        helper.assertTrue(uphill.getHealth() == uphill.getMaxHealth(), "Distant thorns should arrive later");
        helper.assertTrue(near.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "Briars should slow a struck foe");
        near.invulnerableTime = 0;
        helper.runAfterDelay(22, () -> {
            helper.assertTrue(near.getHealth() == afterFirst, "Overlapping thorns should not stack damage");
            helper.assertTrue(uphill.getHealth() < uphill.getMaxHealth(), "Side row should climb the step");
            helper.assertTrue(sheltered.getHealth() == sheltered.getMaxHealth(), "Wall should stop the row");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "nature_spells")
    public static void thornsStopAtLedge(GameTestHelper helper) {
        for (int z = 0; z < 9; z++) {
            for (int x = 0; x < 9; x++) {
                if (x != 4) {
                    helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
                }
            }
        }
        ServerPlayer player = casterAt(helper, 1.5, 5, 4.5, -90);
        Husk across = helper.spawn(EntityType.HUSK, new BlockPos(6, 5, 4));
        across.setNoAi(true);
        cast(ASSpells.THORN_SPIKES.get(), 3, player);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(across.getHealth() == across.getMaxHealth(), "Rows must terminate at a gap");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "nature_aim")
    public static void overheadSpellsMoveWithTheirCaster(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = casterAt(helper, 1.5, 1, 1.5, 0);
        cast(ASSpells.AECHOR_FLOWER.get(), 1, player);
        cast(ASSpells.SOLAR_FLARE.get(), 1, player);
        var area = player.getBoundingBox().inflate(6);
        var sun = helper.getLevel().getEntitiesOfClass(SolarOrb.class, area, e -> e.getOwner() == player).get(0);
        var flower = helper.getLevel().getEntitiesOfClass(AechorFlower.class, area, e -> e.getOwner() == player).get(0);
        helper.setBlock(new BlockPos(7, 3, 6), Blocks.STONE);
        Vec3 end = helper.absoluteVec(new Vec3(7.5, 1, 6.5));
        player.moveTo(end.x, end.y, end.z, 90, -90);
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(Math.abs(sun.getX() - player.getX() - 0.6) < 0.001
                    && Math.abs(sun.getZ() - player.getZ()) < 0.001, "Sun must reach its firing position in one tick, even looking straight up");
            helper.assertTrue(flower.position().subtract(player.position()).horizontalDistanceSqr() < 0.000001,
                    "Flower must follow a teleport immediately");
            helper.assertTrue(Math.abs(flower.getY() - (end.y + 1.75)) < 0.001, "Flower should fit below the new ceiling");
            flower.discard();
            sun.discard();
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "nature_spells", timeoutTicks = 200)
    public static void flowerGuardsCasterAndExpires(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = casterAt(helper, 2.5, 1, 4.5, -90);
        Husk enemy = husk(helper, 6, 4);
        enemy.setNoAi(true);
        var cow = helper.spawn(EntityType.COW, new BlockPos(3, 1, 4));
        cow.setNoAi(true);
        for (int y = 1; y < 5; y++) {
            helper.setBlock(new BlockPos(5, y, 4), Blocks.STONE);
        }
        var spell = ASSpells.AECHOR_FLOWER.get();
        cast(spell, 1, player);
        cast(spell, 1, player);
        var flowers = helper.getLevel().getEntitiesOfClass(AechorFlower.class, player.getBoundingBox().inflate(8),
                f -> f.getOwner() == player);
        helper.assertTrue(flowers.size() == 1, "Recasting should replace this caster's flower");
        var flower = flowers.get(0);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(enemy.getHealth() == enemy.getMaxHealth(), "Flower must not shoot through walls");
            for (int y = 1; y < 5; y++) {
                helper.setBlock(new BlockPos(5, y, 4), Blocks.AIR);
            }
            player.teleportTo(player.getX() + 1, player.getY(), player.getZ());
        });
        helper.runAfterDelay(38, () -> {
            helper.assertTrue(flower.position().subtract(player.position()).horizontalDistanceSqr() < 0.1,
                    "Flower should follow the caster");
            helper.assertTrue(enemy.getHealth() < enemy.getMaxHealth() && enemy.hasEffect(AetherEffects.INEBRIATION.get()),
                    "Needles should damage and briefly poison a visible foe");
            helper.assertTrue(cow.getHealth() == cow.getMaxHealth(), "A passing animal must be spared");
        });
        helper.runAfterDelay(165, () -> {
            helper.assertTrue(flower.isRemoved(), "Level 1 flower should expire after eight seconds");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "nature_aim", timeoutTicks = 75)
    public static void flowerLocksEachNeedleSeparately(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = casterAt(helper, 4.5, 1, 6.5, 177);
        Husk enemy = husk(helper, 4, 2);
        enemy.setNoAi(true);
        enemy.teleportTo(player.getX() - 0.45, player.getY(), player.getZ() - 4);
        cast(ASSpells.AECHOR_FLOWER.get(), 1, player);
        AechorFlower flower = helper.getLevel().getEntitiesOfClass(AechorFlower.class,
                player.getBoundingBox().inflate(4), f -> f.getOwner() == player).get(0);
        helper.onEachTick(() -> {
            int age = (int) flower.volleyAge(0);
            if (age == 2) {
                enemy.teleportTo(player.getX() + 0.45, player.getY(), player.getZ() - 4);
            } else if (age == 6) {
                float expected = (float) Math.toDegrees(-Math.atan2(0.45, -4));
                helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(flower.getYRot() - expected)) < 3,
                        "Turning across the yaw seam must take the short arc");
            } else if (age == AechorFlower.FIRST_SHOT - 1) {
                // Dodge after the first heading is locked, but stay in range for the follow-up.
                enemy.teleportTo(player.getX() + 3.4, player.getY(), player.getZ() - 3);
            } else if (age == AechorFlower.FIRST_SHOT + 7) {
                helper.assertTrue(enemy.getHealth() == enemy.getMaxHealth(), "First needle should miss the late dodge");
            } else if (age == AechorFlower.SECOND_SHOT + 8) {
                helper.assertTrue(enemy.getHealth() < enemy.getMaxHealth(), "Second needle should reacquire the moved target");
                helper.succeed();
            }
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void gravititeSurgeLiftsThenSlams(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = casterAt(helper, 2.5, 1, 4.5, -90);
        Husk husk = husk(helper, 4, 4);
        float max = husk.getHealth();
        cast(ASSpells.GRAVITITE_SURGE.get(), 1, player);
        helper.assertTrue(husk.getDeltaMovement().y > 0.5, "target should be flung upward, vy=" + husk.getDeltaMovement().y);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(husk.getHealth() < max, "target should be damaged by the slam");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void valkyrieLungeStrikesThePath(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = casterAt(helper, 1.5, 1, 4.5, -90);
        Husk husk = husk(helper, 5, 4);
        float max = husk.getHealth();
        cast(ASSpells.VALKYRIE_LUNGE.get(), 1, player);
        helper.assertTrue(husk.getHealth() < max, "enemy in the dash path should be struck");
        helper.assertTrue(player.getDeltaMovement().horizontalDistance() > 0.5, "caster should dash forward");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void zephyrBlastKnocksBack(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = casterAt(helper, 1.5, 1, 4.5, -90);
        Husk husk = husk(helper, 5, 4);
        float max = husk.getHealth();
        ZephyrOrbProjectile orb = new ZephyrOrbProjectile(helper.getLevel(), player);
        orb.setDamage(3);
        orb.configure(3f, 1.5f);
        orb.detonate(husk.position().subtract(1, 0, 0));
        helper.assertTrue(husk.getHealth() < max, "blast should damage");
        helper.assertTrue(husk.getDeltaMovement().x > 0.5, "blast should push the husk away (+x), v=" + husk.getDeltaMovement());
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void whirlwindLiftsAndDamages(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = casterAt(helper, 1.5, 1, 1.5, 0);
        Husk husk = husk(helper, 5, 5);
        float max = husk.getHealth();
        AetherWhirlwind whirlwind = new AetherWhirlwind(helper.getLevel(), player, new Vec3(0, 0, 1));
        whirlwind.setPos(helper.absoluteVec(new Vec3(5.5, 1, 5.5)));
        whirlwind.setRadius(3f);
        whirlwind.setDamage(2f);
        whirlwind.setLifetime(200);
        helper.getLevel().addFreshEntity(whirlwind);
        double startY = husk.getY();
        helper.succeedWhen(() -> {
            helper.assertTrue(husk.getY() > startY + 0.5, "husk should be lifted by the whirlwind");
            helper.assertTrue(husk.getHealth() < max, "husk should be buffeted");
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void solarFlareRainsBolts(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = casterAt(helper, 1.5, 1, 4.5, -90);
        Husk husk = husk(helper, 6, 4);
        float max = husk.getHealth();
        cast(ASSpells.SOLAR_FLARE.get(), 3, player);
        helper.succeedWhen(() -> helper.assertTrue(husk.getHealth() < max || husk.isOnFire(),
                "the miniature sun should shoot the husk (bolts in flight: "
                        + helper.getLevel().getEntitiesOfClass(SolarBoltProjectile.class, new AABB(husk.blockPosition()).inflate(16)).size() + ")"));
    }

    @GameTest(template = EMPTY)
    public static void cloudSentinelsSummonTwoMinions(GameTestHelper helper) {
        ServerPlayer player = casterAt(helper, 4.5, 1, 4.5, 0);
        cast(ASSpells.CLOUD_SENTINELS.get(), 2, player);
        List<CloudMinion> minions = helper.getLevel().getEntitiesOfClass(CloudMinion.class, player.getBoundingBox().inflate(8), m -> m.getOwner() == player);
        helper.assertTrue(minions.size() == 2, "two cloud minions expected, got " + minions.size());
        helper.assertTrue(CloudSentinels.getSentinels(player).size() == 2, "sentinels should be tracked");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void stonebreakerVolleyAtHighLevel(GameTestHelper helper) {
        ServerPlayer player = casterAt(helper, 1.5, 2, 4.5, -90);
        cast(ASSpells.STONEBREAKER_SHARD.get(), 9, player);
        List<StonebreakerShard> shards = helper.getLevel().getEntitiesOfClass(StonebreakerShard.class, player.getBoundingBox().inflate(4));
        helper.assertTrue(shards.size() == 3, "level 9 should fire 3 shards, got " + shards.size());
        helper.assertTrue(shards.stream().allMatch(s -> s.getPierceLevel() == 1), "level 9 shards should pierce");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void thunderCrystalArcsToNearbyEnemies(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = casterAt(helper, 1.5, 1, 1.5, 0);
        Husk first = husk(helper, 2, 6);
        Husk second = husk(helper, 5, 6);
        Husk third = husk(helper, 8, 6);
        float max = first.getHealth();
        ThunderCrystalProjectile crystal = new ThunderCrystalProjectile(helper.getLevel(), player);
        crystal.setDamage(4);
        crystal.setChains(2);
        crystal.detonate(first.getBoundingBox().getCenter(), first);
        for (Mob mob : List.of(first, second, third)) {
            helper.assertTrue(mob.getHealth() < max, "every husk in the chain should be struck");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void frostboundCrystalFollowsPitch(GameTestHelper helper) {
        ServerPlayer player = casterAt(helper, 4.5, 3, 4.5, 0);
        player.setXRot(35);   // looking down
        cast(ASSpells.FROSTBOUND_CRYSTAL.get(), 1, player);
        var crystals = helper.getLevel().getEntitiesOfClass(com.aetherteam.aether.entity.projectile.crystal.IceCrystal.class, player.getBoundingBox().inflate(4));
        helper.assertTrue(!crystals.isEmpty(), "a crystal should be conjured");
        Vec3 v = crystals.get(0).getDeltaMovement();
        helper.assertTrue(v.y < -0.5, "crystal should fly downward along the crosshair, v=" + v);
        helper.assertTrue(Math.abs(v.normalize().dot(player.getLookAngle()) - 1) < 0.01, "crystal should follow the look vector");
        helper.succeed();
    }
}
