package com.aetherspellbooks.gametest;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.item.weapon.AetherSpellbladeItem;
import com.aetherspellbooks.item.weapon.AetherStaffItem;
import com.aetherspellbooks.registry.ASItems;
import com.aetherspellbooks.registry.ASSpells;
import com.aetherteam.aether.attachment.AetherPlayerAttachment;
import com.aetherteam.aether.item.AetherItems;
import com.mojang.authlib.GameProfile;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.Map;
import java.util.UUID;

/**
 * Tests for the spellblades and staffs. Mobs stand in for the wielder: for them every hit counts as fully charged.
 * Development only.
 */
@GameTestHolder(AetherSpellbooks.MODID)
@PrefixGameTestTemplate(false)
public class ASWeaponGameTests {
    private static final String EMPTY = "empty";

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static <T extends net.minecraft.world.entity.Mob> T mob(GameTestHelper helper, EntityType<T> type, int x, int z) {
        T mob = helper.spawn(type, new BlockPos(x, 1, z));
        mob.setPersistenceRequired();
        mob.setNoAi(true);
        return mob;
    }

    private static Husk wielder(GameTestHelper helper, DeferredHolder<Item, Item> weapon, int x, int z) {
        Husk husk = mob(helper, EntityType.HUSK, x, z);
        husk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon.get()));
        return husk;
    }

    @GameTest(template = EMPTY)
    public static void sunfireSpellbladeSetsTargetsAblaze(GameTestHelper helper) {
        floor(helper);
        Husk attacker = wielder(helper, ASItems.SUNFIRE_SPELLBLADE, 2, 4);
        Pig target = mob(helper, EntityType.PIG, 4, 4);
        attacker.doHurtTarget(target);
        helper.assertTrue(target.getRemainingFireTicks() > 20 * 25, "the target should burn for ~30 s, ticks=" + target.getRemainingFireTicks());
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void hallowedSpellbladeSmitesTheUndead(GameTestHelper helper) {
        floor(helper);
        Husk attacker = wielder(helper, ASItems.HALLOWED_SPELLBLADE, 4, 2);
        Zombie undead = mob(helper, EntityType.ZOMBIE, 2, 5);
        Pig living = mob(helper, EntityType.PIG, 6, 5);
        attacker.doHurtTarget(undead);
        attacker.doHurtTarget(living);
        float undeadLoss = undead.getMaxHealth() - undead.getHealth();
        float livingLoss = living.getMaxHealth() - living.getHealth();
        helper.assertTrue(undeadLoss >= livingLoss + AetherSpellbladeItem.HOLY_BONUS - 2.5f,
                "undead should take the holy bonus: undead lost " + undeadLoss + ", pig lost " + livingLoss);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void stormcallerAndSanguineAbilities(GameTestHelper helper) {
        floor(helper);
        Husk storm = wielder(helper, ASItems.STORMCALLER_SPELLBLADE, 1, 1);
        Pig target = mob(helper, EntityType.PIG, 7, 7);
        storm.getMainHandItem().getItem().hurtEnemy(storm.getMainHandItem(), target, storm);
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(LightningBolt.class, new AABB(target.blockPosition()).inflate(2)).isEmpty(),
                "a charged stormcaller hit should call lightning on the target");

        Husk vampire = wielder(helper, ASItems.SANGUINE_SPELLBLADE, 1, 7);
        vampire.setHealth(10);
        Pig prey = mob(helper, EntityType.PIG, 3, 7);
        vampire.getMainHandItem().getItem().hurtEnemy(vampire.getMainHandItem(), prey, vampire);
        helper.assertTrue(vampire.getHealth() > 10, "a sanguine hit should heal the wielder");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void spellbladesCarryTheirSpells(GameTestHelper helper) {
        Map<DeferredHolder<Item, Item>, DeferredHolder<io.redspace.ironsspellbooks.api.spells.AbstractSpell, io.redspace.ironsspellbooks.api.spells.AbstractSpell>> expected = Map.of(
                ASItems.SUNFIRE_SPELLBLADE, ASSpells.SOLAR_FLARE, ASItems.STORMCALLER_SPELLBLADE, ASSpells.THUNDER_CRYSTAL,
                ASItems.HALLOWED_SPELLBLADE, ASSpells.VALKYRIE_LUNGE);
        expected.forEach((item, spell) -> {
            ItemStack stack = new ItemStack(item.get());
            ((AetherSpellbladeItem) stack.getItem()).initializeSpellContainer(stack);
            helper.assertTrue(ISpellContainer.isSpellContainer(stack), item.getId() + " should hold its spell");
            helper.assertTrue(ISpellContainer.get(stack).getSpellAtIndex(0).getSpell() == spell.get(), item.getId() + " should hold " + spell.getId());
        });
        ItemStack blade = new ItemStack(ASItems.STORMCALLER_SPELLBLADE.get());
        double lightning = blade.getAttributeModifiers().modifiers().stream().filter(e -> e.slot().test(EquipmentSlot.MAINHAND) && e.attribute().equals(AttributeRegistry.LIGHTNING_SPELL_POWER)).mapToDouble(e -> e.modifier().amount()).sum();
        helper.assertTrue(Math.abs(lightning - 0.10) < 1e-6, "stormcaller should add 10% lightning power, got " + lightning);
        ItemStack scepter = new ItemStack(ASItems.SOLAR_SCEPTER.get());
        double fire = scepter.getAttributeModifiers().modifiers().stream().filter(e -> e.slot().test(EquipmentSlot.MAINHAND) && e.attribute().equals(AttributeRegistry.FIRE_SPELL_POWER)).mapToDouble(e -> e.modifier().amount()).sum();
        helper.assertTrue(Math.abs(fire - 0.15) < 1e-6, "solar scepter should add 15% fire power, got " + fire);
        helper.assertTrue(scepter.getAttributeModifiers().modifiers().stream().noneMatch(e -> e.slot().test(EquipmentSlot.OFFHAND)), "staff stats only apply in the main hand");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void gravititeStaffLaunchesTargets(GameTestHelper helper) {
        floor(helper);
        Husk attacker = wielder(helper, ASItems.GRAVITITE_STAFF, 2, 4);
        Pig target = helper.spawn(EntityType.PIG, new BlockPos(4, 1, 4));   // needs gravity to stand on the ground
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(target.onGround(), "the pig should be standing");
            attacker.getMainHandItem().getItem().hurtEnemy(attacker.getMainHandItem(), target, attacker);
            helper.assertTrue(target.getDeltaMovement().y > 0.5, "a gravitite staff hit should launch the target, v=" + target.getDeltaMovement());
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void aercloudStaffSummonsCloudMinions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "aether-spellbooks-staff-test"));
        Vec3 pos = helper.absoluteVec(new Vec3(4.5, 2, 4.5));
        player.moveTo(pos.x, pos.y, pos.z, 0, 0);
        level.addNewPlayer(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ASItems.AERCLOUD_STAFF.get()));
        AetherPlayerAttachment aetherPlayer = java.util.Optional.of(player.getData(com.aetherteam.aether.attachment.AetherDataAttachments.AETHER_PLAYER)).orElseThrow(IllegalStateException::new);
        AetherStaffItem.summonOrDismissMinions(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(aetherPlayer.getCloudMinions().size() == 2, "sneak-using the aercloud staff should summon two cloud minions");
        AetherStaffItem.summonOrDismissMinions(player, InteractionHand.MAIN_HAND);
        aetherPlayer.getCloudMinions().forEach(Entity::discard);
        level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void weaponUpgradesOnTheSmithingTable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Map<Item, Item[]> upgrades = Map.of(
                AetherItems.FLAMING_SWORD.get(), new Item[]{ItemRegistry.FIRE_RUNE.get(), ASItems.SUNFIRE_SPELLBLADE.get()},
                AetherItems.LIGHTNING_SWORD.get(), new Item[]{ItemRegistry.LIGHTNING_RUNE.get(), ASItems.STORMCALLER_SPELLBLADE.get()},
                AetherItems.HOLY_SWORD.get(), new Item[]{ItemRegistry.HOLY_RUNE.get(), ASItems.HALLOWED_SPELLBLADE.get()},
                AetherItems.VAMPIRE_BLADE.get(), new Item[]{ItemRegistry.BLOOD_RUNE.get(), ASItems.SANGUINE_SPELLBLADE.get()},
                AetherItems.CLOUD_STAFF.get(), new Item[]{ItemRegistry.EVOCATION_RUNE.get(), ASItems.AERCLOUD_STAFF.get()});
        upgrades.forEach((base, runeAndResult) -> {
            net.minecraft.world.item.crafting.SmithingRecipeInput table = new net.minecraft.world.item.crafting.SmithingRecipeInput(new ItemStack(ASItems.ARCANE_UPGRADE_TEMPLATE.get()), new ItemStack(base), new ItemStack(runeAndResult[0]));
            var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, table, level);
            helper.assertTrue(recipe.isPresent(), "no smithing upgrade for " + base);
            helper.assertTrue(recipe.get().value().assemble(table, level.registryAccess()).is(runeAndResult[1]), "wrong upgrade result for " + base);
        });
        for (String id : new String[]{"zanite_staff", "gravitite_staff"}) {
            helper.assertTrue(level.getRecipeManager().byKey(AetherSpellbooks.id(id)).isPresent(), "missing recipe " + id);
        }
        helper.succeed();
    }
}
