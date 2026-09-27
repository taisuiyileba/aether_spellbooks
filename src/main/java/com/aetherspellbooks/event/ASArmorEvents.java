package com.aetherspellbooks.event;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.armor.PhoenixMageSet;
import com.aetherspellbooks.armor.ValkyrieMageSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Set abilities of the Valkyrie and Phoenix mage armour. */
@Mod.EventBusSubscriber(modid = AetherSpellbooks.MODID)
public class ASArmorEvents {
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (PhoenixMageSet.isWornBy(entity)) {
            PhoenixMageSet.tick(entity);
        }
        if (entity instanceof Player player) {
            ValkyrieMageSet.tick(player, ValkyrieMageSet.isWornBy(player));
        }
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
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
