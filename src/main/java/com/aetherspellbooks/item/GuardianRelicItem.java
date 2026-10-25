package com.aetherspellbooks.item;

import com.aetherspellbooks.dungeon.GuardianKind;
import com.aetherspellbooks.dungeon.Guardians;
import com.aetherspellbooks.entity.GuardianSoul;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Calls a defeated dungeon boss back: used on its soul, or anywhere inside its cleared room. Consumed only when the
 * ritual actually starts.
 */
public class GuardianRelicItem extends Item {
    private final GuardianKind kind;

    public GuardianRelicItem(GuardianKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public GuardianKind kind() {
        return kind;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            offer(server, player, stack, kind, null);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static void offer(ServerLevel level, Player player, ItemStack stack, GuardianKind kind, @Nullable GuardianSoul soul) {
        Guardians.OfferResult result = Guardians.offerRelic(level, player, kind, soul);
        player.displayClientMessage(Guardians.offerMessage(result, kind), true);
        if (result.success()) {
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("item.aether_spellbooks.guardian_relic.desc", kind.bossName()).withStyle(ChatFormatting.GRAY));
    }
}
