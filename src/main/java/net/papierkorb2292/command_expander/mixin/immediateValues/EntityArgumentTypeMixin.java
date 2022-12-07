package net.papierkorb2292.command_expander.mixin.immediateValues;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.argument.EntityArgumentType;
import net.papierkorb2292.command_expander.variables.immediate.ImmediateValue;
import net.papierkorb2292.command_expander.variables.immediate.ImmediateValueCompiler;
import net.papierkorb2292.command_expander.variables.immediate.VariableEntitySelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityArgumentType.class)
public class EntityArgumentTypeMixin {

    @Inject(
            method = "parse(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/command/EntitySelector;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void command_expander$allowImmediateValueEntities(StringReader stringReader, CallbackInfoReturnable<EntitySelector> cir) throws CommandSyntaxException {
        if(!stringReader.canRead() || stringReader.peek() != '(') {
            return;
        }
        stringReader.skip();
        ImmediateValue value = ImmediateValueCompiler.compile(stringReader);
        stringReader.expect(')');
        cir.setReturnValue(new VariableEntitySelector(value));
    }
}
