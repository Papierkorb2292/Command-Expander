package net.papierkorb2292.command_expander.mixin.immediateValues;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.command.TeleportCommand;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.papierkorb2292.command_expander.commands.VariablePosArgument;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Iterator;

@Mixin(TeleportCommand.class)
public class TeleportCommandMixin {

    private static final ThreadLocal<Iterator<Vec3d>> command_expander$teleportPositionIterator = new ThreadLocal<>();
    private static final ThreadLocal<Iterator<Vec2f>> command_expander$teleportRotationIterator = new ThreadLocal<>();

    @ModifyExpressionValue(
            method = "execute(Lnet/minecraft/server/command/ServerCommandSource;Ljava/util/Collection;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/command/argument/PosArgument;Lnet/minecraft/command/argument/PosArgument;Lnet/minecraft/server/command/TeleportCommand$LookTarget;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Iterator;hasNext()Z"
            )
    )
    private static boolean command_expander$requireNextPositionAndRotationIteratorEntry(boolean original) {
        if(!original) {
            return false;
        }
        Iterator<Vec3d> pos = command_expander$teleportPositionIterator.get();
        if(pos != null && !pos.hasNext()) {
            return false;
        }
        Iterator<Vec2f> rot = command_expander$teleportRotationIterator.get();
        if(rot != null && !rot.hasNext()) {
            return false;
        }
        return true;
    }

    @ModifyVariable(
            method = "execute(Lnet/minecraft/server/command/ServerCommandSource;Ljava/util/Collection;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/command/argument/PosArgument;Lnet/minecraft/command/argument/PosArgument;Lnet/minecraft/server/command/TeleportCommand$LookTarget;)I",
            at = @At("STORE")
    )
    private static Vec3d command_expander$savePositionIterator(Vec3d vec3d) {
        command_expander$teleportPositionIterator.set(
                vec3d instanceof VariablePosArgument.StreamVec3d stream
                        ? stream.getStream().iterator()
                        : null
        );
        return vec3d;
    }

    @ModifyVariable(
            method = "execute(Lnet/minecraft/server/command/ServerCommandSource;Ljava/util/Collection;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/command/argument/PosArgument;Lnet/minecraft/command/argument/PosArgument;Lnet/minecraft/server/command/TeleportCommand$LookTarget;)I",
            at = @At("STORE")
    )
    private static Vec2f command_expander$saveRotationIterator(Vec2f vec2f) {
        command_expander$teleportRotationIterator.set(
                vec2f instanceof VariablePosArgument.StreamVec2f stream
                        ? stream.getStream().iterator()
                        : null
        );
        return vec2f;
    }

    @ModifyVariable(
            method = "execute(Lnet/minecraft/server/command/ServerCommandSource;Ljava/util/Collection;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/command/argument/PosArgument;Lnet/minecraft/command/argument/PosArgument;Lnet/minecraft/server/command/TeleportCommand$LookTarget;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Iterator;next()Ljava/lang/Object;",
                    ordinal = 0
            )
    )
    private static Vec3d command_expander$getNextPositionIteratorValue(Vec3d vec3d) {
        Iterator<Vec3d> iterator = command_expander$teleportPositionIterator.get();
        return iterator == null ? vec3d : iterator.next();
    }

    @ModifyVariable(
            method = "execute(Lnet/minecraft/server/command/ServerCommandSource;Ljava/util/Collection;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/command/argument/PosArgument;Lnet/minecraft/command/argument/PosArgument;Lnet/minecraft/server/command/TeleportCommand$LookTarget;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Iterator;next()Ljava/lang/Object;",
                    ordinal = 0
            )
    )
    private static Vec2f command_expander$getNextRotationIteratorValue(Vec2f vec2f) {
        Iterator<Vec2f> iterator = command_expander$teleportRotationIterator.get();
        return iterator == null ? vec2f : iterator.next();
    }

    @ModifyArg(
            method = "execute(Lnet/minecraft/server/command/ServerCommandSource;Ljava/util/Collection;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/command/argument/PosArgument;Lnet/minecraft/command/argument/PosArgument;Lnet/minecraft/server/command/TeleportCommand$LookTarget;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/text/TranslatableText;<init>(Ljava/lang/String;[Ljava/lang/Object;)V"
            )
    )
    private static String command_expander$replaceFeedbackWhenUsingVariablePosition(String original) {
        return command_expander$teleportPositionIterator.get() == null
                ? original
                : "Teleported %s entities to various places";
    }
}
