package com.aetherspellbooks.gametest;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.compat.AetherCuriosBridge;
import com.aetherspellbooks.config.ASConfig;
import com.aetherteam.aether.item.AetherItems;
import com.aetherteam.aether.item.EquipmentUtil;
import io.wispforest.accessories.data.EntitySlotLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.List;
import java.util.Map;

@GameTestHolder(AetherSpellbooks.MODID)
@PrefixGameTestTemplate(false)
public class ASAetherCuriosGameTests {
    @GameTest(template = "empty", batch = "aether_spellbooks_accessories")
    public static void aetherSlotsFollowTheConfig(GameTestHelper helper) {
        var player = ASAccessoriesGameTests.tickingPlayer(helper);
        boolean enabled = ASConfig.useCuriosSlots();
        var nativeSlots = EntitySlotLoader.getEntitySlots(player).keySet();
        helper.assertTrue(AetherCuriosBridge.isEnabled(player) == enabled, "the config must control the synchronized bridge pack");
        for (String slot : List.of("ring", "pendant", "gloves", "cape", "accessory", "shield")) {
            helper.assertTrue(nativeSlots.contains("aether:" + slot + "_slot") != enabled, "unexpected native player slot " + slot);
        }
        helper.assertTrue(EntitySlotLoader.getEntitySlots(helper.getLevel(), EntityType.ARMOR_STAND).containsKey("aether:gloves_slot"),
                "armor stands still use their native gloves slot");
        if (!enabled) {
            helper.assertTrue(CuriosApi.getCurio(new ItemStack(AetherItems.IRON_RING.get())).isEmpty(), "the native ring has no bridge when disabled");
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            helper.succeed();
            return;
        }

        var inventory = CuriosApi.getCuriosInventory(player).orElseThrow();
        double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        equip(helper, inventory, "ring", 0, new ItemStack(AetherItems.IRON_RING.get()));
        equip(helper, inventory, "ring", 1, new ItemStack(AetherItems.IRON_RING.get()));
        equip(helper, inventory, "necklace", 0, new ItemStack(AetherItems.IRON_PENDANT.get()));
        equip(helper, inventory, "hands", 0, new ItemStack(AetherItems.GRAVITITE_GLOVES.get()));
        equip(helper, inventory, "back", 0, new ItemStack(AetherItems.AGILITY_CAPE.get()));
        equip(helper, inventory, "aether_shield", 0, new ItemStack(AetherItems.SHIELD_OF_REPULSION.get()));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AetherItems.GOLDEN_FEATHER.get()));
        NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND));
        helper.assertTrue(player.getMainHandItem().isEmpty() && inventory.isEquipped(AetherItems.GOLDEN_FEATHER.get()),
                "right click must equip the feather into Curios");
        helper.assertTrue(inventory.getStacksHandler("charm").orElseThrow().getSlots() >= 2, "two miscellaneous slots must be available");
        helper.assertTrue(EquipmentUtil.getAccessories(player, AetherItems.IRON_RING.get()).size() == 2, "the Aether must find both rings");
        var gloves = EquipmentUtil.getGloves(player);
        helper.assertTrue(gloves != null && gloves.reference().isValid() && gloves.reference().getStack() == gloves.stack(),
                "the reference must read the real Curios stack");
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) > damage, "the Aether gloves must grant their attack damage");
            helper.assertTrue(gloves.reference().setStack(ItemStack.EMPTY), "the reference must write to the real Curios stack");
        });
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(!inventory.isEquipped(AetherItems.GRAVITITE_GLOVES.get())
                    && player.getAttributeValue(Attributes.ATTACK_DAMAGE) == damage, "unequipping must remove the glove's attributes");
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "aether_spellbooks_accessories")
    public static void deepAetherAccessoriesUseTheSameBridge(GameTestHelper helper) {
        var scarfId = deepId("floaty_scarf");
        if (!ASConfig.useCuriosSlots() || !BuiltInRegistries.ITEM.containsKey(scarfId)) {
            helper.succeed();
            return;
        }
        var player = ASAccessoriesGameTests.tickingPlayer(helper);
        var inventory = CuriosApi.getCuriosInventory(player).orElseThrow();
        Map.of("ring", List.of("skyjade_ring", "stratus_ring", "spooky_ring", "gravitite_ring"),
                "necklace", List.of("medal_of_honor", "aercloud_necklace", "floaty_scarf"),
                "hands", List.of("skyjade_gloves", "stormforged_gloves", "stratus_gloves"),
                "back", List.of("cloud_cape"),
                "charm", List.of("slider_eye"),
                "aether_shield", List.of("wind_shield")).forEach((slot, names) -> {
            var stacks = inventory.getStacksHandler(slot).orElseThrow().getStacks();
            for (String name : names) {
                var stack = new ItemStack(BuiltInRegistries.ITEM.get(deepId(name)));
                helper.assertTrue(!stack.isEmpty() && stacks.isItemValid(0, stack), name + " must fit " + slot);
            }
        });
        var ring = BuiltInRegistries.ITEM.get(deepId("skyjade_ring"));
        equip(helper, inventory, "ring", 0, new ItemStack(ring));
        equip(helper, inventory, "ring", 1, new ItemStack(ring));
        equip(helper, inventory, "necklace", 0, new ItemStack(BuiltInRegistries.ITEM.get(scarfId)));
        helper.assertTrue(EquipmentUtil.getAccessories(player, ring).size() == 2, "Deep Aether's equipment lookup must find both rings");
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(hasGentleWind(helper, player), "the scarf must summon its Gentle Wind");
            inventory.setEquippedCurio("necklace", 0, ItemStack.EMPTY);
        });
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(!hasGentleWind(helper, player), "the scarf must dismiss its Gentle Wind when removed");
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            helper.succeed();
        });
    }

    private static boolean hasGentleWind(GameTestHelper helper, Entity player) {
        // The wrapped scarf keeps its entity fifty blocks above its wearer.
        return !helper.getLevel().getEntities(player, player.getBoundingBox().inflate(3, 64, 3),
                entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(deepId("gentle_wind"))).isEmpty();
    }

    private static ResourceLocation deepId(String path) {
        return ResourceLocation.fromNamespaceAndPath("deep_aether", path);
    }

    private static void equip(GameTestHelper helper, ICuriosItemHandler inventory, String slot, int index, ItemStack stack) {
        var stacks = inventory.getStacksHandler(slot).orElseThrow().getStacks();
        helper.assertTrue(stacks.insertItem(index, stack, false).isEmpty(), stack + " must equip in " + slot);
    }
}
