package net.papierkorb2292.command_expander.mixin.immediateValues;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.block.pattern.CachedBlockPosition;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.BlockStateArgument;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.SetBlockCommand;
import net.minecraft.text.Texts;
import net.minecraft.util.math.BlockPos;
import net.papierkorb2292.command_expander.commands.VariablePosArgument;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

@Mixin(SetBlockCommand.class)
public class SetBlockCommandMixin {

    @WrapOperation(
            method = "method_13622",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/command/SetBlockCommand;execute(Lnet/minecraft/server/command/ServerCommandSource;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/command/argument/BlockStateArgument;Lnet/minecraft/server/command/SetBlockCommand$Mode;Ljava/util/function/Predicate;)I",
                    remap = true
            ),
            remap = false
    )
    private static int command_expander$allowImmediateValuePosition(ServerCommandSource source, BlockPos pos, BlockStateArgument block, SetBlockCommand.Mode mode, @Nullable Predicate<CachedBlockPosition> condition, Operation<Integer> op, CommandContext<ServerCommandSource> cc) {
        return command_expander$allowImmediateValuePositionHandler(source, pos, block, mode, condition, op, cc);
    }

    @WrapOperation(
            method = "method_13625",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/command/SetBlockCommand;execute(Lnet/minecraft/server/command/ServerCommandSource;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/command/argument/BlockStateArgument;Lnet/minecraft/server/command/SetBlockCommand$Mode;Ljava/util/function/Predicate;)I",
                    remap = true
            ),
            remap = false
    )
    private static int command_expander$allowImmediateValuePositionDestroy(ServerCommandSource source, BlockPos pos, BlockStateArgument block, SetBlockCommand.Mode mode, @Nullable Predicate<CachedBlockPosition> condition, Operation<Integer> op, CommandContext<ServerCommandSource> cc) {
        return command_expander$allowImmediateValuePositionHandler(source, pos, block, mode, condition, op, cc);
    }

    @WrapOperation(
            method = "method_13621",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/command/SetBlockCommand;execute(Lnet/minecraft/server/command/ServerCommandSource;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/command/argument/BlockStateArgument;Lnet/minecraft/server/command/SetBlockCommand$Mode;Ljava/util/function/Predicate;)I",
                    remap = true
            ),
            remap = false
    )
    private static int command_expander$allowImmediateValuePositionKeep(ServerCommandSource source, BlockPos pos, BlockStateArgument block, SetBlockCommand.Mode mode, @Nullable Predicate<CachedBlockPosition> condition, Operation<Integer> op, CommandContext<ServerCommandSource> cc) {
        return command_expander$allowImmediateValuePositionHandler(source, pos, block, mode, condition, op, cc);
    }

    @WrapOperation(
            method = "method_13626",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/command/SetBlockCommand;execute(Lnet/minecraft/server/command/ServerCommandSource;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/command/argument/BlockStateArgument;Lnet/minecraft/server/command/SetBlockCommand$Mode;Ljava/util/function/Predicate;)I",
                    remap = true
            ),
            remap = false
    )
    private static int command_expander$allowImmediateValuePositionReplace(ServerCommandSource source, BlockPos pos, BlockStateArgument block, SetBlockCommand.Mode mode, @Nullable Predicate<CachedBlockPosition> condition, Operation<Integer> op, CommandContext<ServerCommandSource> cc) {
        return command_expander$allowImmediateValuePositionHandler(source, pos, block, mode, condition, op, cc);
    }

    @SuppressWarnings("deprecation") // isChunkLoaded is deprecated
    private static int command_expander$allowImmediateValuePositionHandler(ServerCommandSource source, BlockPos pos, BlockStateArgument block, SetBlockCommand.Mode mode, @Nullable Predicate<CachedBlockPosition> condition, Operation<Integer> op, CommandContext<ServerCommandSource> cc) {
        if(pos instanceof VariablePosArgument.StreamBlockPos stream) {
            return stream.getStream().mapToInt(streamedPos -> {
                try {
                    if (!cc.getSource().getWorld().isChunkLoaded(streamedPos)) {
                        throw BlockPosArgumentType.UNLOADED_EXCEPTION.create();
                    }
                    if (!cc.getSource().getWorld().isInBuildLimit(streamedPos)) {
                        throw BlockPosArgumentType.OUT_OF_WORLD_EXCEPTION.create();
                    }
                    return op.call(source, streamedPos, block, mode, condition);
                } catch(CommandSyntaxException e) {
                    // This only works because 'UNLOADED_EXCEPTION' and 'OUR_OF_WORLD_EXCEPTION' are explicitly thrown.
                    // 'op.call' could also throw a CommandSyntaxException, that needs to be caught. If the explicit
                    // throws are removed, the exception type needs to be changed to 'Exception' and checked with
                    // 'instanceof'
                    cc.getSource().sendError(Texts.toText(e.getRawMessage()));
                    return 0;
                }
            }).sum();
        }
        return op.call(source, pos, block, mode, condition);
    }
}
