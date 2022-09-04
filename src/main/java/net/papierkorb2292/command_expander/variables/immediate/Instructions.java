package net.papierkorb2292.command_expander.variables.immediate;

import com.google.common.collect.AbstractIterator;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Either;
import net.minecraft.command.EntitySelector;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.papierkorb2292.command_expander.mixin_method_interfaces.VariableManagerContainer;
import net.papierkorb2292.command_expander.variables.*;
import net.papierkorb2292.command_expander.variables.immediate.operator.AddableOperatorVariableType;
import net.papierkorb2292.command_expander.variables.immediate.operator.MultipliableOperatorVariableType;
import net.papierkorb2292.command_expander.variables.immediate.operator.NegatableOperatorVariableType;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Instructions {

    public static final Instruction SIN = new Instruction(1, 1, false) {
        @Override
        public void apply(CalculationContext context) {
            Deque<Either<VariableHolder, Stream<Variable>>> stack = context.stack();
            stack.push(stack.pop().mapBoth(
                    holder -> new VariableHolder(calcSin(holder.variable)),
                    varStream -> varStream.map(this::calcSin)));
        }

        private DoubleVariable calcSin(Variable var) {
            return new DoubleVariable(Math.sin(var.doubleValue()));
        }
    };

    private static final SimpleCommandExceptionType INCOMPATIBLE_TYPES_EXCEPTION = new SimpleCommandExceptionType(new LiteralMessage("Encountered incompatible types: Unable to find compatible lowered type"));

    public static final Instruction ADD = new Instruction(2, 1, false) {
        @Override
        public void apply(CalculationContext context) {
            applyToTwoParameters(context, (l, r) -> {
                Variable.VariableType.LoweredType loweredType = Variable.VariableType.getLoweredType(l.getType(), r.getType());
                if(loweredType == null) {
                    throw INCOMPATIBLE_TYPES_EXCEPTION.create();
                }
                Variable.VariableType type = loweredType.type;
                while(type != null && !(type instanceof AddableOperatorVariableType)) {
                    type = type.getNextLoweredType();
                }
                if(type == null) {
                    throw INCOMPATIBLE_TYPES_EXCEPTION.create();
                }
                return ((AddableOperatorVariableType)type).addVariables(VariableManager.castVariable(type, l), VariableManager.castVariable(type, r));
            });
        }
    };

    public static final Instruction MUL = new Instruction(2, 1, false) {
        @Override
        public void apply(CalculationContext context) {
            applyToTwoParameters(context, (l, r) -> {
                Variable.VariableType.LoweredType loweredType = Variable.VariableType.getLoweredType(l.getType(), r.getType());
                if(loweredType == null) {
                    throw INCOMPATIBLE_TYPES_EXCEPTION.create();
                }
                Variable.VariableType type = loweredType.type;
                while(type != null && !(type instanceof MultipliableOperatorVariableType)) {
                    type = type.getNextLoweredType();
                }
                if(type == null) {
                    throw INCOMPATIBLE_TYPES_EXCEPTION.create();
                }
                return ((MultipliableOperatorVariableType)type).multiplyVariables(VariableManager.castVariable(type, l), VariableManager.castVariable(type, r));
            });
        }
    };

    public static final Instruction RANGE = new Instruction(2, 1, false) {

        @Override
        public void apply(CalculationContext context) {
            Either<VariableHolder, Stream<Variable>> right = context.stack().pop(), left = context.stack().pop();
            context.stack().push(Either.right(left.map(
                    holderLeft -> right.map(
                            holderRight -> getRangeStream(holderLeft.variable, holderRight.variable),
                            streamRight -> streamRight.flatMap(varRight -> getRangeStream(holderLeft.variable, varRight))
                    ),
                    streamLeft -> right.map(
                            holderRight -> streamLeft.flatMap(varLeft -> getRangeStream(varLeft, holderRight.variable)),
                            streamRight -> StreamSupport.stream(Spliterators.spliteratorUnknownSize(new AbstractIterator<Stream<Variable>>() {

                                private final Iterator<Variable> itLeft = streamLeft.iterator();
                                private final Iterator<Variable> itRight = streamRight.iterator();

                                @Nullable
                                @Override
                                protected Stream<Variable> computeNext() {
                                    if (itLeft.hasNext() && itRight.hasNext()) {
                                        return getRangeStream(itLeft.next(), itRight.next());
                                    }
                                    return endOfData();
                                }
                            }, 0), false).flatMap(stream -> stream)
                    ))));
        }

        private Stream<Variable> getRangeStream(Variable startVar, Variable endVar) {
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new AbstractIterator<>() {

                private int current = startVar.intValue();
                private final int end = endVar.intValue();

                @Nullable
                @Override
                protected Variable computeNext() {
                    if(current > end) {
                        return endOfData();
                    }
                    return new IntVariable(current++);
                }
            }, 0), false);
        }
    };


    public static final Instruction BUILD_MAP_ENTRY = new Instruction(2, 1, false) {
        @Override
        public void apply(CalculationContext context) {
            applyToTwoParameters(context, (key, value) -> {
                MapEntryVariable result = new MapEntryVariable(new MapEntryVariable.MapEntryVariableType(key == null ? null : key.getType(), value == null ? null : value.getType()));
                result.key = key;
                result.value = value;
                return result;
            });
        }
    };

    /**
     * Wrapper for {@link ImmediateValue.CommandBiFunction#applyToTwoParameters} taking both parameters from the stack and using the error consumer of the calculation context.
     * The command context is not used.
     * @param context The calculation context to get the stack and error consumer
     * @param function The function to apply to the parameters
     */
    private static void applyToTwoParameters(Instruction.CalculationContext context, ImmediateValue.CommandBiFunction function) {
        Deque<Either<VariableHolder, Stream<Variable>>> stack = context.stack();
        Either<VariableHolder, Stream<Variable>> right = stack.pop(), left = stack.pop();
        stack.push(function.applyToTwoParameters(left, right, context.errorConsumer()));
    }

    public static final Instruction NEGATE = new Instruction(1, 1, false) {
        @Override
        public void apply(CalculationContext context) {
            Deque<Either<VariableHolder, Stream<Variable>>> stack = context.stack();
            stack.push(stack.pop().mapBoth(
                    holder -> new VariableHolder(calcNegate(holder.variable, context.errorConsumer())),
                    varStream -> varStream.map(var -> calcNegate(var, context.errorConsumer())).filter(Objects::nonNull))
                    .flatMap(holder -> holder.variable == null ? Either.right(Stream.empty()) : Either.left(holder)));
        }

        private Variable calcNegate(Variable var, Consumer<Text> errorConsumer) {
            Variable.VariableType type = var.getType();
            while(type != null && !(type instanceof NegatableOperatorVariableType)) {
                type = type.getNextLoweredType();
            }
            if(type == null) {
                errorConsumer.accept(Texts.toText(INCOMPATIBLE_TYPES_EXCEPTION.create().getRawMessage()));
                return null;
            }
            try {
                return ((NegatableOperatorVariableType)type).negateVariable(VariableManager.castVariable(type, var));
            } catch (CommandSyntaxException e) {
                errorConsumer.accept(Texts.toText(e.getRawMessage()));
                return null;
            }
        }
    };

    public static final Instruction GET_ALL_CONTENTS = new Instruction(1, 1, false) {

        @Override
        public void apply(CalculationContext context) {
            Deque<Either<VariableHolder, Stream<Variable>>> stack = context.stack();
            stack.push(stack.pop().map(
                    holder -> {
                        if(!(holder.variable instanceof IndexableVariable indexable)) {
                            context.errorConsumer().accept(Texts.toText(VALUE_NOT_INDEXABLE_EXCEPTION.create().getRawMessage()));
                            return null;
                        }
                        return Either.right(indexable.getContents());
                    },
                    varStream -> Either.right(varStream.flatMap(var -> {
                        if (!(var instanceof IndexableVariable indexable)) {
                            context.errorConsumer().accept(Texts.toText(VALUE_NOT_INDEXABLE_EXCEPTION.create().getRawMessage()));
                            return null;
                        }
                        return indexable.getContents();
                    }))));
        }
    };

    public static final Instruction GET_INDEXED_CONTENTS = new Instruction(2, 1, false) {

        @Override
        public void apply(CalculationContext context) {
            applyToTwoParameters(context, (value, index) -> {
                if(!(value instanceof IndexableVariable indexable)) {
                    throw VALUE_NOT_INDEXABLE_EXCEPTION.create();
                }
                return indexable.get(indexable.ensureIndexCompatible(index));
            });
        }
    };

    private static final SimpleCommandExceptionType VALUE_NOT_INDEXABLE_EXCEPTION = new SimpleCommandExceptionType(new LiteralMessage("Value is not indexable"));

    public static Instruction getCast(Variable.VariableType type) {
        return new Cast(type);
    }

    public static class Cast extends Instruction {

        private final Variable.VariableType type;

        private Cast(Variable.VariableType type) {
            super(1, 1, false);
            this.type = type;
        }

        @Override
        public void apply(CalculationContext context) {
            context.stack().push(context.stack().pop().mapBoth(
                    var -> new VariableHolder(castSafe(var.variable, type, context.errorConsumer())),
                    varStream -> varStream.map(var -> castSafe(var, type, context.errorConsumer()))));
        }

        private static Variable castSafe(Variable var, Variable.VariableType type, Consumer<Text> errorConsumer) {
            try {
                return VariableManager.castVariable(type, var);
            } catch (CommandSyntaxException e) {
                errorConsumer.accept(Texts.toText(e.getRawMessage()));
                return null;
            }
        }
    }

    public static Instruction getBuildListOrMap(int length) {
        return new BuildListOrMap(length);
    }

    /**
     * Takes a value from the stack for every entry of a list with the given list and lowers their types.
     * If the lowered type is a map entry type, a map with contents corresponding to the entries is build instead. Casting it to a list
     * will give a list of the entries
     */
    public static class BuildListOrMap extends Instruction {

        private final int length;

        public BuildListOrMap(int length) {
            super(length, 1, false);
            this.length = length;
        }

        @Override
        public void apply(CalculationContext context) throws CommandSyntaxException {
            Deque<Either<VariableHolder, Stream<Variable>>> stack = context.stack();
            List<Either<VariableHolder, Iterator<Variable>>> content = new ArrayList<>(length);
            boolean hasIterator = false;
            for(int i = 0; i < length; ++i) {
                Either<VariableHolder, Stream<Variable>> var = stack.pop();
                if(var.right().isPresent()) {
                    hasIterator = true;
                }
                content.add(var.mapRight(Stream::iterator));
            }
            Variable.VariableType[] contentTypes = new Variable.VariableType[length];
            if(!hasIterator) {
                for(int i = 0; i < length; ++i) {
                    //noinspection OptionalGetWithoutIsPresent
                    Variable var = content.get(i).left().get().variable;
                    contentTypes[i] = var == null ? null : var.getType();
                }
                Variable.VariableType.LoweredType loweredContentType = Variable.VariableType.getLoweredType(contentTypes);
                if(loweredContentType == null) {
                    throw INCOMPATIBLE_TYPES_EXCEPTION.create();
                }
                VariableManager.Caster childCaster = loweredContentType.type != null ? loweredContentType.type.getTemplate().caster : null;
                if(childCaster != null) {
                    if (loweredContentType.type instanceof MapEntryVariable.MapEntryVariableType mapType) {
                        MapVariable var = new MapVariable(new MapVariable.MapVariableType(mapType.key, mapType.value));
                        for (int i = 0; i < length; ++i) {
                            //noinspection OptionalGetWithoutIsPresent
                            MapEntryVariable entry = (MapEntryVariable) childCaster.cast(loweredContentType.type, content.get(i).left().get().variable);
                            var.set(entry.key, entry.value);
                        }
                        stack.push(Either.left(new VariableHolder(var)));
                        return;
                    }
                }
                ListVariable var = new ListVariable(new ListVariable.ListVariableType(loweredContentType.type));
                IntVariable index = new IntVariable();
                index.setValue(length - 1);
                var.ensureIndexExists(index);
                for(int i = 0; i < length; ++i) {
                    index.setValue(i);
                    //noinspection OptionalGetWithoutIsPresent
                    Variable value = content.get(i).left().get().variable;
                    var.set(index, childCaster != null ? childCaster.cast(loweredContentType.type, value) : null);
                }
                stack.push(Either.left(new VariableHolder(var)));
                return;
            }
            stack.push(Either.right(StreamSupport.stream(Spliterators.spliteratorUnknownSize(new AbstractIterator<>() {

                private final Variable[] currentValues = new Variable[length];

                @Nullable
                @Override
                protected Variable computeNext() {
                    for (int i = 0; i < length; i++) {
                        Either<VariableHolder, Iterator<Variable>> value = content.get(i);
                        if(value.left().isPresent()) {
                            currentValues[i] = value.left().get().variable;
                            continue;
                        }
                        //noinspection OptionalGetWithoutIsPresent
                        if(value.right().get().hasNext()) {
                            //noinspection OptionalGetWithoutIsPresent
                            currentValues[i] = value.right().get().next();
                            continue;
                        }
                        return endOfData();
                    }
                    for(int i = 0; i < length; ++i) {
                        contentTypes[i] = currentValues[i].getType();
                    }
                    Variable.VariableType.LoweredType loweredContentType = Variable.VariableType.getLoweredType(contentTypes);
                    if(loweredContentType == null) {
                        context.errorConsumer().accept(Texts.toText(INCOMPATIBLE_TYPES_EXCEPTION.create().getRawMessage()));
                        return null;
                    }
                    VariableManager.Caster childCaster = loweredContentType.type.getTemplate().caster;
                    if(loweredContentType.type instanceof MapEntryVariable.MapEntryVariableType mapType) {
                        MapVariable result = new MapVariable(new MapVariable.MapVariableType(mapType.key, mapType.value));
                        for(int i = 0; i < length; ++i) {
                            try {
                                //noinspection OptionalGetWithoutIsPresent
                                MapEntryVariable entry = (MapEntryVariable) childCaster.cast(loweredContentType.type, content.get(i).left().get().variable);
                                result.set(entry.key, entry.value);
                            } catch (CommandSyntaxException e) {
                                context.errorConsumer().accept(Texts.toText(e.getRawMessage()));
                            }
                        }
                        return result;
                    }
                    ListVariable result = new ListVariable(new ListVariable.ListVariableType(loweredContentType.type));
                    IntVariable index = new IntVariable();
                    index.setValue(length);
                    result.ensureIndexExists(index);
                    for(int i = 0; i < length; ++i) {
                        index.setValue(i);
                        try {
                            result.set(index, childCaster.cast(loweredContentType.type, currentValues[i]));
                        } catch (CommandSyntaxException e) {
                            context.errorConsumer().accept(Texts.toText(e.getRawMessage()));
                            result.set(index, null);
                        }
                    }
                    return result;
                }
            }, 0), false)));
        }
    }

    public static Instruction getLoadConstant(Variable constant) {
        return new LoadConstant(constant);
    }

    /**
     * Puts a constant onto the stack
     */
    public static class LoadConstant extends Instruction {

        public final Either<VariableHolder, List<Variable>> value;

        public LoadConstant(Variable constant) {
            super(0, 1, false);
            value = Either.left(new VariableHolder(constant));
        }

        public LoadConstant(List<Variable> value) {
            super(0, 1, false);
            this.value = Either.right(value);
        }

        @Override
        public void apply(CalculationContext context) {
            context.stack().push(value.mapBoth(
                    holder -> holder,
                    List::stream
            ));
        }
    }

    public static Instruction getLoadVariable(VariableIdentifier id) {
        return new LoadVariable(id);
    }

    /**
     * Loads a value from the server using the command context and puts its value onto the stack
     */
    public static class LoadVariable extends Instruction {

        private final VariableIdentifier id;

        public LoadVariable(VariableIdentifier id) {
            super(0, 1, true);
            this.id = id;
        }

        @Override
        public void apply(CalculationContext context) throws CommandSyntaxException {
            context.stack().push(Either.left(new VariableHolder(((VariableManagerContainer)context.commandContext().getSource().getServer()).command_expander$getVariableManager().getReadonly(id).var)));
        }
    }

    public static Instruction getLoadFromEntitySelector(EntitySelector selector) {
        return new LoadFromEntitySelector(selector);
    }

    /**
     * Gets all entities selected by the selector from the server using the command context and puts them onto the stack
     */
    public static class LoadFromEntitySelector extends Instruction {

        private final EntitySelector entitySelector;

        public LoadFromEntitySelector(EntitySelector entitySelector) {
            super(0, 1, true);
            this.entitySelector = entitySelector;
        }

        @Override
        public void apply(CalculationContext context) throws CommandSyntaxException {
            if(entitySelector.getLimit() == 1) {
                context.stack().push(Either.left(
                        new VariableHolder(
                                new EntityVariable(
                                        entitySelector.getEntity(context.commandContext().getSource())
                                                .getUuid()))
                ));
                return;
            }
            context.stack().push(Either.right(
                    entitySelector.getEntities(context.commandContext().getSource())
                            .stream().map(entity ->
                                    new EntityVariable(entity.getUuid()))));
        }
    }

    public static Instruction getLoadStreamAll(int count) {
        return new LoadStreamAll(count);
    }

    /**
     * Takes a with "count" specified amount of entries from the stack and adds a stream of them to the stack.<br/>
     * If the entries are streams themselves, the values of that stream are used
     */
    public static class LoadStreamAll extends Instruction {

        private final int count;

        public LoadStreamAll(int count) {
            super(count, 1, false);
            this.count = count;
        }

        @Override
        public void apply(CalculationContext context) {
            List<Either<VariableHolder, Stream<Variable>>> data = new ArrayList<>(count);
            for(int i = 0; i < count; ++i) {
                data.add(context.stack().pop());
            }
            context.stack().push(Either.right(data.stream().flatMap(entry -> entry.map(
                    holder -> Stream.of(holder.variable),
                    stream -> stream))));
        }
    }
}
