package com.aetherspellbooks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Client-only queries about the local view (call only from client-side code paths). */
public final class ClientViewHelper {
    private ClientViewHelper() {
    }

    /** True when the entity is the local player seen from their own eyes (particles at the hand would fill the screen). */
    public static boolean isFirstPersonSelf(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        return entity == mc.player && mc.options.getCameraType().isFirstPerson();
    }
}
