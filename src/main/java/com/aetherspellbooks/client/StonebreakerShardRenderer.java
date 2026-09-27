package com.aetherspellbooks.client;

import com.aetherspellbooks.entity.StonebreakerShard;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Renders the shard as an elongated chunk of holystone (the Aether's own block model, loaded at runtime)
 * that points along its flight path and tumbles around it.
 */
public class StonebreakerShardRenderer extends EntityRenderer<StonebreakerShard> {
    public StonebreakerShardRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(@NotNull StonebreakerShard entity, float yaw, float partialTicks, PoseStack poseStack, @NotNull MultiBufferSource buffer, int light) {
        Vec3 motion = entity.getDeltaMovement();
        float yRot = (float) (Mth.atan2(motion.x, motion.z) * Mth.RAD_TO_DEG);
        float xRot = (float) (Mth.atan2(motion.y, motion.horizontalDistance()) * Mth.RAD_TO_DEG);
        float age = entity.tickCount + partialTicks;

        poseStack.pushPose();
        poseStack.translate(0, entity.getBbHeight() * 0.5, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(-xRot));
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * 30f));
        // long axis along the flight direction
        poseStack.scale(0.32f, 0.32f, 0.62f);
        poseStack.mulPose(Axis.XP.rotationDegrees(45));
        poseStack.mulPose(Axis.YP.rotationDegrees(45));
        poseStack.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(StonebreakerShard.holystone(), poseStack, buffer, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, buffer, light);
    }

    @SuppressWarnings("deprecation")
    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull StonebreakerShard entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
