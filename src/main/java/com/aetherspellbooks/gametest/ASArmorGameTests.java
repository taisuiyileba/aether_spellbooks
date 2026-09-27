package com.aetherspellbooks.gametest;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.armor.PhoenixMageSet;
import com.aetherspellbooks.armor.ValkyrieMageSet;
import com.aetherspellbooks.registry.ASArmorMaterials;
import com.aetherspellbooks.registry.ASItems;
import com.aetherteam.aether.capability.player.AetherPlayer;
import com.aetherteam.aether.item.AetherItems;
import com.mojang.authlib.GameProfile;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.UUID;

/**
 * Tests for the Valkyrie and Phoenix mage armour. Development only.
 */
@GameTestHolder(AetherSpellbooks.MODID)
@PrefixGameTestTemplate(false)
public class ASArmorGameTests {
    private static final String EMPTY = "empty";
    private static final List<RegistryObject<Item>> PHOENIX = List.of(ASItems.PHOENIX_MAGE_HELMET, ASItems.PHOENIX_MAGE_CHESTPLATE, ASItems.PHOENIX_MAGE_LEGGINGS, ASItems.PHOENIX_MAGE_BOOTS);
    private static final List<RegistryObject<Item>> VALKYRIE = List.of(ASItems.VALKYRIE_MAGE_HELMET, ASItems.VALKYRIE_MAGE_CHESTPLATE, ASItems.VALKYRIE_MAGE_LEGGINGS, ASItems.VALKYRIE_MAGE_BOOTS);

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static void equip(LivingEntity entity, List<RegistryObject<Item>> set) {
        for (int i = 0; i < 4; i++) {
            entity.setItemSlot(ASArmorMaterials.ARMOR_SLOTS[i], new ItemStack(set.get(i).get()));
        }
    }

    private static Husk husk(GameTestHelper helper, int x, int z) {
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(x, 1, z));
        husk.setPersistenceRequired();
        return husk;
    }

    @GameTest(template = EMPTY)
    public static void phoenixMageSetShrugsOffFire(GameTestHelper helper) {
        floor(helper);
        Husk wearer = husk(helper, 2, 4);
        Husk bare = husk(helper, 6, 4);
        equip(wearer, PHOENIX);
        helper.assertTrue(PhoenixMageSet.isWornBy(wearer), "full set should be detected");
        wearer.hurt(wearer.damageSources().lava(), 6);
        wearer.hurt(wearer.damageSources().inFire(), 6);
        bare.hurt(bare.damageSources().lava(), 6);
        helper.assertTrue(wearer.getHealth() == wearer.getMaxHealth(), "phoenix mage set should block fire and lava");
        helper.assertTrue(bare.getHealth() < bare.getMaxHealth(), "a husk without the set should burn");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void phoenixMageRebirthOnceThenCooldown(GameTestHelper helper) {
        floor(helper);
        Husk wearer = husk(helper, 4, 4);
        Husk bystander = husk(helper, 6, 4);
        equip(wearer, PHOENIX);
        wearer.hurt(wearer.damageSources().generic(), 1000);
        helper.assertTrue(wearer.isAlive(), "the first fatal blow should trigger Rebirth");
        helper.assertTrue(Math.abs(wearer.getHealth() - wearer.getMaxHealth() * PhoenixMageSet.REBIRTH_HEALTH) < 0.01f, "Rebirth should restore 40% health, got " + wearer.getHealth());
        helper.assertTrue(bystander.isOnFire(), "the rebirth flare should set nearby enemies alight");
        wearer.invulnerableTime = 0;
        wearer.hurt(wearer.damageSources().generic(), 1000);
        helper.assertTrue(wearer.isDeadOrDying(), "Rebirth should be on cooldown for the second blow");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void valkyrieMageSetIgnoresFalls(GameTestHelper helper) {
        floor(helper);
        Husk wearer = husk(helper, 4, 4);
        equip(wearer, VALKYRIE);
        wearer.causeFallDamage(20, 1, wearer.damageSources().fall());
        helper.assertTrue(wearer.getHealth() == wearer.getMaxHealth(), "valkyrie mage set should prevent fall damage");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void valkyrieMageFlightLiftsTheWearer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "aether-spellbooks-armor-test"));
        Vec3 pos = helper.absoluteVec(new Vec3(4.5, 5, 4.5));
        player.moveTo(pos.x, pos.y, pos.z, 0, 0);
        level.addNewPlayer(player);
        equip(player, VALKYRIE);
        player.setOnGround(false);
        player.setDeltaMovement(0, -0.3, 0);
        AetherPlayer aetherPlayer = AetherPlayer.get(player).orElseThrow(IllegalStateException::new);
        aetherPlayer.setJumping(true);
        for (int i = 0; i < 6; i++) {
            ValkyrieMageSet.tick(player, ValkyrieMageSet.isWornBy(player));
        }
        helper.assertTrue(player.getDeltaMovement().y > 0, "holding jump in the air should lift the wearer, v=" + player.getDeltaMovement());
        var castTime = player.getAttribute(AttributeRegistry.CAST_TIME_REDUCTION.get());
        helper.assertTrue(castTime != null && castTime.getModifier(ValkyrieMageSet.GRACE_ID) != null, "Valkyrie's Grace should apply while airborne");
        player.setOnGround(true);
        ValkyrieMageSet.tick(player, true);
        helper.assertTrue(castTime.getModifier(ValkyrieMageSet.GRACE_ID) == null, "Valkyrie's Grace should end on landing");
        aetherPlayer.setJumping(false);
        level.removePlayerImmediately(player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        helper.succeed();
    }

    private static double modifier(ItemStack stack, EquipmentSlot slot, Attribute attribute) {
        return stack.getAttributeModifiers(slot).get(attribute).stream().mapToDouble(m -> m.getAmount()).sum();
    }

    @GameTest(template = EMPTY)
    public static void mageArmorCarriesSpellcastingStats(GameTestHelper helper) {
        ItemStack robe = new ItemStack(ASItems.PHOENIX_MAGE_CHESTPLATE.get());
        helper.assertTrue(modifier(robe, EquipmentSlot.CHEST, Attributes.ARMOR) == 8, "robe should keep the Aether's 8 armour");
        helper.assertTrue(modifier(robe, EquipmentSlot.CHEST, AttributeRegistry.MAX_MANA.get()) == 125, "robe should add 125 mana");
        helper.assertTrue(Math.abs(modifier(robe, EquipmentSlot.CHEST, AttributeRegistry.FIRE_SPELL_POWER.get()) - 0.10) < 1e-6, "robe should add 10% fire power");
        ItemStack helm = new ItemStack(ASItems.VALKYRIE_MAGE_HELMET.get());
        helper.assertTrue(Math.abs(modifier(helm, EquipmentSlot.HEAD, AttributeRegistry.HOLY_SPELL_POWER.get()) - 0.10) < 1e-6, "helm should add 10% holy power");
        helper.assertTrue(modifier(helm, EquipmentSlot.CHEST, AttributeRegistry.MAX_MANA.get()) == 0, "attributes only apply in the piece's own slot");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void arcaneUpgradeKeepsEnchantments(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack base = new ItemStack(AetherItems.VALKYRIE_HELMET.get());
        base.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 3);
        SimpleContainer table = new SimpleContainer(new ItemStack(ASItems.ARCANE_UPGRADE_TEMPLATE.get()), base, new ItemStack(ItemRegistry.HOLY_RUNE.get()));
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, table, level);
        helper.assertTrue(recipe.isPresent(), "template + valkyrie helmet + holy rune should have a smithing recipe");
        ItemStack result = recipe.get().assemble(table, level.registryAccess());
        helper.assertTrue(result.is(ASItems.VALKYRIE_MAGE_HELMET.get()), "result should be the valkyrie mage helm, got " + result);
        helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(Enchantments.ALL_DAMAGE_PROTECTION, result) == 3, "enchantments should carry over");
        SimpleContainer wrongRune = new SimpleContainer(new ItemStack(ASItems.ARCANE_UPGRADE_TEMPLATE.get()), new ItemStack(AetherItems.VALKYRIE_HELMET.get()),
                new ItemStack(ItemRegistry.FIRE_RUNE.get()));
        helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, wrongRune, level).isEmpty(), "valkyrie armour needs a holy rune");
        helper.assertTrue(level.getRecipeManager().byKey(AetherSpellbooks.id("aether_arcane_upgrade_smithing_template")).isPresent(), "the template should be duplicable");
        helper.succeed();
    }
}
