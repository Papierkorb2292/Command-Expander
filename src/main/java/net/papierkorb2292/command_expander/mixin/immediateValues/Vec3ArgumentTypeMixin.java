package net.papierkorb2292.command_expander.mixin.immediateValues;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.argument.PosArgument;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.papierkorb2292.command_expander.commands.VariablePosArgument;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(Vec3ArgumentType.class)
public class Vec3ArgumentTypeMixin {

    @Inject(
            method = "parse(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/command/argument/PosArgument;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/argument/DefaultPosArgument;parse(Lcom/mojang/brigadier/StringReader;Z)Lnet/minecraft/command/argument/DefaultPosArgument;"
            ),
            cancellable = true
    )
    private void command_expander$allowImmediateValuePosition(StringReader reader, CallbackInfoReturnable<PosArgument> cir) throws CommandSyntaxException {
        if(reader.canRead() && reader.peek() == '(') {
            cir.setReturnValue(VariablePosArgument.parse(reader));
        }
    }

    @Inject(
            method = "listSuggestions",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/suggestion/SuggestionsBuilder;getRemaining()Ljava/lang/String;",
                    remap = false
            ),
            cancellable = true
    )
    private <S> void command_expander$preventImmediateValuePositionSuggestions(CommandContext<S> context, SuggestionsBuilder builder, CallbackInfoReturnable<CompletableFuture<Suggestions>> cir) {
        String remaining = builder.getRemaining();
        if(!remaining.isEmpty() && remaining.charAt(0) == '(') {
            cir.setReturnValue(Suggestions.empty());
        }
    }
}
