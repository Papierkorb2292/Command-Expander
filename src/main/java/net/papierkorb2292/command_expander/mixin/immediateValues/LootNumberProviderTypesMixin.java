package net.papierkorb2292.command_expander.mixin.immediateValues;

import net.minecraft.loot.provider.number.LootNumberProvider;
import net.minecraft.loot.provider.number.LootNumberProviderType;
import net.minecraft.loot.provider.number.LootNumberProviderTypes;
import net.minecraft.util.JsonSerializer;
import net.papierkorb2292.command_expander.variables.immediate.VariableLootNumberProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LootNumberProviderTypes.class)
public abstract class LootNumberProviderTypesMixin {

    private static @Shadow LootNumberProviderType register(String id, JsonSerializer<? extends LootNumberProvider> jsonSerializer) {
        throw new AssertionError();
    }

    static {
        VariableLootNumberProvider.register(LootNumberProviderTypesMixin::register);
    }
}
