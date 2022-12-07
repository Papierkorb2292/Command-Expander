package net.papierkorb2292.command_expander.mixin.immediateValues;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.PosArgument;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.papierkorb2292.command_expander.commands.VariablePosArgument;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(BlockPosArgumentType.class)
public class BlockPosArgumentTypeMixin {

    @Inject(
            method = "parse(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/command/argument/PosArgument;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/argument/DefaultPosArgument;parse(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/command/argument/DefaultPosArgument;"
            ),
            cancellable = true
    )
    private void command_expander$parseImmediateValuePosition(StringReader reader, CallbackInfoReturnable<PosArgument> cir) throws CommandSyntaxException {
        if(reader.canRead() && reader.peek() == '(') {
            cir.setReturnValue(VariablePosArgument.parse(reader));
        }
    }

    @WrapOperation(
            method = "getLoadedBlockPos",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;isChunkLoaded(Lnet/minecraft/util/math/BlockPos;)Z"
            )
    )
    private static boolean command_expander$allowUnloadedImmediateValuePosition(ServerWorld world, BlockPos pos, Operation<Boolean> op) {
        if(pos instanceof VariablePosArgument.StreamBlockPos) {
            return true;
        }
        return op.call(world, pos);
    }

    @WrapOperation(
            method = "getLoadedBlockPos",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;isInBuildLimit(Lnet/minecraft/util/math/BlockPos;)Z"
            )
    )
    private static boolean command_expander$allowOutOfWorldImmediateValuePosition(ServerWorld world, BlockPos pos, Operation<Boolean> op) {
        if(pos instanceof VariablePosArgument.StreamBlockPos) {
            return true;
        }
        return op.call(world, pos);
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
