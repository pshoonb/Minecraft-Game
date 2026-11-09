package com.rpgcore.mixin;

import com.rpgcore.attributes.LuckLootProbability;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Adds one probabilistic extra roll to each eligible loot pool without
 * changing the pool's configured roll provider or entry weights.
 */
@Mixin(value = LootPool.class, remap = false)
public abstract class LootPoolMixin {
    @Redirect(
            method = "addRandomItems",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/storage/loot/providers/number/NumberProvider;getInt(Lnet/minecraft/world/level/storage/loot/LootContext;)I",
                    ordinal = 0,
                    remap = false
            ),
            remap = false
    )
    private int rpgcore$addLuckExtraRoll(NumberProvider rolls, LootContext context) {
        int vanillaRolls = rolls.getInt(context);
        if (vanillaRolls <= 0 || vanillaRolls == Integer.MAX_VALUE) {
            return vanillaRolls;
        }

        return LuckLootProbability.shouldAddExtraPoolRoll(context)
                ? vanillaRolls + 1
                : vanillaRolls;
    }
}
