package net.papierkorb2292.command_expander.mixin.immediateValues;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.SummonCommand;
import net.minecraft.text.Texts;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.papierkorb2292.command_expander.CommandExpander;
import net.papierkorb2292.command_expander.commands.VariablePosArgument;
import net.papierkorb2292.command_expander.variables.immediate.ImmediateValueIdentifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Iterator;
import java.util.stream.Stream;

@Mixin(SummonCommand.class)
public class SummonCommandMixin {

    @WrapOperation(
            method = "method_13691",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/command/SummonCommand;execute(Lnet/minecraft/server/command/ServerCommandSource;Lnet/minecraft/util/Identifier;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/nbt/NbtCompound;Z)I",
                    remap = true
            ),
            remap = false
    )
    private static int command_expander$allowImmediateValueEntitySummonSimple(ServerCommandSource source, Identifier entity, Vec3d pos, NbtCompound nbt, boolean initialize, Operation<Integer> op, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
        if(entity instanceof ImmediateValueIdentifier variableEntity) {
            Either<Identifier, Stream<Identifier>> id = variableEntity.calculate(cc);
            return id.map(
                    singleId -> op.call(source, singleId, pos, nbt, initialize),
                    stream -> stream.mapToInt(streamId -> {
                        try {
                            return CommandExpander.<Integer, CommandSyntaxException>callThrowingWrapOperation(op, source, streamId, pos, nbt, initialize);
                        } catch (CommandSyntaxException e) {
                            cc.getSource().sendError(Texts.toText(e.getRawMessage()));
                            return 0;
                        }
                    }).sum()
            );
        }
        return op.call(source, entity, pos, nbt, initialize);
    }

    @WrapOperation(
            method = "method_13689",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/command/SummonCommand;execute(Lnet/minecraft/server/command/ServerCommandSource;Lnet/minecraft/util/Identifier;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/nbt/NbtCompound;Z)I",
                    remap = true
            ),
            remap = false
    )
    private static int command_expander$allowImmediateValueEntitySummonWithPos(ServerCommandSource source, Identifier entity, Vec3d pos, NbtCompound nbt, boolean initialize, Operation<Integer> op, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
        Either<Identifier, Stream<Identifier>> entityId = entity instanceof ImmediateValueIdentifier variableEntity ? variableEntity.calculate(cc) : Either.left(entity);
        Either<Vec3d, Stream<Vec3d>> entityPos = pos instanceof VariablePosArgument.StreamVec3d stream ? Either.right(stream.getStream()) : Either.left(pos);

        return entityId.map(
                singleId -> entityPos.map(
                        // CAUTION: Throws CommandSyntaxException
                        singlePos -> op.call(source, singleId, singlePos, nbt, initialize),
                        streamPos -> streamPos.mapToInt(
                                streamPosContent -> {
                                    try {
                                        return CommandExpander.<Integer, CommandSyntaxException>callThrowingWrapOperation(op, source, singleId, streamPosContent, nbt, initialize);
                                    } catch(CommandSyntaxException e) {
                                        cc.getSource().sendError(Texts.toText(e.getRawMessage()));
                                        return 0;
                                    }
                                }
                        ).sum()
                ),
                streamId -> entityPos.map(
                        singlePos -> streamId.mapToInt(
                                streamIdContent -> {
                                    try {
                                        return CommandExpander.<Integer, CommandSyntaxException>callThrowingWrapOperation(op, source, streamIdContent, singlePos, nbt, initialize);
                                    } catch(CommandSyntaxException e) {
                                        cc.getSource().sendError(Texts.toText(e.getRawMessage()));
                                        return 0;
                                    }
                                }
                        ).sum(),
                        streamPos -> {
                            Iterator<Identifier> ids = streamId.iterator();
                            Iterator<Vec3d> positions = streamPos.iterator();

                            int sum = 0;

                            while(ids.hasNext() && positions.hasNext()) {
                                try {
                                    sum += CommandExpander.<Integer, CommandSyntaxException>callThrowingWrapOperation(op, source, ids.next(), positions.next(), nbt, initialize);
                                } catch(CommandSyntaxException e) {
                                    cc.getSource().sendError(Texts.toText(e.getRawMessage()));
                                }
                            }

                            return sum;
                        }
                )
        );
    }

    @WrapOperation(
            method = "method_13692",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/command/SummonCommand;execute(Lnet/minecraft/server/command/ServerCommandSource;Lnet/minecraft/util/Identifier;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/nbt/NbtCompound;Z)I",
                    remap = true
            ),
            remap = false
    )
    private static int command_expander$allowImmediateValueEntitySummonWithPosAndNbt(ServerCommandSource source, Identifier entity, Vec3d pos, NbtCompound nbt, boolean initialize, Operation<Integer> op, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
        return command_expander$allowImmediateValueEntitySummonWithPos(source, entity, pos, nbt, initialize, op, cc);
    }
}
