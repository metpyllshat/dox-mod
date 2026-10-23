package dev.blackchannel;

import dev.blackchannel.logic.Bridges;
import dev.blackchannel.net.ModNetwork;
import dev.blackchannel.registry.ModContent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(BlackChannelMod.MODID)
public class BlackChannelMod {
    public static final String MODID = "blackchannel";
    public static final Logger LOGGER = LoggerFactory.getLogger("BlackChannel");

    public BlackChannelMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        ModContent.register(bus);
        bus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModNetwork.register();
            Bridges.init(MinecraftForge.EVENT_BUS);
            LOGGER.info("Black Channel ready. Hooks: {}", Bridges.hookSummary());
        });
    }
}
