package com.rpgcore.data;

import com.rpgcore.RpgCoreMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, RpgCoreMod.MOD_ID);

    public static final Supplier<AttachmentType<PlayerRpgData>> PLAYER_RPG_DATA = ATTACHMENT_TYPES.register(
            "player_rpg_data",
            () -> AttachmentType.builder(() -> PlayerRpgData.EMPTY)
                    .serialize(PlayerRpgData.CODEC)
                    .copyOnDeath()
                    .build()
    );

    private ModAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
