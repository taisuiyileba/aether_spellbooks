package com.aetherspellbooks.client;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.entity.ThornTrail;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public class ThornTrailRenderer extends EntityRenderer<ThornTrail> {
    public static final ResourceLocation HOOK = AetherSpellbooks.id("effect/briar_hook");
    public static final ResourceLocation RUNNER = AetherSpellbooks.id("effect/briar_runner");

    public ThornTrailRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ThornTrail trail, float yaw, float partial, PoseStack pose, MultiBufferSource buffer, int light) {
        int[] heights = trail.profile();
        float age = trail.growthAge(partial);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-trail.getYRot()));
        for (int i = 0; i < heights.length; i++) {
            float time = age - i * ThornTrail.STEP_TICKS;
            if (time < 0 || time >= ThornTrail.WITHER_TICKS) {
                continue;
            }
            float emerge = Mth.clamp((time + 0.7f) / 4, 0, 1);
            float shrink = (float) Mth.smoothstep(Mth.clamp((time - 11) / 7, 0, 1));
            float sprung = Mth.sin(emerge * Mth.HALF_PI);
            float height = heights[i] / 256f;
            float slope = height - (i == 0 ? 0 : heights[i - 1] / 256f);
            pose.pushPose();
            pose.translate(0, height, i + 1);
            pose.pushPose();
            pose.mulPose(Axis.XP.rotation((float) -Math.atan(slope)));
            float rootSize = (0.45f + 0.55f * sprung) * (1 - shrink * 0.85f);
            pose.scale(rootSize, rootSize, (float) Math.sqrt(1 + slope * slope));
            draw(RUNNER, pose, buffer, light);
            pose.popPose();

            float size = (0.59f + Math.min(i, 6) * 0.084f) * (1 - shrink);
            float side = ((i + trail.getId()) % 2 == 0) ? 1 : -1;
            float recoil = Mth.sin((time - 4) * 0.7f) * Math.max(0, 1 - Math.abs(time - 6) / 4);
            pose.mulPose(Axis.YP.rotationDegrees(side * (17 + (i % 3) * 7)));
            pose.mulPose(Axis.XP.rotationDegrees(-32 * (1 - sprung) + recoil * 7 + shrink * 35));
            pose.scale(size * (0.65f + sprung * 0.35f), size * sprung, size);
            draw(HOOK, pose, buffer, light);
            pose.popPose();
        }
        pose.popPose();
    }

    private void draw(ResourceLocation id, PoseStack pose, MultiBufferSource buffer, int light) {
        var minecraft = Minecraft.getInstance();
        var model = minecraft.getModelManager().getModel(id);
        minecraft.getItemRenderer().renderModelLists(model, ItemStack.EMPTY, light, OverlayTexture.NO_OVERLAY,
                pose, buffer.getBuffer(Sheets.cutoutBlockSheet()));
    }

    @Override
    public ResourceLocation getTextureLocation(ThornTrail trail) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
