package net.papierkorb2292.command_expander.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.papierkorb2292.command_expander.CommandExpander;
import net.papierkorb2292.command_expander.variables.*;
import net.papierkorb2292.command_expander.variables.path.VariablePath;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class VarCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, boolean isDedicated) {
        dispatcher.register(
                CommandManager.literal("var")
                        .requires(source -> CommandExpander.isFeatureEnabled(source.getServer(), CommandExpander.VARIABLE_FEATURE))
                        .then(CommandManager.literal("add")
                                .then(CommandManager.argument("id", VariableNameArgumentType.variableName())
                                        .then(CommandManager.argument("type", VariableTypeArgumentType.variableType(false))
                                                .executes(context -> {
                                                    VariableIdentifier id = VariableNameArgumentType.getVariableName(context, "id");
                                                        CommandExpander.getVariableManager(context).add(
                                                                id,
                                                                VariableTypeArgumentType.getType(context, "type"));
                                                    context.getSource().sendFeedback(Text.of(String.format("Successfully added variable '%s'", id)), true);
                                                    return 1;
                                                })))
                        )
                        .then(
                                CommandManager.literal("remove")
                                        .then(CommandManager.argument("id", VariablePathArgumentType.variablePath())
                                            .executes(context -> {
                                                int result = VariablePathArgumentType.getVariablePath(context, "id").remove(context);
                                                context.getSource().sendFeedback(Text.of(String.format("Successfully removed %s %s", result, result == 1 ? "variable" : "variables")), true);
                                                return result;
                                            })))
                        .then(
                                CommandManager.literal("get")
                                        .then(CommandManager.argument("value", VariableImmediateValueArgumentType.variableImmediateValue())
                                                .executes(context ->
                                                        VariableImmediateValueArgumentType.getImmediateValue(context, "value").calculate(context).map(
                                                            holder -> {
                                                                Variable var = holder.variable;
                                                                sendGetFeedback(context, var);
                                                                return var == null ? 0 : var.intValue();
                                                            },
                                                            stream -> stream.mapToInt(var -> {
                                                                sendGetFeedback(context, var);
                                                                return var == null ? 0 : var.intValue();
                                                            }).sum()
                                                )
                                )))
                        .then(
                                CommandManager.literal("set")
                                        .then(CommandManager.argument("id", VariablePathArgumentType.variablePath())
                                                .then(CommandManager.argument("value", VariableImmediateValueArgumentType.variableImmediateValue())
                                                        .executes(context -> {
                                                            int result = VariablePathArgumentType.getVariablePath(context, "id")
                                                                    .set(
                                                                            VariableImmediateValueArgumentType.getImmediateValue(context, "value").calculate(context),
                                                                            context);
                                                            context.getSource().sendFeedback(Text.of(String.format("Successfully set %s %s", result, result == 1 ? "variable" : "variables")), true);
                                                            return result;
                                                        })))
                        )
                        .then(
                                CommandManager.literal("bind")
                                        .then(CommandManager.argument("id", VariablePathArgumentType.variablePath())
                                                .then(CommandManager.argument("criteria", MultiScoreboardCriterionArgumentType.scoreboardCriterion())
                                                        .executes(context -> {
                                                            List<String> criteria = MultiScoreboardCriterionArgumentType.getScoreboardCriteria(context, "criteria");
                                                            VariablePath path = VariablePathArgumentType.getVariablePath(context, "id");
                                                            int result = CommandExpander.getVariableManager(context).bindCriteria(path, criteria, context);
                                                            context.getSource().sendFeedback(Text.of(String.format("Successfully added %s %s", result, result == 1 ? "binding" : "bindings")), true);
                                                            return result;
                                                        })))
                        )
                        .then(
                                CommandManager.literal("unbind")
                                        .then(CommandManager.argument("id", VariablePathArgumentType.variablePath())
                                                .then(CommandManager.argument("criteria", MultiScoreboardCriterionArgumentType.scoreboardCriterion())
                                                    .executes(context -> {
                                                        List<String> criteria = MultiScoreboardCriterionArgumentType.getScoreboardCriteria(context, "criteria");
                                                        VariablePath path = VariablePathArgumentType.getVariablePath(context, "id");
                                                        int result = CommandExpander.getVariableManager(context).unbindCriteria(path, criteria, context);
                                                        context.getSource().sendFeedback(Text.of(String.format("Successfully removed %s %s", result, result == 1 ? "binding" : "bindings")), true);
                                                        return result;
                                                    })))
                        )
                        .then(
                                CommandManager.literal("iterator")
                                        .then(addIteratorOptions(CommandManager.argument("id", VariablePathArgumentType.variablePath())))
                        ));
    }

    private static final Text GET_FEEDBACK = Text.of("Variable has the following value: ");
    private static final List<Pair<String, Consumer<LiteralArgumentBuilder<ServerCommandSource>>>> ITERATORS;

    private static <T extends ArgumentBuilder<ServerCommandSource, T>> T addIteratorOptions(T builder) {
        for(Pair<String, Consumer<LiteralArgumentBuilder<ServerCommandSource>>> it : ITERATORS) {
            LiteralArgumentBuilder<ServerCommandSource> literal = CommandManager.literal(it.getLeft());
            it.getRight().accept(literal);
            builder.then(literal);
        }
        return builder;
    }

    private static void sendGetFeedback(CommandContext<ServerCommandSource> cc, Variable var) {
        cc.getSource().sendFeedback(GET_FEEDBACK.copy().append(CommandExpander.buildCopyableText(var == null ? "null" : var.stringValue())), false);
    }

    private static <T extends ArgumentBuilder<ServerCommandSource, T>> T addHollowOption(T builder, Function<Boolean, Command<ServerCommandSource>> commandSupplier) {
        return builder
                .executes(commandSupplier.apply(false))
                .then(
                        CommandManager.literal("hollow")
                                .executes(commandSupplier.apply(true))
                );
    }

    private static BiFunction<Integer, Integer, PosVariable> get2DResultSupplier(Vec3d pos, Direction.Axis planeAxis) {
        return switch(planeAxis) {
            case X -> (y, z) -> new PosVariable(0 + pos.x, y + pos.y, z + pos.z);
            case Y -> (x, z) -> new PosVariable(x + pos.x, 0 + pos.y, z + pos.z);
            case Z -> (x, y) -> new PosVariable(x + pos.x, y + pos.y, 0 + pos.z);
        };
    }

    private static final DynamicCommandExceptionType NEGATIVE_ITERATOR_LENGTH_EXCEPTION = new DynamicCommandExceptionType(length -> new LiteralMessage(String.format("Invalid iterator length: %s, length can't be negative", length)));

    private static <T extends ArgumentBuilder<ServerCommandSource, T>> T add2DIterator(T builder, Function<IteratorData2d, IteratorVariable.Iterator> supplier) {
        return builder.then(CommandManager.argument("axis", AxisArgumentType.axis())
                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                        .then(CommandManager.argument("length1", IntegerArgumentType.integer())
                                .then(
                                        addHollowOption(
                                                CommandManager.argument("length2", IntegerArgumentType.integer()),
                                                isHollow -> context -> {
                                                    Direction.Axis axis = AxisArgumentType.getAxis(context, "axis");
                                                    int length1 = IntegerArgumentType.getInteger(context, "length1") - 1;
                                                    if(length1 < 0) {
                                                        throw NEGATIVE_ITERATOR_LENGTH_EXCEPTION.create(length1);
                                                    }
                                                    int length2 = IntegerArgumentType.getInteger(context, "length2") - 1;
                                                    if(length2 < 0) {
                                                        throw NEGATIVE_ITERATOR_LENGTH_EXCEPTION.create(length1);
                                                    }
                                                    BlockPos pos = BlockPosArgumentType.getBlockPos(context, "pos");
                                                    return VariablePathArgumentType.getVariablePath(context, "id").set(
                                                            Either.left(
                                                                    new VariableHolder(
                                                                            new IteratorVariable(
                                                                                    supplier.apply(new IteratorData2d(pos, axis, length1, length2, isHollow)),
                                                                                    new IteratorVariable.IteratorVariableType(PosVariable.PosVariableType.INSTANCE)))),
                                                            context
                                                    );
                                                })))));
    };

    private static <T extends ArgumentBuilder<ServerCommandSource, T>> T add3DIterator(T builder, Function<IteratorData3d, IteratorVariable.Iterator> supplier) {
        return builder.then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                .then(CommandManager.argument("length1", IntegerArgumentType.integer())
                        .then(CommandManager.argument("length2", IntegerArgumentType.integer())
                                .then(
                                        addHollowOption(
                                                CommandManager.argument("length3", IntegerArgumentType.integer()),
                                                isHollow -> context -> {
                                                    int length1 = IntegerArgumentType.getInteger(context, "length1") - 1;
                                                    if(length1 < 0) {
                                                        throw NEGATIVE_ITERATOR_LENGTH_EXCEPTION.create(length1);
                                                    }
                                                    int length2 = IntegerArgumentType.getInteger(context, "length2") - 1;
                                                    if(length2 < 0) {
                                                        throw NEGATIVE_ITERATOR_LENGTH_EXCEPTION.create(length1);
                                                    }
                                                    int length3 = IntegerArgumentType.getInteger(context, "length3") - 1;
                                                    if(length3 < 0) {
                                                        throw NEGATIVE_ITERATOR_LENGTH_EXCEPTION.create(length1);
                                                    }
                                                    BlockPos pos = BlockPosArgumentType.getBlockPos(context, "pos");
                                                    return VariablePathArgumentType.getVariablePath(context, "id").set(
                                                            Either.left(
                                                                    new VariableHolder(
                                                                            new IteratorVariable(
                                                                                    supplier.apply(new IteratorData3d(pos, length1, length2, length3, isHollow)),
                                                                                    new IteratorVariable.IteratorVariableType(PosVariable.PosVariableType.INSTANCE)))),
                                                            context
                                                    );
                                                })))));
    };

    static {
        //TODO: Support immediate values
        ITERATORS = new ArrayList<>();
        ITERATORS.add(new Pair<>("rectangle", builder -> add2DIterator(builder, RectangleIterator::new)));
        ITERATORS.add(new Pair<>("ellipse", builder -> add2DIterator(builder, EllipseIterator::new)));
        ITERATORS.add(new Pair<>("cuboid", builder -> add3DIterator(builder, CuboidIterator::new)));
        ITERATORS.add(new Pair<>("ellipsoid", builder -> add3DIterator(builder, EllipsoidIterator::new)));
    }

    public static final class IteratorData2d {

        private final Direction.Axis axis;
        private final BiFunction<Integer, Integer, PosVariable> resultSupplier;
        private final int length1;
        private final int length2;
        private final boolean hollow;
        private final BlockPos pos;

        private int currentIndex1;
        private int currentIndex2;

        public IteratorData2d(BlockPos pos, Direction.Axis axis, int length1, int length2, boolean hollow) {
            this(pos, axis, length1, length2, hollow, -1, 0);
        }

        public IteratorData2d(BlockPos pos, Direction.Axis axis, int length1, int length2, boolean hollow, int currentIndex1, int currentIndex2) {
            this.pos = pos;
            this.axis = axis;
            resultSupplier = get2DResultSupplier(new Vec3d(pos.getX(), pos.getY(), pos.getZ()), axis);
            this.length1 = length1;
            this.length2 = length2;
            this.hollow = hollow;
            this.currentIndex1 = currentIndex1;
            this.currentIndex2 = currentIndex2;
        }


        public IteratorData2d copy() {
            return new IteratorData2d(pos, axis, length1, length2, hollow, currentIndex1, currentIndex2);
        }

        private static final Codec<IteratorData2d> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<com.mojang.datafixers.util.Pair<IteratorData2d, T>> decode(DynamicOps<T> ops, T input) {
                return ops.getMap(input).flatMap(
                        map -> ops.getNumberValue(map.get("axis")).flatMap(
                                axis -> ops.getNumberValue(map.get("length1")).flatMap(
                                        length1 -> ops.getNumberValue(map.get("length2")).flatMap(
                                                length2 -> ops.getBooleanValue(map.get("hollow")).flatMap(
                                                        hollow -> BlockPos.CODEC.decode(ops, map.get("pos")).flatMap(
                                                                posPair -> ops.getNumberValue(map.get("currentIndex1")).flatMap(
                                                                        currentIndex1 -> ops.getNumberValue(map.get("currentIndex2")).map(
                                                                                currentIndex2 -> com.mojang.datafixers.util.Pair.of(new IteratorData2d(posPair.getFirst(), Direction.Axis.VALUES[axis.intValue()], length1.intValue(), length2.intValue(), hollow, currentIndex1.intValue(), currentIndex2.intValue()), ops.empty()))
                                                                )))))));
            }

            @Override
            public <T> DataResult<T> encode(IteratorData2d input, DynamicOps<T> ops, T prefix) {
                return ops.mapBuilder()
                        .add("axis", ops.createInt(input.axis.ordinal()))
                        .add("length1", ops.createInt(input.length1))
                        .add("length2", ops.createInt(input.length2))
                        .add("hollow", ops.createBoolean(input.hollow))
                        .add("pos", BlockPos.CODEC.encode(input.pos, ops, ops.empty()))
                        .add("currentIndex1", ops.createInt(input.currentIndex1))
                        .add("currentIndex2", ops.createInt(input.currentIndex2))
                        .build(prefix);
            }
        };
    }

    public static final class IteratorData3d {

        private final int lengthX;
        private final int lengthY;
        private final int lengthZ;
        private final boolean hollow;
        private final BlockPos pos;

        private int currentIndexX;
        private int currentIndexY;
        private int currentIndexZ;

        public IteratorData3d(BlockPos pos, int length1, int length2, int length3, boolean hollow) {
            this(pos, length1, length2, length3, hollow, -1, 0, 0);
        }

        public IteratorData3d(BlockPos pos, int length1, int length2, int length3, boolean hollow, int currentIndexX, int currentIndexY, int currentIndexZ) {
            this.pos = pos;
            this.lengthX = length1;
            this.lengthY = length2;
            this.lengthZ = length3;
            this.hollow = hollow;
            this.currentIndexX = currentIndexX;
            this.currentIndexY = currentIndexY;
            this.currentIndexZ = currentIndexZ;
        }


        public IteratorData3d copy() {
            return new IteratorData3d(pos, lengthX, lengthY, lengthZ, hollow, currentIndexX, currentIndexY, currentIndexZ);
        }

        private static final Codec<IteratorData3d> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<com.mojang.datafixers.util.Pair<IteratorData3d, T>> decode(DynamicOps<T> ops, T input) {
                return ops.getMap(input).flatMap(
                        map -> ops.getNumberValue(map.get("lengthX")).flatMap(
                                lengthX -> ops.getNumberValue(map.get("lengthY")).flatMap(
                                        lengthY -> ops.getNumberValue(map.get("lengthZ")).flatMap(
                                                lengthZ -> ops.getBooleanValue(map.get("hollow")).flatMap(
                                                        hollow -> BlockPos.CODEC.decode(ops, map.get("pos")).flatMap(
                                                                posPair -> ops.getNumberValue(map.get("currentIndexX")).flatMap(
                                                                        currentIndexX -> ops.getNumberValue(map.get("currentIndexY")).flatMap(
                                                                                currentIndexY -> ops.getNumberValue(map.get("currentIndexZ")).map(
                                                                                        currentIndexZ -> com.mojang.datafixers.util.Pair.of(new IteratorData3d(posPair.getFirst(), lengthX.intValue(), lengthY.intValue(), lengthZ.intValue(), hollow, currentIndexX.intValue(), currentIndexY.intValue(), currentIndexZ.intValue()), ops.empty()))
                                                                ))))))));
            }

            @Override
            public <T> DataResult<T> encode(IteratorData3d input, DynamicOps<T> ops, T prefix) {
                return ops.mapBuilder()
                        .add("lengthX", ops.createInt(input.lengthX))
                        .add("lengthY", ops.createInt(input.lengthY))
                        .add("lengthZ", ops.createInt(input.lengthZ))
                        .add("hollow", ops.createBoolean(input.hollow))
                        .add("pos", BlockPos.CODEC.encode(input.pos, ops, ops.empty()))
                        .add("currentIndexX", ops.createInt(input.currentIndexX))
                        .add("currentIndexY", ops.createInt(input.currentIndexY))
                        .add("currentIndexZ", ops.createInt(input.currentIndexZ))
                        .build(prefix);
            }
        };
    }

    public static class RectangleIterator implements IteratorVariable.Iterator {

        public static int ID;

        private final IteratorData2d data;

        private int count;

        public RectangleIterator(IteratorData2d data) {
            this(data, calculateCount(data));
        }

        private static int calculateCount(IteratorData2d data) {
            int count = (data.length1 + 1) * (data.length2 + 1);
            if(data.hollow && count != 0) {
                count -= (data.length1 - 1) * (data.length2 - 1);
            }
            return count;
        }

        protected RectangleIterator(IteratorData2d data, int count) {
            this.data = data;
            this.count = count;
        }

        @Override
        public boolean hasNext() {
            return count > 0;
        }

        @Override
        public Variable next() throws CommandSyntaxException {
            if(!hasNext()) {
                throw new NoSuchElementException();
            }
            IteratorData2d data = this.data;
            count--;
            if (data.currentIndex1 != -1) {
                if(data.currentIndex1 < data.length1) {
                    if (data.hollow) {
                        if (data.currentIndex2 > 0 && data.currentIndex2 < data.length2) {
                            data.currentIndex1 = data.length1;
                        } else {
                            data.currentIndex1++;
                        }
                    } else {
                        data.currentIndex1++;
                    }
                } else {
                    data.currentIndex1 = 0;
                    data.currentIndex2++;
                }
            } else {
                data.currentIndex1 = 0;
            }
            return data.resultSupplier.apply(data.currentIndex1, data.currentIndex2);
        }

        @Override
        public int getCount() {
            int result = count;
            count = 0;
            return result;
        }

        @Override
        public int getID() {
            return ID;
        }

        @Override
        public IteratorCodec getEncoder() {
            return CODEC;
        }

        @Override
        public IteratorVariable.Iterator copy() {
            return new RectangleIterator(data.copy(), count);
        }

        public static final IteratorCodec CODEC = new IteratorCodec() {

            @Override
            public <T> DataResult<com.mojang.datafixers.util.Pair<IteratorVariable.Iterator, T>> decode(DynamicOps<T> ops, T input, Variable.VariableType contentType) {
                return IteratorData2d.CODEC.decode(ops, input).flatMap(
                        dataPair -> ops.getMap(input).flatMap(
                                map -> ops.getNumberValue(map.get("count")).map(
                                        count -> new com.mojang.datafixers.util.Pair<>(new RectangleIterator(dataPair.getFirst(), count.intValue()), ops.empty())
                                )));
            }

            @Override
            public <T> DataResult<T> encode(IteratorVariable.Iterator input, DynamicOps<T> ops, T prefix) {
                RectangleIterator it = (RectangleIterator) input;
                return IteratorData2d.CODEC.encode(it.data, ops, prefix)
                        .flatMap(data -> ops.mapBuilder()
                                .add("count", ops.createInt(it.count))
                                .build(data));
            }
        };
    }

    public static class EllipseIterator implements IteratorVariable.Iterator {

        public static int ID;

        private final IteratorData2d data;

        private final int offset1;
        private final int offset2;
        private final int maxIndex1;
        private final int maxIndex2;
        private final double inverseRadius1;
        private final double inverseRadius2;
        private final boolean length1Odd;
        private final boolean length2Odd;

        private CurrentQuarter currentQuarter;

        public EllipseIterator(IteratorData2d data) {
            this(data, null);
        }

        protected EllipseIterator(IteratorData2d data, CurrentQuarter currentQuarter) {
            this.data = data;
            this.currentQuarter = currentQuarter;
            this.maxIndex1 = data.length1 / 2;
            this.maxIndex2 = data.length2 / 2;
            this.offset1 = this.maxIndex1 + (data.length1 & 1);
            this.offset2 = this.maxIndex2 + (data.length2 & 1);
            this.length1Odd = (data.length1 & 1) == 1;
            this.length2Odd = (data.length2 & 1) == 1;
            this.inverseRadius1 = 2d / (data.length1 + 1);
            this.inverseRadius2 = 2d / (data.length2 + 1);
        }

        @Override
        public boolean hasNext() {
            if(currentQuarter != null) {
                return true;
            }
            IteratorData2d data = this.data;
            do {
                if (data.currentIndex1 != -1) {
                    if (data.currentIndex1 < maxIndex1) {
                        data.currentIndex1++;
                    } else {
                        data.currentIndex1 = 0;
                        if(data.currentIndex2 >= maxIndex2) {
                            return false;
                        }
                        data.currentIndex2++;
                    }
                } else {
                    data.currentIndex1 = 0;
                }
            } while(!containsCurrentIndices());
            currentQuarter = CurrentQuarter.PosPos;
            return true;
        }

        private boolean containsCurrentIndices() {
            double normIndex1 = (data.currentIndex1 + (length1Odd ? 0.5 : 0)) * inverseRadius1;
            double normIndex2 = (data.currentIndex2 + (length2Odd ? 0.5 : 0)) * inverseRadius2;
            if(normIndex1 * normIndex1 + normIndex2 * normIndex2 > 1) {
                return false;
            }
            if(data.hollow) {
                normIndex1 += inverseRadius1;
                normIndex2 += inverseRadius2;
                if(normIndex1 * normIndex1 + normIndex2 * normIndex2 <= 1) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public Variable next() throws CommandSyntaxException {
            if(!hasNext()) {
                throw new NoSuchElementException();
            }
            PosVariable result = currentQuarter.getPos(data, offset1, offset2, length1Odd, length2Odd);
            nextQuarter();
            return result;
        }

        private void nextQuarter() {
            currentQuarter = currentQuarter.nextQuarter();
            // Make sure the 'edges' aren't returned twice when the length in that direction is odd
            if(currentQuarter != null) {
                if(!length1Odd && data.currentIndex1 == 0) {
                    if(!length2Odd && data.currentIndex2 == 0) {
                        currentQuarter = null; // The center is only returned once
                    } else {
                        currentQuarter = currentQuarter.nextQuarter();
                    }
                } else if(!length2Odd && data.currentIndex2 == 0) {
                    currentQuarter = currentQuarter.nextQuarter();
                }
            }
        }

        @Override
        public int getCount() {
            int result = 0;
            while(hasNext()) {
                result++;
                nextQuarter();
            }
            return result;
        }

        @Override
        public int getID() {
            return ID;
        }

        @Override
        public IteratorCodec getEncoder() {
            return CODEC;
        }

        @Override
        public IteratorVariable.Iterator copy() {
            return new EllipseIterator(data.copy());
        }

        public static final IteratorCodec CODEC = new IteratorCodec() {

            @Override
            public <T> DataResult<T> encode(IteratorVariable.Iterator input, DynamicOps<T> ops, T prefix) {
                EllipseIterator it = (EllipseIterator) input;
                DataResult<T> result = IteratorData2d.CODEC.encode(it.data, ops, prefix);
                if(it.currentQuarter != null) {
                    result = result.flatMap(data -> ops.mapBuilder()
                            .add("currentQuarter", ops.createInt(it.currentQuarter.ordinal()))
                            .build(data));
                }
                return result;
            }

            @Override
            public <T> DataResult<com.mojang.datafixers.util.Pair<IteratorVariable.Iterator, T>> decode(DynamicOps<T> ops, T input, Variable.VariableType contentType) {
                return IteratorData2d.CODEC.decode(ops, input).flatMap(
                        dataPair -> ops.getMap(input).flatMap(
                                map -> {
                                    T rawCurrentQuarter = map.get("currentQuarter");
                                    return rawCurrentQuarter == null
                                            ? DataResult.success(new com.mojang.datafixers.util.Pair<>(new EllipseIterator(dataPair.getFirst()), ops.empty()))
                                            : ops.getNumberValue(rawCurrentQuarter).map(
                                                    currentQuarter -> new com.mojang.datafixers.util.Pair<>(new EllipseIterator(dataPair.getFirst(), CurrentQuarter.VALUES[currentQuarter.intValue()]), ops.empty())
                                    );
                                }));
            }
        };

        private enum CurrentQuarter {
            PosPos{
                @Override
                public PosVariable getPos(IteratorData2d data, int offset1, int offset2, boolean length1Odd, boolean length2Odd) {
                    return data.resultSupplier.apply(data.currentIndex1 + offset1, data.currentIndex2 + offset2);
                }
            },
            NegPos {
                @Override
                public PosVariable getPos(IteratorData2d data, int offset1, int offset2, boolean length1Odd, boolean length2Odd) {
                    return data.resultSupplier.apply(-data.currentIndex1 + (length1Odd ? offset1 - 1 : offset1), data.currentIndex2 + offset2);
                }
            },
            NegNeg {
                @Override
                public PosVariable getPos(IteratorData2d data, int offset1, int offset2, boolean length1Odd, boolean length2Odd) {
                    return data.resultSupplier.apply(-data.currentIndex1 + (length1Odd ? offset1 - 1 : offset1), -data.currentIndex2 + (length2Odd ? offset2 - 1 : offset2));
                }
            },
            PosNeg {
                @Override
                public PosVariable getPos(IteratorData2d data, int offset1, int offset2, boolean length1Odd, boolean length2Odd) {
                    return data.resultSupplier.apply(data.currentIndex1 + offset1, -data.currentIndex2 + (length2Odd ? offset2 - 1 : offset2));
                }
            };

            public static final CurrentQuarter[] VALUES = values();

            public CurrentQuarter nextQuarter() {
                int ordinal = ordinal() + 1;
                if(ordinal >= VALUES.length) {
                    return null;
                }
                return VALUES[ordinal];
            }

            public abstract PosVariable getPos(IteratorData2d data, int offset1, int offset2, boolean length1Odd, boolean length2Odd);
        }
    }

    public static class CuboidIterator implements IteratorVariable.Iterator {

        public static int ID;

        private final IteratorData3d data;

        private int count;

        public CuboidIterator(IteratorData3d data) {
            this(data, calculateCount(data));
        }

        private static int calculateCount(IteratorData3d data) {
            int count = (data.lengthX + 1) * (data.lengthY + 1) * (data.lengthZ + 1);
            if(data.hollow && count != 0) {
                count -= (data.lengthX - 1) * (data.lengthY - 1) * (data.lengthZ - 1);
            }
            return count;
        }

        protected CuboidIterator(IteratorData3d data, int count) {
            this.data = data;
            this.count = count;
        }

        @Override
        public boolean hasNext() {
            return count > 0;
        }

        @Override
        public Variable next() throws CommandSyntaxException {
            if(!hasNext()) {
                throw new NoSuchElementException();
            }
            IteratorData3d data = this.data;
            count--;
            if (data.currentIndexX != -1) {
                if(data.currentIndexX < data.lengthX) {
                    if (data.hollow && data.currentIndexZ > 0 && data.currentIndexZ < data.lengthZ && data.currentIndexY > 0 && data.currentIndexY < data.lengthY) {
                        data.currentIndexX = data.lengthX;
                    } else {
                        data.currentIndexX++;
                    }
                } else {
                    data.currentIndexX = 0;
                    if(data.currentIndexZ < data.lengthZ) {
                        data.currentIndexZ++;
                    } else {
                        data.currentIndexZ = 0;
                        data.currentIndexY++;
                    }
                }
            } else {
                data.currentIndexX = 0;
            }
            return new PosVariable(data.pos.getX() + data.currentIndexX, data.pos.getY() + data.currentIndexY, data.pos.getZ() + data.currentIndexZ);
        }

        @Override
        public int getCount() {
            int count = this.count;
            this.count = 0;
            return count;
        }

        @Override
        public int getID() {
            return ID;
        }

        @Override
        public IteratorCodec getEncoder() {
            return CODEC;
        }

        @Override
        public IteratorVariable.Iterator copy() {
            return new CuboidIterator(data.copy(), count);
        }

        public static final IteratorCodec CODEC = new IteratorCodec() {

            @Override
            public <T> DataResult<T> encode(IteratorVariable.Iterator input, DynamicOps<T> ops, T prefix) {
                CuboidIterator it = (CuboidIterator) input;
                return IteratorData3d.CODEC.encode(it.data, ops, prefix)
                        .flatMap(data -> ops.mapBuilder()
                                .add("count", ops.createInt(it.count))
                                .build(data));
            }

            @Override
            public <T> DataResult<com.mojang.datafixers.util.Pair<IteratorVariable.Iterator, T>> decode(DynamicOps<T> ops, T input, Variable.VariableType variable) {
                return IteratorData3d.CODEC.decode(ops, input).flatMap(
                        dataPair -> ops.getMap(input).flatMap(
                                map -> ops.getNumberValue(map.get("count")).map(
                                        count -> new com.mojang.datafixers.util.Pair<>(new CuboidIterator(dataPair.getFirst(), count.intValue()), ops.empty())
                                )));
            }
        };
    }

    public static class EllipsoidIterator implements IteratorVariable.Iterator {

        public static int ID;

        private final IteratorData3d data;

        private final int offsetX;
        private final int offsetY;
        private final int offsetZ;
        private final int maxIndexX;
        private final int maxIndexY;
        private final int maxIndexZ;
        private final double inverseRadiusX;
        private final double inverseRadiusY;
        private final double inverseRadiusZ;
        private final boolean lengthXOdd;
        private final boolean lengthYOdd;
        private final boolean lengthZOdd;

        private CurrentEighth currentEighth;

        public EllipsoidIterator(IteratorData3d data) {
            this(data, null);
        }

        protected EllipsoidIterator(IteratorData3d data, CurrentEighth currentEighth) {
            this.data = data;
            this.currentEighth = currentEighth;
            this.maxIndexX = data.lengthX / 2;
            this.maxIndexY = data.lengthY / 2;
            this.maxIndexZ = data.lengthZ / 2;
            this.offsetX = this.maxIndexX + (data.lengthX & 1);
            this.offsetY = this.maxIndexY + (data.lengthY & 1);
            this.offsetZ = this.maxIndexZ + (data.lengthZ & 1);
            this.lengthXOdd = (data.lengthX & 1) == 1;
            this.lengthYOdd = (data.lengthY & 1) == 1;
            this.lengthZOdd = (data.lengthZ & 1) == 1;
            this.inverseRadiusX = 2d / (data.lengthX + 1);
            this.inverseRadiusY = 2d / (data.lengthY + 1);
            this.inverseRadiusZ = 2d / (data.lengthZ + 1);
        }

        @Override
        public boolean hasNext() {
            if(currentEighth != null) {
                return true;
            }
            IteratorData3d data = this.data;
            do {
                if (data.currentIndexX != -1) {
                    if (data.currentIndexX < maxIndexX) {
                        data.currentIndexX++;
                    } else {
                        data.currentIndexX = 0;
                        if(data.currentIndexZ < maxIndexZ) {
                            data.currentIndexZ++;
                        } else if(data.currentIndexY >= data.lengthY) {
                            return false;
                        } else {
                            data.currentIndexZ = 0;
                            data.currentIndexY++;
                        }
                    }
                } else {
                    data.currentIndexX = 0;
                }
            } while(!containsCurrentIndices());
            currentEighth = CurrentEighth.PosPosPos;
            return true;
        }

        private boolean containsCurrentIndices() {
            double normIndexX = (data.currentIndexX + (lengthXOdd ? 0.5 : 0)) * inverseRadiusX;
            double normIndexY = (data.currentIndexY + (lengthYOdd ? 0.5 : 0)) * inverseRadiusY;
            double normIndexZ = (data.currentIndexZ + (lengthZOdd ? 0.5 : 0)) * inverseRadiusZ;
            if(normIndexX * normIndexX + normIndexY * normIndexY + normIndexZ * normIndexZ > 1) {
                    return false;
            }
            if(data.hollow) {
                normIndexX += inverseRadiusX;
                normIndexY += inverseRadiusY;
                normIndexZ += inverseRadiusZ;
                if(normIndexX * normIndexX + normIndexY * normIndexY + normIndexZ * normIndexZ <= 1) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public Variable next() throws CommandSyntaxException {
            if(!hasNext()) {
                throw new NoSuchElementException();
            }
            PosVariable result = currentEighth.getPos(data, offsetX, offsetY, offsetZ, lengthXOdd, lengthYOdd, lengthZOdd);
            nextQuarter();
            return result;
        }

        private void nextQuarter() {
            CurrentEighth current = currentEighth;
            current = current.nextEighth();
            // Make sure the 'edges' aren't returned twice when the length in that direction is odd
            if(current != null) {
                int oddAxes = 0;
                if(!lengthXOdd && data.currentIndexX == 0) {
                    oddAxes += 1;
                }
                if(!lengthYOdd && data.currentIndexY == 0) {
                    oddAxes += 1;
                }
                if(!lengthZOdd && data.currentIndexZ == 0) {
                    oddAxes += 1;
                }
                current = switch(oddAxes) {
                    case 0 -> current;
                    case 1 -> current.nextEighth();
                    case 2 -> CurrentEighth.NegNegNeg;
                    default -> null;
                };
            }
            currentEighth = current;
        }

        @Override
        public int getCount() {
            int result = 0;
            while(hasNext()) {
                result++;
                nextQuarter();
            }
            return result;
        }

        @Override
        public int getID() {
            return ID;
        }

        @Override
        public IteratorCodec getEncoder() {
            return CODEC;
        }

        @Override
        public IteratorVariable.Iterator copy() {
            return new EllipsoidIterator(data.copy());
        }

        public static final IteratorCodec CODEC = new IteratorCodec() {

            @Override
            public <T> DataResult<T> encode(IteratorVariable.Iterator input, DynamicOps<T> ops, T prefix) {
                EllipsoidIterator it = (EllipsoidIterator) input;
                DataResult<T> result = IteratorData3d.CODEC.encode(it.data, ops, prefix);
                if(it.currentEighth != null) {
                    result = result.flatMap(data -> ops.mapBuilder()
                            .add("currentEighth", ops.createInt(it.currentEighth.ordinal()))
                            .build(data));
                }
                return result;
            }

            @Override
            public <T> DataResult<com.mojang.datafixers.util.Pair<IteratorVariable.Iterator, T>> decode(DynamicOps<T> ops, T input, Variable.VariableType type) {
                return IteratorData3d.CODEC.decode(ops, input).flatMap(
                        dataPair -> ops.getMap(input).flatMap(
                                map -> {
                                    T rawCurrentEighth = map.get("currentEighth");
                                    return rawCurrentEighth == null
                                            ? DataResult.success(new com.mojang.datafixers.util.Pair<>(new EllipsoidIterator(dataPair.getFirst()), ops.empty()))
                                            : ops.getNumberValue(rawCurrentEighth).map(
                                            currentEighth -> new com.mojang.datafixers.util.Pair<>(new EllipsoidIterator(dataPair.getFirst(), CurrentEighth.VALUES[currentEighth.intValue()]), ops.empty())
                                    );
                                }));
            }
        };

        private enum CurrentEighth {
            PosPosPos {
                @Override
                protected boolean isNegative(int index) {
                    return false;
                }
            },
            NegPosPos {
                @Override
                protected boolean isNegative(int index) {
                    return index == 0;
                }
            },
            NegPosNeg {
                @Override
                protected boolean isNegative(int index) {
                    return index != 1;
                }
            },
            PosPosNeg {
                @Override
                protected boolean isNegative(int index) {
                    return index == 2;
                }
            },
            PosNegNeg {
                @Override
                protected boolean isNegative(int index) {
                    return index != 0;
                }
            },
            PosNegPos {
                @Override
                protected boolean isNegative(int index) {
                    return index == 1;
                }
            },
            NegNegPos {
                @Override
                protected boolean isNegative(int index) {
                    return index != 2;
                }
            },
            NegNegNeg {
                @Override
                protected boolean isNegative(int index) {
                    return true;
                }
            };

            public static final CurrentEighth[] VALUES = values();

            protected abstract boolean isNegative(int index);

            public PosVariable getPos(IteratorData3d data, int offsetX, int offsetY, int offsetZ, boolean lengthXOdd, boolean lengthYOdd, boolean lengthZOdd) {
                BlockPos pos = data.pos;
                int x = pos.getX() + offsetX;
                if(isNegative(0)) {
                    x -= data.currentIndexX;
                    if(lengthXOdd) {
                        x -= 1;
                    }
                } else {
                    x += data.currentIndexX;
                }
                int y = pos.getY() + offsetY;
                if(isNegative(1)) {
                    y -= data.currentIndexY;
                    if(lengthYOdd) {
                        y -= 1;
                    }
                } else {
                    y += data.currentIndexY;
                }
                int z = pos.getZ() + offsetZ;
                if(isNegative(2)) {
                    z -= data.currentIndexZ;
                    if(lengthXOdd) {
                        z -= 1;
                    }
                } else {
                    z += data.currentIndexZ;
                }
                return new PosVariable(x, y, z);
            }

            public CurrentEighth nextEighth() {
                int ordinal = ordinal() + 1;
                if(ordinal >= VALUES.length) {
                    return null;
                }
                return VALUES[ordinal];
            }
        }
    }
}
