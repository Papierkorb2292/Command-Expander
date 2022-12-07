package net.papierkorb2292.command_expander.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChatInputSuggestor.class)
@Environment(EnvType.CLIENT)
public class ChatInputSuggestorMixin {

    @Redirect(
            method = "refresh()V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/gui/screen/ChatInputSuggestor;completingSuggestions:Z",
                    ordinal = 0
            )
    )
    private boolean refreshWhileCompletingSuggestions(ChatInputSuggestor instance) {
        return false;
    }
}
