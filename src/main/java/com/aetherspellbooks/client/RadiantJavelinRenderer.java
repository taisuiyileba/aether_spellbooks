package com.aetherspellbooks.client;

import com.aetherspellbooks.entity.RadiantJavelin;
import com.aetherteam.aether.item.AetherItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Draws the javelin as the Aether's Valkyrie Lance (its item model, loaded at runtime), full-bright and pointing along
 * its flight. The flat sprite is drawn twice, crossed like an arrow, so it reads from every angle; a planted javelin
 * quivers where it struck.
 */
public class RadiantJavelinRenderer extends EntityRenderer<RadiantJavelin> {
    private ItemStack lance;

    public RadiantJavelinRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(@NotNull RadiantJavelin entity, float yaw, float partialTicks, PoseStack poseStack, @NotNull MultiBufferSource buffer, int light) {
        if (lance == null) {
            lance = new ItemStack(AetherItems.VALKYRIE_LANCE.get());
        }
        Vec3 dir = entity.renderDirection;
        float yRot = (float) (Mth.atan2(dir.x, dir.z) * Mth.RAD_TO_DEG);
        float xRot = (float) (Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG);
        if (entity.isPlanted()) {
            // a dying quiver after it strikes
            float t = entity.getPlantedTicks() + partialTicks;
            xRot += Mth.sin(t * 2.2f) * 6f * Math.max(0f, 1f - t / 8f);
        }

        poseStack.pushPose();
        poseStack.translate(0, entity.getBbHeight() * 0.5, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(-xRot));
        for (int roll = 0; roll < 180; roll += 90) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(roll + 45));
            // the lance sprite runs diagonally (handle bottom-left, tip top-right): turn the tip onto +Z
            poseStack.mulPose(Axis.XP.rotationDegrees(90));
            poseStack.mulPose(Axis.ZP.rotationDegrees(45));
            // the tip leads: shift so the spear's middle trails behind the entity position
            poseStack.translate(-0.35, -0.35, 0);
            poseStack.scale(1.9f, 1.9f, 1.9f);
            Minecraft.getInstance().getItemRenderer().renderStatic(lance, ItemDisplayContext.NONE, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    poseStack, buffer, entity.level(), entity.getId());
            poseStack.popPose();
        }
        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, buffer, light);
    }

    @SuppressWarnings("deprecation")
    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull RadiantJavelin entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
