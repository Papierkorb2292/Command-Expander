package net.papierkorb2292.command_expander.mixin.immediateValues;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.NbtCompoundArgumentType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtEnd;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_expander.mixin_method_interfaces.OptionalImmediateValueSupporter;
import net.papierkorb2292.command_expander.variables.immediate.NbtProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NbtCompoundArgumentType.class)
public class NbtCompoundArgumentTypeMixin {

    @ModifyReturnValue(
            method = "getNbtCompound",
            at = @At("RETURN")
    )
    private static <S> NbtCompound command_expander$applyContextToNbtProvider(NbtCompound original, CommandContext<S> context, String name) {
        if(original instanceof NbtProvider<?> provider) {
            if(context.getSource() instanceof ServerCommandSource) {
                //noinspection unchecked
                return (NbtCompound) provider.applyContext((CommandContext<ServerCommandSource>) context);
            }
            return new NbtCompound();
        }
        return original;
    }

    @ModifyReceiver(
            method = "parse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/StringNbtReader;parseCompound()Lnet/minecraft/nbt/NbtCompound;"
            )
    )
    private StringNbtReader command_expander$allowImmediateValuesForReader(StringNbtReader original) {
        ((OptionalImmediateValueSupporter)original).command_expander$setSupportImmediateValue(true);
        return original;
    }
}
