package com.otectus.runicclasses.client;

import com.otectus.runicclasses.data.ClassDataManager;
import com.otectus.runicclasses.data.ClassDefinition;
import com.otectus.runicclasses.data.ClassIds;
import com.otectus.runicclasses.state.ClassState;
import com.otectus.runicclasses.state.RCCapabilities;
import com.otectus.runicskills.client.screen.RunicSkillsScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * A small class badge in the corner of Runic Skills' Skills screen: the class icon and name, and a
 * tooltip naming the Class Orb. Added through {@code ScreenEvent.Init.Post}, the same injection Runic
 * Races uses for its Back button, so Runic Skills is not edited for it. Informational only: re-picking
 * is item-gated, so there is no button here.
 */
public final class SkillsScreenClassBadge extends AbstractWidget {

    private SkillsScreenClassBadge(int x, int y) {
        super(x, y, 96, 18, Component.empty());
        this.active = false;
    }

    static void attach(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof RunicSkillsScreen)) return;
        event.addListener(new SkillsScreenClassBadge(6, 6));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClassState state = RCCapabilities.get(mc.player);
        ClassDefinition def = null;
        if (state != null && state.hasAppliedClass()) {
            def = ClassDataManager.client().byId(ResourceLocation.tryParse(state.appliedClass));
        }
        Component name = def == null
                ? Component.translatable("screen.runicclasses.badge.classless")
                : Component.translatable("origin." + ClassIds.MOD_ID + "." + def.path() + ".name");
        ItemStack icon = new ItemStack(iconOf(def));
        graphics.renderItem(icon, getX(), getY());
        int color = def == null ? 0xBFA46A : def.color();
        graphics.drawString(mc.font, name, getX() + 20, getY() + 5, color, true);
        setTooltip(Tooltip.create(Component.translatable("screen.runicclasses.badge.class", name)
                .append("\n").append(Component.translatable("screen.runicclasses.badge.hint").withStyle(ChatFormatting.GRAY))));
    }

    private static Item iconOf(ClassDefinition def) {
        if (def == null || def.iconItem() == null) return Items.COMPASS;
        Item item = ForgeRegistries.ITEMS.getValue(def.iconItem());
        return item == null || item == Items.AIR ? Items.COMPASS : item;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }
}
