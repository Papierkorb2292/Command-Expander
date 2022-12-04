package net.papierkorb2292.command_expander.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import net.minecraft.command.argument.PosArgument;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Texts;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.papierkorb2292.command_expander.variables.PosVariable;
import net.papierkorb2292.command_expander.variables.Variable;
import net.papierkorb2292.command_expander.variables.VariableHolder;
import net.papierkorb2292.command_expander.variables.VariableManager;
import net.papierkorb2292.command_expander.variables.immediate.ImmediateValue;
import net.papierkorb2292.command_expander.variables.immediate.ImmediateValueCompiler;

import java.util.Objects;
import java.util.stream.Stream;

public class VariablePosArgument implements PosArgument {

    private final ImmediateValue value;

    public VariablePosArgument(ImmediateValue value) {
        this.value = value;
    }

    @Override
    public Vec3d toAbsolutePos(ServerCommandSource source) {
        Either<VariableHolder, Stream<Variable>> result;
        try {
            result = value.calculate(new CommandContext<>(source, null, null, null, null, null, null, null, null, false));
        } catch (CommandSyntaxException e) {
            source.sendError(Texts.toText(e.getRawMessage()));
            return new StreamVec3d(Stream.empty());
        }
        return result.map(
                holder -> {
                    try {
                        PosVariable pos = (PosVariable) VariableManager.castVariable(PosVariable.PosVariableType.INSTANCE, holder.variable);
                        return pos == null ? new StreamVec3d(Stream.empty()) : new Vec3d(pos.getX().doubleValue(), pos.getY().doubleValue(), pos.getZ().doubleValue());
                    } catch (CommandSyntaxException e) {
                        source.sendError(Texts.toText(e.getRawMessage()));
                        return new StreamVec3d(Stream.empty());
                    }
                },
                stream -> new StreamVec3d(stream.map(var -> {
                    try {
                        PosVariable pos = (PosVariable) VariableManager.castVariable(PosVariable.PosVariableType.INSTANCE, var);
                        return pos == null ? null : new Vec3d(pos.getX().doubleValue(), pos.getY().doubleValue(), pos.getZ().doubleValue());
                    } catch (CommandSyntaxException e) {
                        source.sendError(Texts.toText(e.getRawMessage()));
                               return null;
                    }
                }).filter(Objects::nonNull))
        );
    }

    @Override
    public BlockPos toAbsoluteBlockPos(ServerCommandSource source) {
        Vec3d pos = toAbsolutePos(source);
        if(pos instanceof StreamVec3d stream) {
            return new StreamBlockPos(stream.getStream().map(
                    vec3d -> new BlockPos(vec3d.getX(), vec3d.getY(), vec3d.getZ())));
        }
        return new BlockPos(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public Vec2f toAbsoluteRotation(ServerCommandSource source) {
        Vec3d pos = toAbsolutePos(source);
        if(!(pos instanceof StreamVec3d stream)) {
            return new Vec2f((float) pos.getX(), (float) pos.getY());
        }
        return new StreamVec2f(stream.stream.map(vec3d -> new Vec2f((float) vec3d.getX(), (float) vec3d.getY())));
    }

    @Override
    public boolean isXRelative() {
        return false;
    }

    @Override
    public boolean isYRelative() {
        return false;
    }

    @Override
    public boolean isZRelative() {
        return false;
    }

    public static VariablePosArgument parse(StringReader reader) throws CommandSyntaxException {
        reader.expect('(');
        ImmediateValue value = ImmediateValueCompiler.compile(reader);
        reader.expect(')');
        return new VariablePosArgument(value);
    }

    // This class is needed to return multiple Vec3ds. It has to extend Vec3d, so it can be returned.
    // This is a bad idea and the best idea
    public static class StreamVec3d extends Vec3d {

        private final Stream<Vec3d> stream;

        public StreamVec3d(Stream<Vec3d> stream) {
            super(2292, 2292, 2292);
            this.stream = stream;
        }

        public Stream<Vec3d> getStream() {
            return stream;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof StreamVec3d other && other.stream.equals(stream);
        }
    }

    // Like StreamVec3d
    // This is used as a stream of rotations
    public static class StreamVec2f extends Vec2f {

        private final Stream<Vec2f> stream;

        public StreamVec2f(Stream<Vec2f> stream) {
            super(2292, 2292);
            this.stream = stream;
        }

        public Stream<Vec2f> getStream() {
            return stream;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof StreamVec2f other && other.stream.equals(stream);
        }
    }

    // Like StreamVec3d
    // This is used when a VariablePos is converted to a BlockPos
    public static class StreamBlockPos extends BlockPos {

        private final Stream<BlockPos> stream;

        public StreamBlockPos(Stream<BlockPos> stream) {
            super(2292, 2292, 2292);
            this.stream = stream;
        }

        public Stream<BlockPos> getStream() {
            return stream;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof StreamBlockPos other && other.stream.equals(stream);
        }
    }
}
