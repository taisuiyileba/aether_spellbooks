package com.aetherspellbooks.client.armor;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.item.AetherMageArmorItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib model of a mage armour set: {@code geo/<set>_armor.geo.json}, {@code textures/models/armor/<set>.png}
 * and an idle animation for its moving parts (helm wings, crest feathers).
 */
public class AetherMageArmorModel extends GeoModel<AetherMageArmorItem> {
    private final ResourceLocation model;
    private final ResourceLocation texture;
    private final ResourceLocation animation;

    public AetherMageArmorModel(String set) {
        this.model = AetherSpellbooks.id("geo/" + set + "_armor.geo.json");
        this.texture = AetherSpellbooks.id("textures/models/armor/" + set + ".png");
        this.animation = AetherSpellbooks.id("animations/" + set + "_armor.animation.json");
    }

    @Override
    public ResourceLocation getModelResource(AetherMageArmorItem item) {
        return model;
    }

    @Override
    public ResourceLocation getTextureResource(AetherMageArmorItem item) {
        return texture;
    }

    @Override
    public ResourceLocation getAnimationResource(AetherMageArmorItem item) {
        return animation;
    }
}
