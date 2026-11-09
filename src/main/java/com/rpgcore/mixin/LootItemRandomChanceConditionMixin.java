package com.rpgcore.mixin;

import com.rpgcore.attributes.LuckLootProbability;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Applies Luck to vanilla random_chance loot predicates. */
@Mixin(value = LootItemRandomChanceCondition.class, remap = false)
public abstract class LootItemRandomChanceConditionMixin {
    @Redirect(
            method = "test",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/RandomSource;nextFloat()F",
                    remap = false
            ),
            remap = false
    )
    private float rpgcore$applyLuck(RandomSource random, LootContext context) {
        return LuckLootProbability.adjustRandomRoll(random.nextFloat(), context);
    }
}
