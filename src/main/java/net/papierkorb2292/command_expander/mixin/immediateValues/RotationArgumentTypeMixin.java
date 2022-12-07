package net.papierkorb2292.command_expander.mixin.immediateValues;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.PosArgument;
import net.minecraft.command.argument.RotationArgumentType;
import net.papierkorb2292.command_expander.commands.VariablePosArgument;
import net.papierkorb2292.command_expander.variables.immediate.ImmediateValue;
import net.papierkorb2292.command_expander.variables.immediate.ImmediateValueCompiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RotationArgumentType.class)
public class RotationArgumentTypeMixin {

    @Inject(
            method = "parse(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/command/argument/PosArgument;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void command_expander$allowImmediateValueRotation(StringReader stringReader, CallbackInfoReturnable<PosArgument> cir) throws CommandSyntaxException {
        if(stringReader.canRead() && stringReader.peek() == '(') {
            stringReader.skip();
            ImmediateValue value = ImmediateValueCompiler.compile(stringReader);
            stringReader.expect(')');
            cir.setReturnValue(new VariablePosArgument(value));
        }
    }
}
