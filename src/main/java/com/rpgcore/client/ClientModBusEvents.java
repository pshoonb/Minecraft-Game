package com.rpgcore.client;

import com.rpgcore.RpgCoreMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Retains the configurable key mapping for compatibility, but leaves it
 * unbound by default. The inventory button is the primary way to open the panel.
 */
@EventBusSubscriber(modid = RpgCoreMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModBusEvents {
    public static final KeyMapping OPEN_RPG_MENU = new KeyMapping(
            "key.rpgcore.open_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.rpgcore"
    );

    private ClientModBusEvents() {
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_RPG_MENU);
    }
}
