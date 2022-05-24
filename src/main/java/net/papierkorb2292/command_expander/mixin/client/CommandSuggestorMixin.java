package net.papierkorb2292.command_expander.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.CommandSuggestor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CommandSuggestor.class)
@Environment(EnvType.CLIENT)
public class CommandSuggestorMixin {

    @Redirect(
            method = "refresh()V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/gui/screen/CommandSuggestor;completingSuggestions:Z",
                    ordinal = 0
            )
    )
    private boolean refreshWhileCompletingSuggestions(CommandSuggestor instance) {
        return false;
    }
}
