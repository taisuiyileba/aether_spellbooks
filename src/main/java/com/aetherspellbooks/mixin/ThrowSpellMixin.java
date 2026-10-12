package com.aetherspellbooks.mixin;

import com.aetherspellbooks.compat.SliderSpellCompat;
import com.aetherspellbooks.compat.ThrownToolDamage;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.spells.evocation.ThrowSpell;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ThrowSpell.class, remap = false)
public abstract class ThrowSpellMixin extends AbstractSpell {
    @Inject(method = "getDamage", at = @At("HEAD"), cancellable = true)
    private void aetherSpellbooks$useThrownToolDamage(int spellLevel, LivingEntity caster, CallbackInfoReturnable<Float> cir) {
        if (caster != null) {
            ItemStack tool = ThrownToolDamage.castingItem(caster);
            if (SliderSpellCompat.isMiningTool(tool)) {
                cir.setReturnValue(getSpellPower(spellLevel, caster) + ThrownToolDamage.attackDamage(caster, tool));
            }
        }
    }

    @Inject(method = "getDamageText", at = @At("HEAD"), cancellable = true)
    private void aetherSpellbooks$describeThrownToolDamage(int spellLevel, LivingEntity caster, CallbackInfoReturnable<String> cir) {
        if (caster != null) {
            ItemStack tool = ThrownToolDamage.castingItem(caster);
            if (SliderSpellCompat.isMiningTool(tool)) {
                float attack = ThrownToolDamage.attackDamage(caster, tool);
                cir.setReturnValue(Utils.stringTruncation(getSpellPower(spellLevel, caster) + attack, 1)
                        + " (+" + Utils.stringTruncation(attack, 1) + ")");
            }
        }
    }

    @ModifyArg(method = "onCast", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/entity/spells/thrown_item/ThrownItemProjectile;<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)V"), index = 1)
    private ItemStack aetherSpellbooks$snapshotThrownTool(ItemStack item) {
        return SliderSpellCompat.isMiningTool(item) ? item.copy() : item;
    }
}
