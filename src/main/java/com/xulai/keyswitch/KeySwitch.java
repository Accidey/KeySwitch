package com.xulai.keyswitch;

import com.mojang.logging.LogUtils;
import com.xulai.keyswitch.client.KeySwitchClient;
import com.xulai.keyswitch.client.KeySwitchConfigScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(KeySwitch.MODID)
public class KeySwitch {

    public static final String MODID = "keyswitch";

    public static final Logger LOGGER = LogUtils.getLogger();

    public KeySwitch() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            FMLJavaModLoadingContext.get().getModEventBus().addListener(KeySwitchClient::onRegisterKeyMappings);
            ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new KeySwitchConfigScreen(parent)));
        }
    }
}
