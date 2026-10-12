package com.aetherspellbooks.gametest;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Real baked-model comparison and a 360-degree inspection; excluded from the release jar. */
final class BookModelRenderCheck extends Screen {
    static final String[] BOOKS = {"slider_codex", "valkyrie_grimoire", "solar_codex"};
    private static final String[] REFERENCES = {"ice_spell_book", "gold_spell_book", "blaze_spell_book"};
    private static boolean finished;
    private int frame = -1;
    private boolean references = true;

    private BookModelRenderCheck() {
        super(Component.literal("Spellbook model review"));
    }

    static boolean tick(Minecraft mc, int timer) {
        if (finished) return true;
        if (!(mc.screen instanceof BookModelRenderCheck)) {
            mc.options.guiScale().set(2);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1440, 960);
            mc.resizeDisplay();
            mc.setScreen(new BookModelRenderCheck());
        }
        BookModelRenderCheck screen = (BookModelRenderCheck) mc.screen;
        if (timer == 30) capture(mc, "asb_books_references");
        if (timer == 32) screen.references = false;
        if (timer == 36) capture(mc, "asb_books_comparison");
        // Set each angle for two ticks before capturing the rendered frame.
        if (timer >= 40 && timer < 112) {
            if (timer % 2 == 0) screen.frame = (timer - 40) / 2;
            else capture(mc, String.format(java.util.Locale.ROOT, "asb_books_turn_%02d", screen.frame));
        }
        if (timer >= 116) {
            mc.setScreen(null);
            finished = true;
        }
        return false;
    }

    private static void capture(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> {});
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xff202735);
        graphics.drawString(font, "AETHER SPELLBOOKS / 1.21.1 / actual Minecraft item renderer", 16, 14, 0xffe1e7f1, false);
        String[] labels = references
                ? new String[]{"Our 2D icon", "Iron's reference", "Our front", "Our cover", "Our back", "Our open book"}
                : new String[]{"2D icon", "Front", "Cover + pages", "Back + spine", "Item frame", "Open / HEAD"};
        float step = width / 6f;
        for (int i = 0; i < labels.length; i++) {
            graphics.drawCenteredString(font, labels[i], (int) (step * (i + .5f)), 36, 0xffaebcd0);
        }
        for (int row = 0; row < BOOKS.length; row++) {
            float y = 106 + row * ((height - 75) / 3f);
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("aether_spellbooks", BOOKS[row])));
            graphics.drawString(font, BOOKS[row], 16, (int) y + 47, 0xffaebcd0, false);
            graphics.pose().pushPose();
            graphics.pose().translate(step * .5f - 32, y - 32, 0);
            graphics.pose().scale(4, 4, 1);
            graphics.renderItem(stack, 0, 0);
            graphics.pose().popPose();
            float turn = frame < 0 ? 0 : frame * 10;
            float pitch = frame < 0 ? 18 : (float) (25 * Math.sin(frame * Math.PI / 18));
            if (references) {
                ItemStack reference = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", REFERENCES[row])));
                renderBook(graphics, reference, step * 1.5f, y, pitch, turn + 212, false);
                graphics.drawCenteredString(font, REFERENCES[row], (int) (step * 1.5f), (int) y + 47, 0xffaebcd0);
                renderBook(graphics, stack, step * 2.5f, y, 0, turn + 180, false);
                renderBook(graphics, stack, step * 3.5f, y, pitch, turn + 212, false);
                renderBook(graphics, stack, step * 4.5f, y, pitch, turn + 25, false);
            } else {
                renderBook(graphics, stack, step * 1.5f, y, 0, turn + 180, false);
                renderBook(graphics, stack, step * 2.5f, y, pitch, turn + 212, false);
                renderBook(graphics, stack, step * 3.5f, y, pitch, turn + 25, false);
                renderFixed(graphics, stack, step * 4.5f, y, pitch, turn);
            }
            renderBook(graphics, stack, step * 5.5f, y, pitch + 20, turn + 15, true);
        }
        graphics.drawString(font, frame < 0 ? "Original artwork / 7x10 cover pixels / same scale and lighting as Iron's Spells"
                : "Turntable " + frame + "/35 / yaw + changing pitch / closed and open", 16, height - 14, 0xff8297b3, false);
    }

    private void renderBook(GuiGraphics graphics, ItemStack stack, float x, float y, float pitch, float yaw, boolean open) {
        graphics.flush();
        Lighting.setupForFlatItems();
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 150);
        pose.scale(110, -110, 110);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.ZP.rotationDegrees(270));
        pose.mulPose(Axis.YP.rotationDegrees(-90));
        if (open) {
            // Undo only the HEAD attachment transform; the loader still selects
            // and renders the real open model, including its two-sided leaves.
            pose.translate(0, .375, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(90));
            pose.translate(-.25, -.53125, 0);
        } else {
            pose.translate(0, .28125, 0);
        }
        minecraft.getItemRenderer().renderStatic(stack, open ? ItemDisplayContext.HEAD : ItemDisplayContext.NONE,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, graphics.bufferSource(), minecraft.level, 0);
        graphics.flush();
        pose.popPose();
        Lighting.setupFor3DItems();
    }

    private void renderFixed(GuiGraphics graphics, ItemStack stack, float x, float y, float pitch, float yaw) {
        graphics.flush();
        Lighting.setupForFlatItems();
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 150);
        pose.scale(110, -110, 110);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        minecraft.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, graphics.bufferSource(), minecraft.level, 0);
        graphics.flush();
        pose.popPose();
        Lighting.setupFor3DItems();
    }
}
