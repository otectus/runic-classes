package com.otectus.runicclasses;

import com.mojang.logging.LogUtils;
import com.otectus.runicclasses.command.RCCommands;
import com.otectus.runicclasses.config.RCCommonConfig;
import com.otectus.runicclasses.config.RCServerConfig;
import com.otectus.runicclasses.data.ClassIds;
import com.otectus.runicclasses.event.ClassLifecycleHandler;
import com.otectus.runicclasses.integration.irons.IronsSpellsBridge;
import com.otectus.runicclasses.integration.runicskills.RunicSkillsHooksInstaller;
import com.otectus.runicclasses.kit.KitDispatcher;
import com.otectus.runicclasses.kit.Kits;
import com.otectus.runicclasses.network.RCNetwork;
import com.otectus.runicclasses.registry.RCEffects;
import com.otectus.runicclasses.registry.RCItems;
import com.otectus.runicclasses.registry.RCPerks;
import com.otectus.runicclasses.registry.RCPowers;
import com.otectus.runicclasses.state.RCCapabilities;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Runic Classes: the class layer of the Runic suite.
 *
 * <p>A third Origins layer ({@code runicclasses:class}, order 20) follows Runic Races' heritage and race
 * layers; a class hard-wires into Runic Skills through the 2.4.0 hooks installed here at construction:
 * two Aptitudes start at 8 with caps raised by 8, six class Perks and a Mark, Seal and Crown appear for
 * members only, and re-picking with a Class Orb swaps all of it wholesale. See docs/plan.md.
 */
@Mod(ClassIds.MOD_ID)
public final class RunicClasses {

    public static final String MOD_ID = ClassIds.MOD_ID;
    public static final Logger LOGGER = LogUtils.getLogger();

    public RunicClasses() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, RCServerConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, RCCommonConfig.SPEC);

        RCItems.register(modBus);
        RCPerks.register(modBus);
        RCPowers.register(modBus);
        RCEffects.register(modBus);
        modBus.addListener(RCCapabilities::register);
        modBus.addListener(this::commonSetup);

        // Runic Skills reads these hooks on both sides from the first frame; install them now.
        RunicSkillsHooksInstaller.install();
        Kits.registerAll();

        IEventBus forge = MinecraftForge.EVENT_BUS;
        forge.addGenericListener(Entity.class, RCCapabilities::attach);
        forge.register(ClassLifecycleHandler.class);
        forge.register(KitDispatcher.class);
        forge.addListener(RCCommands::register);
        IronsSpellsBridge.init();

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.otectus.runicclasses.client.RCClient.init(modBus));
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            RCNetwork.register();
            Kits.commonSetup();
        });
    }
}
