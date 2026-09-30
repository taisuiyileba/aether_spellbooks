package com.aetherspellbooks.item;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.client.armor.AetherMageArmorModel;
import com.aetherspellbooks.client.armor.ArmorTooltipHelper;
import com.aetherspellbooks.registry.ASArmorMaterials;
import io.redspace.ironsspellbooks.entity.armor.GenericCustomArmorRenderer;
import io.redspace.ironsspellbooks.item.armor.IDisableHat;
import io.redspace.ironsspellbooks.item.armor.ImbuableChestplateArmorItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLEnvironment;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

import java.util.List;

/**
 * A piece of the Valkyrie or Phoenix mage set. Behaves like an Iron's Spells school armour (mana and spell power
 * per piece, a spell can be imbued into the chestplate, upgrade orbs apply) with its own 3D model and a cape;
 * the full set carries the original Aether set ability plus a spellcasting bonus (see the armor package).
 */
public class AetherMageArmorItem extends ImbuableChestplateArmorItem implements IDisableHat {
    private final ASArmorMaterials set;

    public AetherMageArmorItem(ASArmorMaterials set, Type type, Properties properties) {
        super(set.holder(), type, properties.durability(set.getDurabilityForType(type)), set.attributeContainers());
        this.set = set;
    }

    public ASArmorMaterials getSet() {
        return set;
    }

    /** Cape drawn by {@code MageCapeLayer} while the chestplate is worn (vanilla 64x32 cape layout). */
    public ResourceLocation getCapeTexture() {
        return AetherSpellbooks.id("textures/models/armor/" + set.id() + "_cape.png");
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        int worn = FMLEnvironment.dist == Dist.CLIENT ? ArmorTooltipHelper.piecesWorn(set) : 0;
        tooltip.add(Component.translatable("tooltip.aether_spellbooks.set_bonus", worn).withStyle(worn == 4 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        ChatFormatting line = worn == 4 ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY;
        tooltip.add(Component.literal(" ").append(Component.translatable("tooltip.aether_spellbooks." + set.id() + ".set_1")).withStyle(line));
        tooltip.add(Component.literal(" ").append(Component.translatable("tooltip.aether_spellbooks." + set.id() + ".set_2")).withStyle(line));
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public GeoArmorRenderer<?> supplyRenderer() {
        return new GenericCustomArmorRenderer<>(new AetherMageArmorModel(set.id()));
    }
}
