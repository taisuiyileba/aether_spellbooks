package com.aetherspellbooks.client.accessory;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.item.AetherMageArmorItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

public final class AccessoryWearLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation PENDANT_TEXTURE = texture("pendant/zanite_focus_pendant");
    private static final ResourceLocation GLOVES_TEXTURE = texture("gloves/gravitite_casting_gloves");
    private static final ResourceLocation MANTLE_TEXTURE = texture("capes/valkyrie_mantle");

    private static final ModelPart PENDANT = AccessoryModels.pendant(0.62f);
    private static final ModelPart ARMORED_PENDANT = AccessoryModels.pendant(1.25f);
    private static final ModelPart MAGE_PENDANT = AccessoryModels.pendant(1.76f);
    private static final ModelPart RIGHT_GLOVE = AccessoryModels.glove(true, false, false);
    private static final ModelPart LEFT_GLOVE = AccessoryModels.glove(false, false, false);
    private static final ModelPart RIGHT_SLIM_GLOVE = AccessoryModels.glove(true, true, false);
    private static final ModelPart LEFT_SLIM_GLOVE = AccessoryModels.glove(false, true, false);
    private static final ModelPart RIGHT_MAGE_GLOVE = AccessoryModels.glove(true, false, true);
    private static final ModelPart LEFT_MAGE_GLOVE = AccessoryModels.glove(false, false, true);
    private static final ModelPart MANTLE = AccessoryModels.mantle();

    public AccessoryWearLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (player.isInvisible()) {
            return;
        }
        PlayerModel<AbstractClientPlayer> playerModel = getParentModel();
        var chest = player.getItemBySlot(EquipmentSlot.CHEST);
        boolean mageArmor = chest.getItem() instanceof AetherMageArmorItem;
        if (playerModel.body.visible && WearableSlot.PENDANT.isVisible(player)) {
            ModelPart pendant = mageArmor ? MAGE_PENDANT : (chest.isEmpty() ? PENDANT : ARMORED_PENDANT);
            pendant.copyFrom(playerModel.body);
            renderPart(pendant, PENDANT_TEXTURE, pose, buffers, light);
        }
        if (WearableSlot.GLOVES.isVisible(player)) {
            renderGlove(player, playerModel.rightArm, true, mageArmor, pose, buffers, light);
            renderGlove(player, playerModel.leftArm, false, mageArmor, pose, buffers, light);
        }
    }

    public static void renderFirstPerson(AbstractClientPlayer player, ModelPart arm, boolean right,
                                         PoseStack pose, MultiBufferSource buffers, int light) {
        if (!player.isInvisible() && WearableSlot.GLOVES.isVisible(player)) {
            renderGlove(player, arm, right, false, pose, buffers, light);
        }
    }

    public static void renderMantle(PoseStack pose, MultiBufferSource buffers, int light) {
        renderPart(MANTLE, MANTLE_TEXTURE, pose, buffers, light);
    }

    private static void renderGlove(AbstractClientPlayer player, ModelPart arm, boolean right, boolean mageArmor,
                                    PoseStack pose, MultiBufferSource buffers, int light) {
        if (!arm.visible) {
            return;
        }
        boolean slim = "slim".equals(player.getModelName());
        ModelPart glove;
        if (mageArmor) {
            glove = right ? RIGHT_MAGE_GLOVE : LEFT_MAGE_GLOVE;
        } else if (slim) {
            glove = right ? RIGHT_SLIM_GLOVE : LEFT_SLIM_GLOVE;
        } else {
            glove = right ? RIGHT_GLOVE : LEFT_GLOVE;
        }
        glove.copyFrom(arm);
        renderPart(glove, GLOVES_TEXTURE, pose, buffers, light);
    }

    private static void renderPart(ModelPart part, ResourceLocation texture, PoseStack pose, MultiBufferSource buffers, int light) {
        part.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY);
    }

    private static ResourceLocation texture(String path) {
        return AetherSpellbooks.id("textures/models/accessory/" + path + ".png");
    }
}
