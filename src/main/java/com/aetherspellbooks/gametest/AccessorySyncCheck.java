package com.aetherspellbooks.gametest;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.item.ZaniteFocusPendantItem;
import com.aetherspellbooks.registry.ASItems;
import com.aetherspellbooks.registry.ASSpells;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import top.theillusivec4.curios.api.CuriosApi;

/** Real client/server packet check, after the accessory menu scenes. Excluded from release jars. */
public final class AccessorySyncCheck {
    private record Snapshot(int stage, double power, int damage) {}
    private static volatile Snapshot snapshot;
    private static int checkedStage;
    private static double baseline;
    private static double base;

    private AccessorySyncCheck() {}

    static void tick(Minecraft mc, MinecraftServer server, int timer) {
        var expected = snapshot;
        if (expected != null && checkedStage < expected.stage()) {
            var stack = CuriosApi.getCuriosInventory(mc.player).orElseThrow().getStacksHandler("curio")
                    .orElseThrow().getStacks().getStackInSlot(0);
            int damage = stack.is(ASItems.ZANITE_FOCUS_PENDANT.get()) ? stack.getDamageValue() : -1;
            if (damage == expected.damage()
                    && Math.abs(mc.player.getAttributeValue(AttributeRegistry.SPELL_POWER) - expected.power()) < 1e-6) {
                checkedStage = expected.stage();
                AetherSpellbooks.LOGGER.info("[ASB-ACC-SYNC] stage {} passed: client and server power={}, damage={}",
                        checkedStage, expected.power(), damage);
            }
        }
        if (timer == 350) {
            var uuid = mc.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(uuid);
                baseline = player.getAttributeValue(AttributeRegistry.SPELL_POWER);
                base = player.getAttribute(AttributeRegistry.SPELL_POWER).getBaseValue();
                CuriosApi.getCuriosInventory(player).orElseThrow().setEquippedCurio("curio", 0,
                        new ItemStack(ASItems.ZANITE_FOCUS_PENDANT.get()));
            });
        }
        if (timer >= 380 && timer <= 580 && (timer - 380) % 50 == 0) {
            int stage = (timer - 380) / 50 + 1;
            var uuid = mc.player.getUUID();
            server.execute(() -> capture(server.getPlayerList().getPlayer(uuid), stage));
        }
        if (timer >= 400 && timer <= 600 && (timer - 400) % 50 == 0) {
            int stage = (timer - 400) / 50 + 1;
            if (checkedStage != stage) {
                throw new IllegalStateException("Accessory synchronization stage " + stage + " never reached the client; snapshot=" + snapshot);
            }
            if (stage == 5) {
                AetherSpellbooks.LOGGER.info("[ASB-ACC-SYNC] all 5 stages passed (equip, wear, depletion, repair, unequip)");
                mc.stop();
                return;
            }
            var uuid = mc.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(uuid);
                var inventory = CuriosApi.getCuriosInventory(player).orElseThrow();
                var stack = inventory.getStacksHandler("curio").orElseThrow().getStacks().getStackInSlot(0);
                switch (stage) {
                    case 1 -> {
                        var spell = ASSpells.STONEBREAKER_SHARD.get();
                        NeoForge.EVENT_BUS.post(new SpellOnCastEvent(player, spell.getSpellId(), 1, 10,
                                spell.getSchoolType(), CastSource.SPELLBOOK));
                    }
                    case 2 -> stack.setDamageValue(stack.getMaxDamage() - 1);
                    case 3 -> stack.setDamageValue(0);
                    case 4 -> inventory.setEquippedCurio("curio", 0, ItemStack.EMPTY);
                    default -> throw new IllegalStateException("Unexpected synchronization stage " + stage);
                }
            });
        }
    }

    private static void capture(ServerPlayer player, int stage) {
        var stack = CuriosApi.getCuriosInventory(player).orElseThrow().getStacksHandler("curio")
                .orElseThrow().getStacks().getStackInSlot(0);
        int expectedDamage = switch (stage) {
            case 1, 4 -> 0;
            case 2 -> 1;
            case 3 -> stack.getMaxDamage() - 1;
            case 5 -> -1;
            default -> throw new IllegalStateException("Unexpected synchronization stage " + stage);
        };
        int damage = stack.is(ASItems.ZANITE_FOCUS_PENDANT.get()) ? stack.getDamageValue() : -1;
        double expectedPower = baseline + (stage == 5 ? 0 : base * ZaniteFocusPendantItem.getSpellPowerBonus(stack));
        double power = player.getAttributeValue(AttributeRegistry.SPELL_POWER);
        if (damage != expectedDamage || Math.abs(power - expectedPower) > 1e-6) {
            throw new IllegalStateException("Accessory server stage " + stage + ": expected power=" + expectedPower
                    + ", actual=" + power + ", expected damage=" + expectedDamage + ", actual=" + damage);
        }
        snapshot = new Snapshot(stage, power, damage);
    }
}
