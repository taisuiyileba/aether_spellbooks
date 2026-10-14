package com.aetherspellbooks.gametest;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.event.ASEvents;
import com.aetherspellbooks.item.ZaniteFocusPendantItem;
import com.aetherspellbooks.registry.ASItems;
import com.aetherspellbooks.registry.ASSpells;
import com.mojang.authlib.GameProfile;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesAPI;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(AetherSpellbooks.MODID)
@PrefixGameTestTemplate(false)
public class ASAccessoriesGameTests {
    @GameTest(template = "empty", batch = "aether_spellbooks_accessories", timeoutTicks = 60)
    public static void aetherAccessoriesGrantStatsAndPendantWears(GameTestHelper helper) {
        checkEffects(helper, true, "aether:pendant_slot", "aether:cape_slot");
    }

    /** Both accessory systems accept the matching slot and provide the same attributes. */
    @GameTest(template = "empty", batch = "aether_spellbooks_accessories", timeoutTicks = 40)
    public static void accessoriesFitAetherAndCuriosSlots(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "asb-slots"));
        level.addNewPlayer(player);
        var inventory = AccessoriesCapability.get(player);
        Map<Item, List<String>> expected = Map.of(
                ASItems.AMBROSIUM_RING.get(), List.of("aether:ring_slot", "ring"),
                ASItems.ZANITE_FOCUS_PENDANT.get(), List.of("aether:pendant_slot", "necklace"),
                ASItems.GRAVITITE_CASTING_GLOVES.get(), List.of("aether:gloves_slot", "hands"),
                ASItems.VALKYRIE_MANTLE.get(), List.of("aether:cape_slot", "back"));
        var curios = CuriosApi.getCuriosInventory(player).orElseThrow();
        expected.forEach((item, slots) -> {
            String aetherSlot = slots.get(0);
            String curiosSlot = slots.get(1);
            var stack = new ItemStack(item);
            var types = AccessoriesAPI.getStackSlotTypes(player, stack).stream().map(t -> t.name()).toList();
            helper.assertTrue(types.equals(List.of(aetherSlot)), item + " should fit the Aether slot " + aetherSlot + ", fits " + types);
            // Curios' generic curio slot deliberately accepts every tagged curio.
            CuriosApi.getSlots(level).keySet().stream().filter(id -> !id.equals("curio")).forEach(id -> helper.assertTrue(
                    CuriosApi.isStackValid(new SlotContext(id, player, 0, false, true), stack) == id.equals(curiosSlot),
                    item + " should fit only the Curios slot " + curiosSlot + ", checked " + id));
            var equipped = inventory.attemptToEquipAccessory(stack.copy());
            helper.assertTrue(equipped != null && equipped.slotName().equals(aetherSlot),
                    item + " should equip into " + aetherSlot + ", went to " + (equipped == null ? "nothing" : equipped.slotName()));
            var aetherModifiers = AccessoriesAPI.getAttributeModifiers(equipped.getStack(), equipped).getAttributeModifiers(true);
            var context = new SlotContext(curiosSlot, player, 0, false, true);
            var curiosModifiers = CuriosApi.getAttributeModifiers(context, CuriosApi.getSlotId(context), stack);
            helper.assertTrue(!curiosModifiers.isEmpty() && curiosModifiers.size() == aetherModifiers.size(),
                    item + " should grant attributes through both APIs; Curios=" + curiosModifiers + ", Accessories=" + aetherModifiers);
            curiosModifiers.forEach((attribute, modifier) -> helper.assertTrue(
                    aetherModifiers.get(attribute).stream().anyMatch(other ->
                            Math.abs(other.amount() - modifier.amount()) < 1e-6 && other.operation() == modifier.operation()),
                    item + " should grant the same " + attribute + " bonus in both systems"));
            var handler = curios.getStacksHandler(curiosSlot).orElseThrow();
            helper.assertTrue(handler.getStacks().isItemValid(0, stack), item + " should be accepted by its Curios inventory slot");
            curios.setEquippedCurio(curiosSlot, 0, stack.copy());
            helper.assertTrue(curios.isEquipped(item), item + " should equip in Curios");
        });
        helper.assertTrue(CuriosApi.isStackValid(new SlotContext("spellbook", player, 0, false, true),
                new ItemStack(ASItems.VALKYRIE_GRIMOIRE.get())), "spellbooks keep the Curios spellbook slot");
        level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "aether_spellbooks_accessories", timeoutTicks = 60)
    public static void curiosPendantWearsAndMantleSoftensFalls(GameTestHelper helper) {
        checkEffects(helper, false, "necklace", "back");
    }


    @GameTest(template = "empty", batch = "aether_spellbooks_accessories", timeoutTicks = 60)
    public static void genericCuriosPendantWearsAndMantleSoftensFalls(GameTestHelper helper) {
        checkEffects(helper, false, "curio", "curio");
    }

    @GameTest(template = "empty", batch = "aether_spellbooks_accessories", timeoutTicks = 40)
    public static void unsupportedSlotsDoNotActivateEffects(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "asb-wrong-slots"));
        level.addNewPlayer(player);
        var inventory = CuriosApi.getCuriosInventory(player).orElseThrow();
        var pendant = new ItemStack(ASItems.ZANITE_FOCUS_PENDANT.get());
        // Force unsupported slots, as a pack could do by overriding its validators.
        inventory.setEquippedCurio("ring", 0, pendant);
        inventory.setEquippedCurio("hands", 0, new ItemStack(ASItems.VALKYRIE_MANTLE.get()));
        castEvent(player);
        helper.assertTrue(pendant.getDamageValue() == 0, "an inactive pendant must not wear");
        assertFall(helper, player, 1, "an inactive mantle must not soften falls");
        level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "aether_spellbooks_accessories", timeoutTicks = 40)
    public static void genericCuriosAccessoriesApplyAndRemoveLiveAttributes(GameTestHelper helper) {
        var player = tickingPlayer(helper);
        var inventory = CuriosApi.getCuriosInventory(player).orElseThrow();
        var attrs = List.of(AttributeRegistry.MANA_REGEN, AttributeRegistry.SPELL_POWER,
                AttributeRegistry.CAST_TIME_REDUCTION, AttributeRegistry.ENDER_SPELL_POWER,
                AttributeRegistry.HOLY_SPELL_POWER, AttributeRegistry.MAX_MANA);
        double[] baseline = attrs.stream().mapToDouble(player::getAttributeValue).toArray();
        var items = List.of(ASItems.AMBROSIUM_RING.get(), ASItems.ZANITE_FOCUS_PENDANT.get(),
                ASItems.GRAVITITE_CASTING_GLOVES.get(), ASItems.VALKYRIE_MANTLE.get());
        for (int i = 0; i < items.size(); i++) {
            var stack = new ItemStack(items.get(i));
            var handler = inventory.getStacksHandler("curio").orElseThrow().getStacks();
            helper.assertTrue(handler.isItemValid(i, stack), items.get(i) + " must fit the generic curio slot");
            inventory.setEquippedCurio("curio", i, stack);
        }
        helper.runAfterDelay(4, () -> {
            double[] bonuses = {0.15, ZaniteFocusPendantItem.getSpellPowerBonus(
                    inventory.getStacksHandler("curio").orElseThrow().getStacks().getStackInSlot(1)), 0.10, 0.08, 0.10, 50};
            for (int i = 0; i < attrs.size(); i++) {
                double expected = baseline[i] + bonuses[i] * (i == 5 ? 1 : player.getAttribute(attrs.get(i)).getBaseValue());
                assertAttribute(helper, player, attrs.get(i), expected, "generic curio equipped attribute " + i);
            }
            for (int i = 0; i < items.size(); i++) inventory.setEquippedCurio("curio", i, ItemStack.EMPTY);
        });
        helper.runAfterDelay(8, () -> {
            for (int i = 0; i < attrs.size(); i++) assertAttribute(helper, player, attrs.get(i), baseline[i], "removed generic attribute " + i);
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            helper.succeed();
        });
    }

    private static void checkEffects(GameTestHelper helper, boolean aetherAccessories, String pendantSlot, String mantleSlot) {
        var player = tickingPlayer(helper);
        double baseline = player.getAttributeValue(AttributeRegistry.SPELL_POWER);
        double base = player.getAttribute(AttributeRegistry.SPELL_POWER).getBaseValue();
        var equipped = equipEffects(helper, player, aetherAccessories, pendantSlot, mantleSlot,
                new ItemStack(ASItems.ZANITE_FOCUS_PENDANT.get()), new ItemStack(ASItems.VALKYRIE_MANTLE.get()));
        var pendant = equipped.pendant();
        helper.runAfterDelay(4, () -> {
            assertAttribute(helper, player, AttributeRegistry.SPELL_POWER, baseline + base * ZaniteFocusPendantItem.getSpellPowerBonus(pendant), "fresh pendant");
            assertFall(helper, player, ASEvents.MANTLE_FALL_DAMAGE_MULTIPLIER, "equipped mantle");
            castEvent(player);
            helper.assertTrue(pendant.getDamageValue() == 1, "one cast must wear the pendant exactly once");
        });
        helper.runAfterDelay(8, () -> {
            assertAttribute(helper, player, AttributeRegistry.SPELL_POWER, baseline + base * ZaniteFocusPendantItem.getSpellPowerBonus(pendant), "worn pendant");
            helper.assertTrue(player.getAttributeValue(AttributeRegistry.SPELL_POWER) > baseline + base * com.aetherspellbooks.config.ASConfig.PENDANT_MIN_SPELL_POWER.get(),
                    "wear must increase the player's actual spell power");
            pendant.setDamageValue(pendant.getMaxDamage() - 2);
        });
        helper.runAfterDelay(12, () -> {
            assertAttribute(helper, player, AttributeRegistry.SPELL_POWER, baseline + base * ZaniteFocusPendantItem.getSpellPowerBonus(pendant), "nearly depleted pendant");
            castEvent(player);
            helper.assertTrue(ZaniteFocusPendantItem.isDepleted(pendant), "the final cast must deplete the pendant");
        });
        helper.runAfterDelay(16, () -> {
            assertAttribute(helper, player, AttributeRegistry.SPELL_POWER, baseline, "depletion must remove the player's modifier");
            pendant.setDamageValue(0);
        });
        helper.runAfterDelay(20, () -> {
            assertAttribute(helper, player, AttributeRegistry.SPELL_POWER, baseline + base * ZaniteFocusPendantItem.getSpellPowerBonus(pendant), "repair must restore the player's modifier");
            equipped.unequip().run();
        });
        helper.runAfterDelay(24, () -> {
            assertAttribute(helper, player, AttributeRegistry.SPELL_POWER, baseline, "unequipping must remove the player's modifier");
            assertFall(helper, player, 1, "unequipped mantle");
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            helper.succeed();
        });
    }

    /** A disconnected GameTest player has no connection tick; run vanilla doTick from its world tick. */
    private static FakePlayer tickingPlayer(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "asb-live-stats")) {
            @Override
            public void tick() {
                doTick();
            }
        };
        var pos = helper.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 2, 4.5));
        player.moveTo(pos.x, pos.y, pos.z, 0, 0);
        player.setNoGravity(true);
        helper.getLevel().addNewPlayer(player);
        return player;
    }

    private static void castEvent(FakePlayer player) {
        var spell = ASSpells.STONEBREAKER_SHARD.get();
        NeoForge.EVENT_BUS.post(new SpellOnCastEvent(player, spell.getSpellId(), 1, 10, spell.getSchoolType(), CastSource.SPELLBOOK));
    }

    private static void assertFall(GameTestHelper helper, FakePlayer player, float expected, String message) {
        var fall = new LivingFallEvent(player, 10, 1);
        NeoForge.EVENT_BUS.post(fall);
        helper.assertTrue(Math.abs(fall.getDamageMultiplier() - expected) < 1e-6, message);
    }

    private static void assertAttribute(GameTestHelper helper, FakePlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double expected, String message) {
        double actual = player.getAttributeValue(attribute);
        helper.assertTrue(Math.abs(actual - expected) < 1e-6, message + ": expected=" + expected + ", actual=" + actual);
    }

    private record EquippedEffects(ItemStack pendant, Runnable unequip) {}

    private static EquippedEffects equipEffects(GameTestHelper helper, FakePlayer player, boolean aetherAccessories,
                                         String pendantSlot, String mantleSlot, ItemStack pendant, ItemStack mantle) {
        if (aetherAccessories) {
            var inventory = AccessoriesCapability.get(player);
            var pendantRef = inventory.attemptToEquipAccessory(pendant);
            var mantleRef = inventory.attemptToEquipAccessory(mantle);
            helper.assertTrue(pendantRef != null && mantleRef != null, "Aether must accept both accessories");
            return new EquippedEffects(pendantRef.getStack(), () -> {
                inventory.getContainers().get(pendantSlot).getAccessories().setItem(pendantRef.slot(), ItemStack.EMPTY);
                inventory.getContainers().get(mantleSlot).getAccessories().setItem(mantleRef.slot(), ItemStack.EMPTY);
            });
        }
        var inventory = CuriosApi.getCuriosInventory(player).orElseThrow();
        int mantleIndex = pendantSlot.equals(mantleSlot) ? 1 : 0;
        inventory.setEquippedCurio(pendantSlot, 0, pendant);
        inventory.setEquippedCurio(mantleSlot, mantleIndex, mantle);
        return new EquippedEffects(pendant, () -> {
            inventory.setEquippedCurio(pendantSlot, 0, ItemStack.EMPTY);
            inventory.setEquippedCurio(mantleSlot, mantleIndex, ItemStack.EMPTY);
        });
    }
}
