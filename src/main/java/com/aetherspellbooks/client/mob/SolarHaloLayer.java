package com.aetherspellbooks.client.mob;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.client.RenderHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * A slowly turning sun halo behind the Solar Acolyte's head, with a glowing corona that flares up while it casts.
 */
public class SolarHaloLayer extends BoneAnchoredLayer {
    private static final ResourceLocation HALO = AetherSpellbooks.id("textures/entity/solar_halo.png");
    private static final ResourceLocation RAYS = AetherSpellbooks.id("textures/entity/solar_rays.png");
    private static final ResourceLocation CORE = AetherSpellbooks.id("textures/entity/solar_core.png");
    /** Just behind the back of the head, level with the eyes (head-bone space, blocks). */
    private static final float CENTER_Y = 1.78f;
    private static final float CENTER_Z = 0.31f;

    public SolarHaloLayer(GeoRenderer<AbstractSpellCastingMob> renderer) {
        super(renderer, "head");
    }

    @Override
    protected void renderOnBone(PoseStack poseStack, AbstractSpellCastingMob animatable, MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
        float time = animatable.tickCount + partialTick;
        float flare = animatable.isCasting() ? 1f : 0f;
        float pulse = 1f + 0.06f * Mth.sin(time * 0.15f) + 0.25f * flare;
        int glow = (int) (170 + 60 * flare);

        poseStack.translate(0, CENTER_Y, CENTER_Z);
        // the golden halo itself: alpha blended, so it shows against the Aether's bright sky
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(time * 0.8f));
        poseStack.scale(1.3f * pulse, 1.3f * pulse, 1);
        RenderHelper.quad(bufferSource.getBuffer(RenderType.entityTranslucentEmissive(HALO)), poseStack.last(), 255, 255, 255, 235);
        poseStack.popPose();

        // additive glow on top: a corona of rays turning the other way, and a hot core
        poseStack.pushPose();
        poseStack.translate(0, 0, 0.01f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(-time * 1.6f));
        poseStack.scale(1.5f * pulse, 1.5f * pulse, 1);
        RenderHelper.quad(bufferSource.getBuffer(RenderType.eyes(RAYS)), poseStack.last(), glow, (int) (glow * 0.8f), (int) (glow * 0.45f), 255);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(0, 0, 0.02f);
        poseStack.scale(0.55f * pulse, 0.55f * pulse, 1);
        RenderHelper.quad(bufferSource.getBuffer(RenderType.eyes(CORE)), poseStack.last(), glow, (int) (glow * 0.85f), (int) (glow * 0.6f), 255);
        poseStack.popPose();
    }
}
