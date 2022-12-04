package net.papierkorb2292.command_expander.mixin.immediateValues;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.builder.ArgumentBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_expander.commands.VariablePosArgument;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Set;
import java.util.stream.Collectors;

@Mixin(ArgumentBuilder.class)
public class ArgumentBuilderMixin {

    @WrapOperation(
            method = "lambda$redirect$1",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Collections;singleton(Ljava/lang/Object;)Ljava/util/Set;"
            ),
            remap = false
    )
    private static Set<?> command_expander$forkStreamedRotationAndPosition(Object arg, Operation<Set<?>> op) {
        if (arg instanceof ServerCommandSource source) {
            if (source.getPosition() instanceof VariablePosArgument.StreamVec3d stream) {
                return stream.getStream().map(source::withPosition).collect(Collectors.toSet());
            }
            if (source.getRotation() instanceof VariablePosArgument.StreamVec2f stream) {
                return stream.getStream().map(source::withRotation).collect(Collectors.toSet());
            }
        }
        return op.call(arg);
    }
}
