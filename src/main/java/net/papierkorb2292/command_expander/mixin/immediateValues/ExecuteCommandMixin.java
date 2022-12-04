package net.papierkorb2292.command_expander.mixin.immediateValues;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.command.ExecuteCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.Vec3d;
import net.papierkorb2292.command_expander.commands.VariablePosArgument;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ExecuteCommand.class)
public abstract class ExecuteCommandMixin {

    @WrapOperation(
            method = "method_13268",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/command/ServerCommandSource;withLookingAt(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/server/command/ServerCommandSource;"
            ),
            remap = false
    )
    private static ServerCommandSource command_expander$supportMultipleFacingPositionalTargets(ServerCommandSource source, Vec3d pos, Operation<ServerCommandSource> op) {
        if(!(pos instanceof VariablePosArgument.StreamVec3d stream)) {
            return op.call(source, pos);
        }
        return source.withRotation(new VariablePosArgument.StreamVec2f(stream.getStream().map(
                vec3d -> op.call(source, vec3d).getRotation()
        )));
    }


}
