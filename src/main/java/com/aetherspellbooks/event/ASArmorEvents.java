package com.aetherspellbooks.event;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.armor.PhoenixMageSet;
import com.aetherspellbooks.armor.ValkyrieMageSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Set abilities of the Valkyrie and Phoenix mage armour. */
@EventBusSubscriber(modid = AetherSpellbooks.MODID)
public class ASArmorEvents {
    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (PhoenixMageSet.isWornBy(entity)) {
            PhoenixMageSet.tick(entity);
        }
        if (entity instanceof Player player) {
            ValkyrieMageSet.tick(player, ValkyrieMageSet.isWornBy(player));
        }
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingIncomingDamageEvent event) {
        if (PhoenixMageSet.blocksDamage(event.getEntity(), event.getSource())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (ValkyrieMageSet.isWornBy(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Runs before other death handlers so a reborn wearer keeps everything. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (PhoenixMageSet.tryRebirth(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
