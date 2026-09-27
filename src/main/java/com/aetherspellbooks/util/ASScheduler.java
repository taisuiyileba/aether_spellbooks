package com.aetherspellbooks.util;

import com.aetherspellbooks.AetherSpellbooks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Runs short server-side delayed actions (e.g. the slam of Gravitite Surge).
 * Not persisted: pending actions are dropped when the server stops.
 */
@Mod.EventBusSubscriber(modid = AetherSpellbooks.MODID)
public final class ASScheduler {
    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();

    private record Task(int[] remaining, Runnable action) {
    }

    private ASScheduler() {
    }

    /**
     * Schedules {@code action} to run on the server thread after {@code delayTicks} server ticks.
     */
    public static void schedule(int delayTicks, Runnable action) {
        synchronized (PENDING) {
            PENDING.add(new Task(new int[]{Math.max(1, delayTicks)}, action));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        synchronized (PENDING) {
            TASKS.addAll(PENDING);
            PENDING.clear();
        }
        Iterator<Task> it = TASKS.iterator();
        List<Runnable> due = new ArrayList<>();
        while (it.hasNext()) {
            Task task = it.next();
            if (--task.remaining()[0] <= 0) {
                due.add(task.action());
                it.remove();
            }
        }
        for (Runnable action : due) {
            try {
                action.run();
            } catch (Exception e) {
                AetherSpellbooks.LOGGER.error("Scheduled spell action failed", e);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TASKS.clear();
        synchronized (PENDING) {
            PENDING.clear();
        }
    }
}
