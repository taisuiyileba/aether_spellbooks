package com.aetherspellbooks.client.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * A layer that draws extra geometry attached to one bone of the model, so it follows the bone's animation.
 * <p>
 * The bone's transform is captured while the model renders and the geometry is drawn afterwards,
 * because switching buffers in the middle of the model would end the batch the model is still writing into.
 */
public abstract class BoneAnchoredLayer extends GeoRenderLayer<AbstractSpellCastingMob> {
    private final String boneName;
    private final Matrix4f bonePose = new Matrix4f();
    private final Matrix3f boneNormal = new Matrix3f();
    private boolean captured;

    protected BoneAnchoredLayer(GeoRenderer<AbstractSpellCastingMob> renderer, String boneName) {
        super(renderer);
        this.boneName = boneName;
    }

    @Override
    public void renderForBone(PoseStack poseStack, AbstractSpellCastingMob animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                              VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        if (bone.getName().equals(boneName)) {
            bonePose.set(poseStack.last().pose());
            boneNormal.set(poseStack.last().normal());
            captured = true;
        }
    }

    @Override
    public void render(PoseStack poseStack, AbstractSpellCastingMob animatable, BakedGeoModel bakedModel, RenderType renderType, MultiBufferSource bufferSource,
                       VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        if (!captured) {
            return;
        }
        captured = false;
        if (animatable.isInvisible()) {
            return;
        }
        poseStack.pushPose();
        poseStack.last().pose().set(bonePose);
        poseStack.last().normal().set(boneNormal);
        renderOnBone(poseStack, animatable, bufferSource, partialTick, packedLight, packedOverlay);
        poseStack.popPose();
    }

    /**
     * Draws in the bone's space: units are blocks, +Y is up and +Z points out of the model's back.
     */
    protected abstract void renderOnBone(PoseStack poseStack, AbstractSpellCastingMob animatable, MultiBufferSource bufferSource,
                                         float partialTick, int packedLight, int packedOverlay);
}
