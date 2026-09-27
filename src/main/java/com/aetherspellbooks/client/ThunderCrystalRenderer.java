package com.aetherspellbooks.client;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.entity.ThunderCrystalProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * A spinning, glowing octahedral crystal: an additive outer shell, a bright inner core and a soft halo.
 */
public class ThunderCrystalRenderer extends EntityRenderer<ThunderCrystalProjectile> {
    private static final ResourceLocation GLOW = AetherSpellbooks.id("textures/entity/thunder_glow.png");
    private static final ResourceLocation TEXTURE = AetherSpellbooks.id("textures/entity/thunder_crystal.png");

    public ThunderCrystalRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(@NotNull ThunderCrystalProjectile entity, float yaw, float partialTicks, PoseStack poseStack, @NotNull MultiBufferSource buffer, int light) {
        float age = entity.tickCount + partialTicks;
        poseStack.pushPose();
        poseStack.translate(0, entity.getBbHeight() * 0.5, 0);

        // halo
        poseStack.pushPose();
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180f));
        float halo = 1.3f + 0.15f * Mth.sin(age * 0.8f);
        poseStack.scale(halo, halo, halo);
        RenderHelper.quad(buffer.getBuffer(RenderType.eyes(GLOW)), poseStack.last(), 255, 240, 170, 190);
        poseStack.popPose();

        // crystal
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 14f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(age * 0.2f) * 15f));
        VertexConsumer lightning = buffer.getBuffer(RenderType.lightning());
        var matrix = poseStack.last().pose();
        RenderHelper.octahedron(lightning, matrix, 0.2f, 0.38f, 255, 214, 60, 150);
        RenderHelper.octahedron(lightning, matrix, 0.1f, 0.22f, 255, 255, 230, 230);
        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, buffer, light);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull ThunderCrystalProjectile entity) {
        return TEXTURE;
    }
}
