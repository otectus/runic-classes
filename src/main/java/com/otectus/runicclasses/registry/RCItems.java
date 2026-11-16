package com.otectus.runicclasses.registry;

import com.otectus.runicclasses.data.ClassIds;
import com.otectus.runicclasses.item.ClassOrbItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class RCItems {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ClassIds.MOD_ID);

    public static final RegistryObject<Item> CLASS_ORB = ITEMS.register("class_orb",
            () -> new ClassOrbItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    private RCItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(RCItems::creativeTabs);
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(CLASS_ORB);
    }
}
