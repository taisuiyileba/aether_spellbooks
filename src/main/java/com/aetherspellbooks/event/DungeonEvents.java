package com.aetherspellbooks.event;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.dungeon.Guardians;
import com.aetherspellbooks.dungeon.DungeonKeyRewards;
import com.aetherteam.aether.event.BossFightEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** Dungeon boss fights: remembering the room, and what a boss's death leaves behind. */
@EventBusSubscriber(modid = AetherSpellbooks.MODID)
public final class DungeonEvents {
    private DungeonEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            DungeonKeyRewards.get(player.serverLevel()).deliver(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerEntered(BossFightEvent.AddPlayer event) {
        if (event.getDungeon() != null && event.getEntity() instanceof Mob boss && boss.level() instanceof ServerLevel level) {
            Guardians.onPlayerEntered(level, boss, event.getPlayer());
        }
    }

    @SubscribeEvent
    public static void onFightStarted(BossFightEvent.Start event) {
        if (event.getDungeon() != null && event.getEntity() instanceof Mob boss && boss.level() instanceof ServerLevel level) {
            Guardians.onFightStarted(level, boss);
        }
    }

    @SubscribeEvent
    public static void onFightStopped(BossFightEvent.Stop event) {
        if (event.getEntity() instanceof Mob boss && !boss.level().isClientSide) {
            Guardians.onFightStopped(boss);
        }
    }

    /** Lowest priority, and only if no other mod cancelled the death. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Mob boss && boss.level() instanceof ServerLevel level) {
            Guardians.onDeath(level, boss);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Mob boss && boss.level() instanceof ServerLevel level) {
            Guardians.onDrops(level, boss, event.getSource(), event.getDrops());
        }
    }
}
