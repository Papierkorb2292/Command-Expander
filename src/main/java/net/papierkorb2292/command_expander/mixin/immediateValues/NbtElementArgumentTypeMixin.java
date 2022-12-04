package net.papierkorb2292.command_expander.mixin.immediateValues;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.NbtElementArgumentType;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtEnd;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_expander.mixin_method_interfaces.OptionalImmediateValueSupporter;
import net.papierkorb2292.command_expander.variables.immediate.NbtProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NbtElementArgumentType.class)
public class NbtElementArgumentTypeMixin {

    @ModifyReturnValue(
            method = "getNbtElement",
            at = @At("RETURN")
    )
    private static <S> NbtElement command_expander$applyContextToNbtProvider(NbtElement original, CommandContext<S> context, String name) {
        if(original instanceof NbtProvider<?> provider) {
            if(context.getSource() instanceof ServerCommandSource) {
                //noinspection unchecked
                return provider.applyContext((CommandContext<ServerCommandSource>) context);
            }
            return NbtEnd.INSTANCE;
        }
        return original;
    }

    @ModifyReceiver(
            method = "parse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/StringNbtReader;parseElement()Lnet/minecraft/nbt/NbtElement;"
            )
    )
    private StringNbtReader command_expander$allowImmediateValuesForReader(StringNbtReader original) {
        ((OptionalImmediateValueSupporter)original).command_expander$setSupportImmediateValue(true);
        return original;
    }
}
