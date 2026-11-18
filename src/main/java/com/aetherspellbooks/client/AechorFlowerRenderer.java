package com.aetherspellbooks.client;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.entity.AechorFlower;
import com.aetherteam.aether.client.renderer.AetherModelLayers;
import com.aetherteam.aether.client.renderer.entity.model.AechorPlantModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class AechorFlowerRenderer extends EntityRenderer<AechorFlower> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("aether", "textures/entity/mobs/aechor_plant/aechor_plant.png");
    private static final ResourceLocation PETALS = AetherSpellbooks.id("textures/entity/aechor_corolla.png");
    private final AechorPlantModel model;
    private final ModelPart receptacle;
    private final Iterable<ModelPart> corolla;
    private final Iterable<ModelPart> stamens;

    public AechorFlowerRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new AechorPlantModel(context.bakeLayer(AetherModelLayers.AECHOR_PLANT));
        var junction = new MeshDefinition();
        junction.getRoot().addOrReplaceChild("receptacle", CubeListBuilder.create().texOffs(36, 20)
                .addBox(-1.5f, -3, -1.5f, 3, 6, 3), PartPose.ZERO);
        receptacle = LayerDefinition.create(junction, 64, 32).bakeRoot().getChild("receptacle");
        corolla = model.petalParts();
        stamens = model.stamenStemParts();
    }

    @Override
    public Vec3 getRenderOffset(AechorFlower entity, float partialTick) {
        return entity.renderOffset(partialTick);
    }

    @Override
    public void render(AechorFlower flower, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffer, int light) {
        float age = flower.tickCount + partialTick;
        float tilt = Mth.lerp(partialTick, flower.xRotO, flower.getXRot());
        float t = flower.volleyAge(partialTick);
        boolean firing = t >= 0 && t < AechorFlower.RECOVER;
        float beat = t <= AechorFlower.FIRST_SHOT + 3 ? t - AechorFlower.FIRST_SHOT : t - AechorFlower.SECOND_SHOT;
        float charge = firing && beat < 0 ? (float) Mth.smoothstep(Mth.clamp(1 + beat / 8, 0, 1)) : 0;
        float kick = firing && beat >= 0 ? Mth.clamp(1 - beat / 4f, 0, 1) : 0;
        float appear = (float) Mth.smoothstep(Mth.clamp((age + 0.25f) / 9f, 0, 1));
        var plant = flower.animationSource();
        model.setupAnim(plant, 0, 0, age * 0.16f, 0, 0);
        for (ModelPart petal : corolla) {
            petal.xRot += 0.14f - 0.94f * (1 - appear) - 0.48f * charge + 0.54f * kick;
        }
        for (ModelPart stamen : stamens) {
            stamen.xRot += charge * 0.22f - kick * 0.25f;
        }
        pose.pushPose();
        float scale = 0.63f * appear * flower.fade(partialTick);
        pose.scale(scale, scale, scale);
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, flower.yRotO, flower.getYRot())));

        // The leaf rosette hangs beneath the bloom. Only the corolla hinges towards its target.
        pose.pushPose();
        pose.translate(0, -0.09, 0);
        pose.mulPose(Axis.XP.rotationDegrees(tilt * 0.14f));
        pose.scale(-0.73f, -0.85f, 0.73f);
        corolla.forEach(part -> part.visible = false);
        stamens.forEach(part -> part.visible = false);
        var nativeSkin = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        model.stem.render(pose, nativeSkin, light, OverlayTexture.NO_OVERLAY);
        corolla.forEach(part -> part.visible = true);
        stamens.forEach(part -> part.visible = true);
        pose.popPose();

        receptacle.render(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(PETALS)), light, OverlayTexture.NO_OVERLAY);
        pose.translate(0, 0.07, 0);
        pose.mulPose(Axis.XP.rotationDegrees(tilt));
        pose.translate(0, -0.13f * kick, 0);
        if (firing && (charge > 0 || kick > 0)) {
            pollen(pose, buffer, age, charge, kick);
        }
        pose.scale(-1, -1, 1);
        model.head.render(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(PETALS)), light, OverlayTexture.NO_OVERLAY);
        nativeSkin = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        model.stem.translateAndRotate(pose);
        for (ModelPart stamen : stamens) {
            stamen.render(pose, nativeSkin, light, OverlayTexture.NO_OVERLAY);
        }
        var petals = buffer.getBuffer(RenderType.entityCutoutNoCull(PETALS));
        for (ModelPart petal : corolla) {
            petal.render(pose, petals, light, OverlayTexture.NO_OVERLAY);
        }
        pose.popPose();
        super.render(flower, yaw, partialTick, pose, buffer, light);
    }

    private static void pollen(PoseStack pose, MultiBufferSource buffer, float age, float charge, float kick) {
        var vertices = buffer.getBuffer(RenderType.lightning());
        float size = 0.027f + 0.046f * (charge + kick);
        for (int i = 0; i < 5; i++) {
            float angle = age * 0.24f + i * Mth.TWO_PI / 5;
            float radius = 0.64f - 0.42f * charge + 0.08f * kick;
            pose.pushPose();
            pose.translate(Mth.cos(angle) * radius, 0.12f + 0.25f * charge + 0.13f * kick, Mth.sin(angle) * radius);
            RenderHelper.octahedron(vertices, pose.last().pose(), size, size * 1.7f, 209, 255, 157, (int) (210 * (charge + kick)));
            pose.popPose();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(AechorFlower flower) {
        return TEXTURE;
    }
}
