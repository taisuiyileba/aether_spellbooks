package com.aetherspellbooks.util;

import com.aetherteam.aether.entity.miscellaneous.CloudMinion;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks the cloud minions conjured by Cloud Sentinels and tells them when to fire.
 * Cloud minions aim wherever their owner looks, so they fire when the owner's crosshair is on an enemy.
 */
public final class CloudSentinels {
    public static final float AIM_RANGE = 24f;

    private record Entry(List<CloudMinion> minions, int interval) {
    }

    private static final Map<UUID, Entry> ACTIVE = new HashMap<>();

    private CloudSentinels() {
    }

    public static void track(Player player, List<CloudMinion> minions, int interval) {
        ACTIVE.put(player.getUUID(), new Entry(List.copyOf(minions), interval));
    }

    public static void dismiss(Player player) {
        Entry entry = ACTIVE.remove(player.getUUID());
        if (entry != null) {
            entry.minions().forEach(minion -> {
                if (minion.isAlive()) {
                    minion.setLifeSpan(1);
                }
            });
        }
    }

    public static List<CloudMinion> getSentinels(Player player) {
        Entry entry = ACTIVE.get(player.getUUID());
        return entry == null ? List.of() : entry.minions();
    }

    /** Called every server tick for each player. */
    public static void tick(Player player) {
        Entry entry = ACTIVE.get(player.getUUID());
        if (entry == null) {
            return;
        }
        if (entry.minions().stream().noneMatch(CloudMinion::isAlive)) {
            ACTIVE.remove(player.getUUID());
            return;
        }
        if (player.tickCount % entry.interval() == 0 && hasTargetInSight(player)) {
            volley(player);
        }
    }

    /** Fires every living sentinel of the player. */
    public static void volley(Player player) {
        for (CloudMinion minion : getSentinels(player)) {
            if (minion.isAlive()) {
                minion.setShouldShoot(true);
            }
        }
    }

    private static boolean hasTargetInSight(Player player) {
        HitResult hit = Utils.raycastForEntity(player.level(), player, AIM_RANGE, true, 0.4f);
        return hit instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof LivingEntity living
                && !(living instanceof CloudMinion)
                && !living.isAlliedTo(player) && !player.isAlliedTo(living);
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
