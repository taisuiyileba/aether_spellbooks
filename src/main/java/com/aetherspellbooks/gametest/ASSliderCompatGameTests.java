package com.aetherspellbooks.gametest;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.config.ASConfig;
import com.aetherspellbooks.compat.SliderSpellCompat;
import com.aetherteam.aether.entity.AetherEntityTypes;
import com.aetherteam.aether.entity.monster.dungeon.boss.Slider;
import com.aetherteam.aether.event.hooks.AbilityHooks;
import com.aetherteam.aether.item.AetherItems;
import com.aetherteam.nitrogen.entity.BossRoomTracker;
import com.mojang.authlib.GameProfile;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.spells.magic_missile.MagicMissileProjectile;
import io.redspace.ironsspellbooks.entity.spells.thrown_item.ThrownItemProjectile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Integration tests use the dependency spells and the real Slider hurt/awakening path. */
@GameTestHolder(AetherSpellbooks.MODID)
@PrefixGameTestTemplate(false)
public class ASSliderCompatGameTests {
    private record Scene(ServerPlayer caster, Slider slider) {}

    private static Scene scene(GameTestHelper helper) {
        ServerPlayer caster = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "asb-slider"));
        Vec3 pos = helper.absoluteVec(new Vec3(1.5, 1, 4.5));
        caster.moveTo(pos.x, pos.y, pos.z, -90, 0);
        caster.setGameMode(GameType.SURVIVAL);
        caster.setNoGravity(true);
        caster.setInvulnerable(true);
        helper.getLevel().addNewPlayer(caster);
        Slider slider = helper.spawn(AetherEntityTypes.SLIDER.get(), new BlockPos(5, 1, 4));
        slider.setNoAi(true);
        slider.setNoGravity(true);
        aim(caster, slider.getBoundingBox().getCenter());
        return new Scene(caster, slider);
    }

    private static void aim(LivingEntity caster, Vec3 at) {
        Vec3 delta = at.subtract(caster.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()));
        caster.setYRot(yaw);
        caster.setYHeadRot(yaw);
        caster.setXRot(pitch);
    }

    private static void cast(AbstractSpell spell, ServerPlayer caster) {
        spell.onCast(caster.level(), 1, caster, CastSource.SPELLBOOK, MagicData.getPlayerMagicData(caster));
    }

    private static boolean throwHit(Scene scene, Item item) {
        ThrownItemProjectile thrown = new ThrownItemProjectile(scene.caster.level(), new ItemStack(item));
        thrown.setOwner(scene.caster);
        scene.slider.invulnerableTime = 0;
        return DamageSources.applyDamage(scene.slider, 10,
                SpellRegistry.THROW_SPELL.get().getDamageSource(thrown, scene.caster));
    }

    private static boolean hammerCanCast(Scene scene) {
        return SpellRegistry.SPECTRAL_HAMMER_SPELL.get().checkPreCastConditions(
                scene.caster.level(), 1, scene.caster, MagicData.getPlayerMagicData(scene.caster));
    }

    private static ThrownItemProjectile castThrow(Scene scene) {
        cast(SpellRegistry.THROW_SPELL.get(), scene.caster);
        return scene.caster.level().getEntitiesOfClass(ThrownItemProjectile.class,
                scene.caster.getBoundingBox().inflate(2), entity -> !entity.isRemoved() && entity.getOwner() == scene.caster)
                .get(0);
    }

    @GameTest(template = "empty", batch = "slider_compat")
    public static void thrownPickaxeMaterialChangesActualDamage(GameTestHelper helper) {
        Scene scene = scene(helper);
        helper.assertTrue(throwHit(scene, Items.WOODEN_PICKAXE), "wake boss before measuring damage");
        Item[] picks = {Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE};
        // Player base attack + pickaxe bonus + level-one Throw (1).
        float[] expected = {3, 4, 5, 6, 7};
        for (int i = 0; i < picks.length; i++) {
            scene.caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(picks[i]));
            ThrownItemProjectile thrown = castThrow(scene);
            helper.assertTrue(Math.abs(thrown.getDamage() - expected[i]) < 0.01,
                    "wrong cast damage for " + picks[i] + ": " + thrown.getDamage());
            scene.slider.invulnerableTime = 0;
            float health = scene.slider.getHealth();
            float resist = DamageSources.getResist(scene.slider, SpellRegistry.THROW_SPELL.get().getSchoolType());
            // The Aether also weakens non-Aether projectiles (tools_debuff). Keep that rule intact.
            float expectedLoss = AbilityHooks.WeaponHooks.reduceWeaponEffectiveness(scene.slider, thrown, expected[i] * resist);
            boolean hit = DamageSources.applyDamage(scene.slider, thrown.getDamage(),
                    SpellRegistry.THROW_SPELL.get().getDamageSource(thrown, scene.caster));
            helper.assertTrue(Math.abs(health - scene.slider.getHealth() - expectedLoss) < 0.01,
                    "actual Slider health loss must include " + picks[i] + " attack: expected=" + expected[i]
                            + ", after Aether debuff=" + expectedLoss + ", delta=" + (health - scene.slider.getHealth()) + ", hit=" + hit);
            thrown.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "slider_compat")
    public static void offhandThrownPickaxeUsesItsOwnEnchantmentsAndSnapshot(GameTestHelper helper) {
        Scene scene = scene(helper);
        scene.caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_SWORD));
        // Simulate already-applied mainhand equipment modifiers; they must not leak into the thrown pickaxe.
        scene.caster.getAttribute(Attributes.ATTACK_DAMAGE).addTransientModifier(
                scene.caster.getMainHandItem().getAttributeModifiers(EquipmentSlot.MAINHAND)
                        .get(Attributes.ATTACK_DAMAGE).iterator().next());
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe.enchant(Enchantments.SHARPNESS, 2);
        scene.caster.setItemInHand(InteractionHand.OFF_HAND, pickaxe);
        var data = MagicData.getPlayerMagicData(scene.caster);
        var spell = SpellRegistry.THROW_SPELL.get();
        data.getSyncedData(); // FakePlayer has no login handshake to initialize this lazily-created state.
        data.initiateCast(spell, 1, 5, CastSource.SPELLBOOK, SpellSelectionManager.OFFHAND);
        ThrownItemProjectile thrown = castThrow(scene);
        helper.assertTrue(Math.abs(thrown.getDamage() - 7.5f) < 0.01,
                "offhand diamond pickaxe should deal 1 spell + 5 attack + 1.5 Sharpness, got " + thrown.getDamage());
        helper.assertTrue(spell.getUniqueInfo(1, scene.caster).get(0).getString().contains("7.5"),
                "tooltip and actual projectile damage must agree");
        helper.assertTrue(scene.caster.getAttributeValue(Attributes.ATTACK_DAMAGE) == 8,
                "damage calculation must not modify the caster's real attributes");
        pickaxe.setCount(0);
        helper.assertTrue(thrown.getThrownItem().is(Items.DIAMOND_PICKAXE) && !thrown.getThrownItem().isEmpty(),
                "in-flight item must retain its own copy when the held stack changes");
        thrown.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "slider_compat")
    public static void thrownToolPreservesStrengthWithoutDoubleCountingWeapon(GameTestHelper helper) {
        Scene scene = scene(helper);
        scene.caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        scene.caster.getAttribute(Attributes.ATTACK_DAMAGE).addTransientModifier(
                scene.caster.getMainHandItem().getAttributeModifiers(EquipmentSlot.MAINHAND)
                        .get(Attributes.ATTACK_DAMAGE).iterator().next());
        scene.caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0));
        ThrownItemProjectile thrown = castThrow(scene);
        helper.assertTrue(Math.abs(thrown.getDamage() - 9) < 0.01,
                "damage must be 1 spell + 5 pickaxe attack + 3 Strength, got " + thrown.getDamage());
        thrown.discard();
        helper.succeed();
    }

    private static void wall(GameTestHelper helper) {
        for (int y = 1; y <= 5; y++) {
            for (int z = 2; z <= 6; z++) {
                helper.setBlock(new BlockPos(3, y, z), Blocks.BEDROCK);
            }
        }
    }

    private static void roomAroundSlider(Scene scene) {
        scene.slider.setDungeon(new BossRoomTracker<>(scene.slider, scene.slider.position(),
                scene.slider.getBoundingBox().inflate(1), new ArrayList<>()));
    }

    @GameTest(template = "empty", batch = "slider_compat")
    public static void thrownToolRulesAndOrdinarySpells(GameTestHelper helper) {
        Scene scene = scene(helper);
        scene.caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        for (Item item : List.of(Items.DIRT, Items.STICK, Items.IRON_SWORD, Items.IRON_AXE)) {
            helper.assertFalse(throwHit(scene, item), "non-mining thrown item must fail even while holding a pickaxe: " + item);
        }
        helper.assertTrue(throwHit(scene, Items.WOODEN_PICKAXE), "wooden pickaxe must wake the Slider");
        helper.assertTrue(scene.slider.isBossFight(), "accepted projectile must start the boss fight");
        helper.assertTrue(scene.slider.getHealth() == scene.slider.getMaxHealth(), "awakening must restore health");
        for (Item item : List.of(Items.DIAMOND_PICKAXE, AetherItems.ZANITE_PICKAXE.get())) {
            float before = scene.slider.getHealth();
            helper.assertTrue(throwHit(scene, item), "valid thrown mining tool must be accepted: " + item);
            helper.assertTrue(scene.slider.getHealth() < before, "accepted hit must reduce health");
        }
        MagicMissileProjectile missile = new MagicMissileProjectile(helper.getLevel(), scene.caster);
        scene.slider.invulnerableTime = 0;
        helper.assertFalse(DamageSources.applyDamage(scene.slider, 10,
                SpellRegistry.MAGIC_MISSILE_SPELL.get().getDamageSource(missile, scene.caster)), "magic missile must still fail");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "slider_compat", timeoutTicks = 40)
    public static void actualThrowSurvivesChangingHeldItem(GameTestHelper helper) {
        Scene scene = scene(helper);
        scene.caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        cast(SpellRegistry.THROW_SPELL.get(), scene.caster);
        scene.caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(scene.slider.isBossFight(), "real thrown pickaxe must wake the Slider after switching to dirt");
            scene.slider.invulnerableTime = 0;
            scene.caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
            cast(SpellRegistry.THROW_SPELL.get(), scene.caster);
            scene.caster.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        });
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(scene.slider.getHealth() < scene.slider.getMaxHealth(), "second real projectile must damage the awakened Slider");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slider_compat", timeoutTicks = 65)
    public static void hammerWakesThenDamagesExactlyOnce(GameTestHelper helper) {
        Scene scene = scene(helper);
        helper.assertTrue(hammerCanCast(scene), "hammer must cast directly on a Slider without a mineable block");
        cast(SpellRegistry.SPECTRAL_HAMMER_SPELL.get(), scene.caster);
        helper.runAtTickTime(8, () -> helper.assertFalse(scene.slider.isBossFight(), "hammer must wait for the impact frame"));
        helper.runAtTickTime(18, () -> {
            helper.assertTrue(scene.slider.isBossFight(), "first hammer must wake the Slider");
            helper.assertTrue(scene.slider.getHealth() == scene.slider.getMaxHealth(), "first hammer must preserve awakening heal");
        });
        helper.runAtTickTime(23, () -> {
            scene.slider.invulnerableTime = 0;
            cast(SpellRegistry.SPECTRAL_HAMMER_SPELL.get(), scene.caster);
        });
        float[] after = {0};
        helper.runAtTickTime(40, () -> {
            after[0] = scene.slider.getHealth();
            helper.assertTrue(Math.abs(after[0] - (scene.slider.getMaxHealth() - 6)) < 0.01,
                    "second level-one hammer should deal exactly six damage, health=" + after[0]);
            scene.slider.invulnerableTime = 0;
        });
        helper.runAtTickTime(56, () -> {
            helper.assertTrue(scene.slider.getHealth() == after[0], "hammer must never deal another hit on later ticks");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slider_compat")
    public static void hammerRespectsOcclusionRangeAndAdventure(GameTestHelper helper) {
        Scene scene = scene(helper);
        helper.assertTrue(hammerCanCast(scene), "control: visible Slider should be a valid target");
        scene.caster.setGameMode(GameType.ADVENTURE);
        helper.assertFalse(hammerCanCast(scene), "adventure-mode restriction must remain");
        scene.caster.setGameMode(GameType.SURVIVAL);
        Vec3 before = scene.caster.position();
        scene.caster.setPos(before.add(-20, 0, 0));
        aim(scene.caster, scene.slider.getBoundingBox().getCenter());
        // At this distance the ray can cross a neighbouring GameTest's blocks.
        // Those may legitimately allow the original mining spell to cast; the
        // Slider targeting branch itself must reject an out-of-range boss.
        helper.assertTrue(SliderSpellCompat.findHammerTarget(scene.caster) == null,
                "hammer must not target a Slider beyond sixteen blocks");
        scene.caster.setPos(before);
        aim(scene.caster, scene.slider.getBoundingBox().getCenter());
        wall(helper);
        helper.assertFalse(hammerCanCast(scene), "wall must block entity targeting");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "slider_compat")
    public static void nearerEntityBlocksHammerTargeting(GameTestHelper helper) {
        Scene scene = scene(helper);
        var blocker = helper.spawn(EntityType.COW, new BlockPos(3, 1, 4));
        blocker.setNoAi(true);
        blocker.setNoGravity(true);
        blocker.setPos(blocker.getX(), scene.caster.getEyeY() - 0.5, scene.slider.getZ());
        helper.assertFalse(hammerCanCast(scene), "a nearer animal must block the entity ray");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "slider_compat", timeoutTicks = 30)
    public static void hammerMissesMovedTarget(GameTestHelper helper) {
        Scene scene = scene(helper);
        cast(SpellRegistry.SPECTRAL_HAMMER_SPELL.get(), scene.caster);
        scene.slider.setPos(scene.slider.position().add(0, 0, 5));
        helper.runAtTickTime(20, () -> {
            helper.assertFalse(scene.slider.isBossFight(), "Slider moving out of the impact area must evade the hammer");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slider_compat", timeoutTicks = 30)
    public static void hammerRechecksWallAtImpact(GameTestHelper helper) {
        Scene scene = scene(helper);
        cast(SpellRegistry.SPECTRAL_HAMMER_SPELL.get(), scene.caster);
        wall(helper);
        helper.runAtTickTime(20, () -> {
            helper.assertFalse(scene.slider.isBossFight(), "a wall added before impact must block the hammer");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slider_compat", timeoutTicks = 30)
    public static void compatibilityPreservesDungeonBoundary(GameTestHelper helper) {
        Scene scene = scene(helper);
        // Queue a valid strike, then put the caster outside the boss room before it lands.
        cast(SpellRegistry.SPECTRAL_HAMMER_SPELL.get(), scene.caster);
        roomAroundSlider(scene);
        helper.assertFalse(scene.slider.getDungeon().isPlayerWithinRoomInterior(scene.caster), "test caster must be outside");
        helper.assertFalse(throwHit(scene, Items.DIAMOND_PICKAXE), "thrown tools must not bypass dungeon bounds");
        helper.assertFalse(hammerCanCast(scene), "hammer must not cast from outside the room");
        helper.runAtTickTime(20, () -> {
            helper.assertFalse(scene.slider.isBossFight(), "hammer must recheck room bounds when it lands");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slider_compat")
    public static void peacefulAndConfigToggles(GameTestHelper helper) {
        Scene scene = scene(helper);
        Difficulty previous = helper.getLevel().getDifficulty();
        boolean thrownEnabled = ASConfig.THROWN_TOOLS_DAMAGE_SLIDER.get();
        boolean hammerEnabled = ASConfig.SPECTRAL_HAMMER_DAMAGE_SLIDER.get();
        try {
            ASConfig.THROWN_TOOLS_DAMAGE_SLIDER.set(false);
            helper.assertFalse(throwHit(scene, Items.DIAMOND_PICKAXE), "disabling thrown-tool compatibility must restore rejection");
            ASConfig.SPECTRAL_HAMMER_DAMAGE_SLIDER.set(false);
            helper.assertFalse(hammerCanCast(scene), "disabling hammer compatibility must restore block-only casting");
            ASConfig.THROWN_TOOLS_DAMAGE_SLIDER.set(true);
            ASConfig.SPECTRAL_HAMMER_DAMAGE_SLIDER.set(true);
            helper.getLevel().getServer().setDifficulty(Difficulty.PEACEFUL, true);
            helper.assertFalse(throwHit(scene, Items.DIAMOND_PICKAXE), "peaceful must still reject thrown tools");
            helper.assertFalse(hammerCanCast(scene), "peaceful must reject the hammer's Slider branch");
        } finally {
            helper.getLevel().getServer().setDifficulty(previous, true);
            ASConfig.THROWN_TOOLS_DAMAGE_SLIDER.set(thrownEnabled);
            ASConfig.SPECTRAL_HAMMER_DAMAGE_SLIDER.set(hammerEnabled);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "slider_compat", timeoutTicks = 30)
    public static void originalHammerStillMinesBlocks(GameTestHelper helper) {
        Scene scene = scene(helper);
        scene.slider.discard();
        BlockPos stone = new BlockPos(5, 2, 4);
        helper.setBlock(stone, Blocks.STONE);
        aim(scene.caster, Vec3.atCenterOf(helper.absolutePos(stone)));
        helper.assertTrue(hammerCanCast(scene), "ordinary mining must remain castable");
        cast(SpellRegistry.SPECTRAL_HAMMER_SPELL.get(), scene.caster);
        helper.runAtTickTime(20, () -> {
            helper.assertBlockNotPresent(Blocks.STONE, stone);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slider_compat", timeoutTicks = 30)
    public static void sliderHammerDoesNotMineNearbyBlocks(GameTestHelper helper) {
        Scene scene = scene(helper);
        BlockPos stone = new BlockPos(5, 0, 4);
        helper.setBlock(stone, Blocks.STONE);
        cast(SpellRegistry.SPECTRAL_HAMMER_SPELL.get(), scene.caster);
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(scene.slider.isBossFight(), "control: combat hammer should connect");
            helper.assertBlockPresent(Blocks.STONE, stone);
            helper.succeed();
        });
    }
}
