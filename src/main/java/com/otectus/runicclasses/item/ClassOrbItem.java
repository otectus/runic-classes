package com.otectus.runicclasses.item;

import com.otectus.runicclasses.service.RepickService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Class Orb: re-opens the class screen and nothing else (owner decision: divided orbs). Heritage
 * and race have their own orb in Runic Races; Origins' all-layer Orb of Origin stays an operator tool.
 * Consumed on use unless the player is in creative, like Origins' orb.
 */
public class ClassOrbItem extends Item {

    public ClassOrbItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            RepickService.Result result = RepickService.repick(serverPlayer, true);
            if (!result.ok()) {
                serverPlayer.displayClientMessage(result.refusal().copy().withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(stack);
            }
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.runicclasses.class_orb.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.runicclasses.class_orb.tooltip.cap").withStyle(ChatFormatting.DARK_GRAY));
    }
}
