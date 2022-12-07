package net.papierkorb2292.command_expander.variables.path;

import com.google.common.collect.AbstractIterator;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Texts;
import net.minecraft.util.Util;
import net.papierkorb2292.command_expander.variables.*;
import net.papierkorb2292.command_expander.variables.immediate.ImmediateValue;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Interface for accessors of {@link VariablePath}s defining how to get, set or remove the specified children.
 * Accessors are for example used for indexing or accessing the key/value of a map entry
 */
public interface PathChildrenAccessor {

    /**
     * Gets all children that the accessor points to.
     * @param current The current value of the path. Children of this value should be returned.
     * @param cc The {@link CommandContext} of the command the path is used in.
     * @return The children that the accessor points to. When this is used in {@link VariablePath#set}
     * or {@link VariablePath#remove}, the returned value becomes the new current value that is given to the next accessor.
     * @throws CommandSyntaxException An error happened when getting the children.
     */
    Either<VariableHolder, Stream<Variable>> getChildren(Either<VariableHolder, Stream<Variable>> current, CommandContext<ServerCommandSource> cc, boolean createIfMissing) throws CommandSyntaxException;

    /**
     * Sets all children that the accessor points to of the current value to the specified value
     * @param current The current value of the path. Children of this value should be set to the specified value
     * @param value The value to set the children to
     * @param cc The {@link CommandContext} of the command the path is used in
     * @return The amount of set children
     * @throws CommandSyntaxException An error happened when setting the children
     */
    int setChildren(Either<VariableHolder, Stream<Variable>> current, Either<VariableHolder, Stream<Variable>> value, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException;

    /**
     * Removes all children that the accessor points to
     * @param current The current valueof the path. Children of this value should be removed
     * @param cc The {@link CommandContext} of the command the path is used in
     * @return The amount of removed children
     * @throws CommandSyntaxException An error happened when removing the children
     */
    int removeChildren(Either<VariableHolder, Stream<Variable>> current, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException;

    /**
     * Gets the {@link CriterionPath.ChildDescriptor}s that point to the children that the accessor uses. Needed
     * when using the variable path in criteria binding to save each bound variable individually
     * @param value The variable that the accessor is applied to
     * @param cc The command context of the command the path is used in
     * @return The child descriptors for the children of the value
     * @throws CommandSyntaxException An error happened when getting the child descriptors. For example when the value was of the wrong type
     */
    List<CriterionPath.ChildDescriptor> getChildDescriptors(Variable value, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException;

    /**
     * Indexes variables with an {@link ImmediateValue} as index.<br/>
     * If a single index is returned and the current value of the path is a single variable, the index is applied to that variable.<br/>
     * If multiple indices are returned and the current value of the path is a single variable, all
     * indices are applied to the current variable.<br/>
     * If a single index is returned and the current values of the path consists of multiple variables, that index is
     * applied to all variables.<br/>
     * If multiple indices are returned and the current values of the path consists of multiple variables, each index is used once
     * on a single variable until no more variables or no more indices are present.<br/>
     * {@link IndexableVariable#ensureIndexCompatible} is used on every index. If the variable is a list containing maps and the index is also a map,
     * all maps that have matching values for all keys present in the index are used. If this behavior is not desired, the index can be casted to an int first.<br/>
     * If the value is not indexable, a {@link VariablePath#VARIABLE_NOT_INDEXABLE_EXCEPTION} is thrown.
     */
    class IndexedPathChildrenAccessor implements PathChildrenAccessor {

        private final ImmediateValue index;
        private final boolean compareMaps;

        public IndexedPathChildrenAccessor(ImmediateValue index, boolean compareMaps) {
            this.index = index;
            this.compareMaps = compareMaps;
        }

        @Override
        public Either<VariableHolder, Stream<Variable>> getChildren(Either<VariableHolder, Stream<Variable>> current, CommandContext<ServerCommandSource> cc, boolean createIfMissing) throws CommandSyntaxException {
            return index.calculate(cc).map(
                    indexHolder -> {
                        Variable singleIndex = indexHolder.variable;
                        return current.map(
                                holder -> {
                                    if (holder == null) return null;
                                    if (!(holder.variable instanceof IndexableVariable indexable)) {
                                        cc.getSource().sendError(Texts.toText(VariablePath.VARIABLE_NOT_INDEXABLE_EXCEPTION.create().getRawMessage()));
                                        return null;
                                    }
                                    try {
                                        return get(indexable, singleIndex, compareMaps);
                                    } catch (CommandSyntaxException e) {
                                        cc.getSource().sendError(Texts.toText(e.getRawMessage()));
                                        return null;
                                    }
                                },
                                stream -> Either.right(stream.map(variable -> {
                                    if (variable == null) return null;
                                    if (!(variable instanceof IndexableVariable indexable)) {
                                        cc.getSource().sendError(Texts.toText(VariablePath.VARIABLE_NOT_INDEXABLE_EXCEPTION.create().getRawMessage()));
                                        return null;
                                    }
                                    try {
                                        return get(indexable, singleIndex, compareMaps);
                                    } catch (CommandSyntaxException e) {
                                        cc.getSource().sendError(Texts.toText(e.getRawMessage()));
                                        return null;
                                    }
                                }).flatMap(either -> either == null ? null : either.map(
                                        resultHolder -> Stream.of(resultHolder.variable),
                                        resultStream -> resultStream))));
                    },
                    indexStream -> current.map(
                            holder -> {
                                if (holder == null) return null;
                                if (!(holder.variable instanceof IndexableVariable indexable)) {
                                    cc.getSource().sendError(Texts.toText(VariablePath.VARIABLE_NOT_INDEXABLE_EXCEPTION.create().getRawMessage()));
                                    return null;
                                }
                                return Either.right(indexStream.map(singleIndex -> {
                                    try {
                                        return get(indexable, singleIndex, compareMaps);
                                    } catch (CommandSyntaxException e) {
                                        cc.getSource().sendError(Texts.toText(e.getRawMessage()));
                                        return null;
                                    }
                                }).flatMap(either -> either == null ? null : either.map(
                                        resultHolder -> Stream.of(resultHolder.variable),
                                        resultStream -> resultStream)));
                            },
                            variableStream -> {
                                Iterator<Variable> indexIterator = indexStream.iterator(), variableIterator = variableStream.iterator();
                                return Either.right(StreamSupport.stream(Spliterators.spliteratorUnknownSize(new AbstractIterator<Either<VariableHolder, Stream<Variable>>>() {

                                    @Nullable
                                    @Override
                                    protected Either<VariableHolder, Stream<Variable>> computeNext() {
                                        if (!(indexIterator.hasNext() && variableIterator.hasNext())) {
                                            return endOfData();
                                        }
                                        if (!(variableIterator.next() instanceof IndexableVariable indexable)) {
                                            indexIterator.next();
                                            cc.getSource().sendError(Texts.toText(VariablePath.VARIABLE_NOT_INDEXABLE_EXCEPTION.create().getRawMessage()));
                                            return null;
                                        }
                                        try {
                                            return get(indexable, indexIterator.next(), compareMaps);
                                        } catch (CommandSyntaxException e) {
                                            cc.getSource().sendError(Texts.toText(e.getRawMessage()));
                                            return null;
                                        }
                                    }

                                }, 0), false).flatMap(either -> either == null ? null : either.map(
                                        resultHolder -> Stream.of(resultHolder.variable),
                                        resultStream -> resultStream)));
                            }
                    )
            );
        }

        @Override
        public int setChildren(Either<VariableHolder, Stream<Variable>> current, Either<VariableHolder, Stream<Variable>> value, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
            Either<VariableHolder, Stream<Variable>> indexEither = index.calculate(cc);
            if (current.left().isPresent()) {
                if (!(current.left().get().variable instanceof IndexableVariable indexable)) {
                    throw VariablePath.VARIABLE_NOT_INDEXABLE_EXCEPTION.create();
                }
                if (indexEither.left().isPresent()) {
                    if (value.left().isPresent()) {
                        return set(indexable, indexEither.left().get().variable, value.left().get().variable, compareMaps);
                    }
                    throw VariablePath.MULTIPLE_VALUES_TO_SINGLE_VARIABLE_EXCEPTION.create();
                }
                if(indexEither.right().isEmpty()) {
                    throw new IllegalArgumentException("Invalid Either returned from index immediate value. Neither left nor right were present");
                }
                Stream<Variable> indexes = indexEither.right().get();
                return value.map(
                        valueHolder -> indexes.mapToInt(index ->
                        {
                            try {
                                return set(indexable, index, valueHolder.variable, compareMaps);
                            } catch (CommandSyntaxException e) {
                                cc.getSource().sendError(Texts.toText(e.getRawMessage()));
                                return 0;
                            }
                        }).sum(),
                        valueStream -> {
                            Iterator<Variable> indexIterator = indexes.iterator(), valueIterator = valueStream.iterator();
                            int changed = 0;
                            while (indexIterator.hasNext() && valueIterator.hasNext()) {
                                try {
                                    changed += set(indexable, indexIterator.next(), valueIterator.next(), compareMaps);
                                } catch (CommandSyntaxException e) {
                                    cc.getSource().sendError(Texts.toText(e.getRawMessage()));
                                }
                            }
                            return changed;
                        }
                );
            }
            if(current.right().isEmpty()) {
                throw new IllegalArgumentException("Invalid Either passed as current value. Neither left nor right were present");
            }
            Stream<Variable> currentRight = current.right().get();
            return indexEither.map(
                    indexHolder -> value.map(
                            valueHolder -> currentRight.mapToInt(variable -> {
                                if (!(variable instanceof IndexableVariable indexable)) {
                                    cc.getSource().sendError(Texts.toText(VariablePath.VARIABLE_NOT_INDEXABLE_EXCEPTION.create().getRawMessage()));
                                    return 0;
                                }
                                try {
                                    return set(indexable, indexHolder.variable, valueHolder.variable, compareMaps);
                                } catch (CommandSyntaxException e) {
                                    cc.getSource().sendError(Texts.toText(e.getRawMessage()));
                                    return 0;
                                }
                            }).sum(),
                            valueStream -> {
                                Iterator<Variable> variableIterator = currentRight.iterator(), valueIterator = valueStream.iterator();
                                int changed = 0;
                                while (variableIterator.hasNext() && valueIterator.hasNext()) {
                                    if (!(variableIterator.next() instanceof IndexableVariable indexable)) {
                                        cc.getSource().sendError(Texts.toText(VariablePath.VARIABLE_NOT_INDEXABLE_EXCEPTION.create().getRawMessage()));
                                        continue;
                                    }
                                    try {
                                        changed += set(indexable, indexHolder.variable, valueIterator.next(), compareMaps);
                                    } catch (CommandSyntaxException e) {
                                        cc.getSource().sendError(Texts.toText(e.getRawMessage()));
                                    }
                                }
                                return changed;
                            }
                    ),
                    indexStream -> value.map(
                            valueHolder -> {
                                Iterator<Variable> variableIterator = currentRight.iterator(), indexIterator = indexStream.iterator();
                                Variable valueLeft = valueHolder.variable;
                                int changed = 0;
                                while (variableIterator.hasNext() && indexIterator.hasNext()) {
                                    if (!(variableIterator.next() instanceof IndexableVariable indexable)) {
                                        cc.getSource().sendError(Texts.toText(VariablePath.VARIABLE_NOT_INDEXABLE_EXCEPTION.create().getRawMessage()));
                                        continue;
                                    }
                                    try {
                                        changed += set(indexable, indexIterator.next(), valueLeft, compareMaps);
                                    } catch (CommandSyntaxException e) {
                                        cc.getSource().sendError(Texts.toText(e.getRawMessage()));
                                    }
                                }
                                return changed;
                            },
                            valueStream -> {
                                Iterator<Variable> variableIterator = currentRight.iterator(), indexIterator = indexStream.iterator(), valueIterator = valueStream.iterator();
                                int changed = 0;
                                while (variableIterator.hasNext() && indexIterator.hasNext() && valueIterator.hasNext()) {
                                    if (!(variableIterator.next() instanceof IndexableVariable indexable)) {
                                        cc.getSource().sendError(Texts.toText(VariablePath.VARIABLE_NOT_INDEXABLE_EXCEPTION.create().getRawMessage()));
                                        continue;
                                    }
                                    try {
                                        changed += set(indexable, indexIterator.next(), valueIterator.next(), compareMaps);
                                    } catch (CommandSyntaxException e) {
                                        cc.getSource().sendError(Texts.toText(e.getRawMessage()));
                                    }
                                }
                                return changed;
                            }
                    )
            );
        }

        @Override
        public int removeChildren(Either<VariableHolder, Stream<Variable>> current, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
            Either<VariableHolder, Stream<Variable>> indexEither = index.calculate(cc);
            if (current.left().isPresent()) {
                if (!(current.left().get().variable instanceof IndexableVariable indexable)) {
                    throw VariablePath.VARIABLE_NOT_INDEXABLE_EXCEPTION.create();
                }
                if (indexEither.left().isPresent()) {
                    return remove(indexable, indexEither.left().get().variable, compareMaps);
                }
                if(indexEither.right().isEmpty()) {
                    throw new IllegalArgumentException("Invalid Either returned from index immediate value. Neither left nor right were present");
                }
                return indexEither.right().get().mapToInt(index -> {
                    try {
                        return remove(indexable, index, compareMaps);
                    } catch (CommandSyntaxException e) {
                        cc.getSource().sendError(Texts.toText(e.getRawMessage()));
                        return 0;
                    }
                }).sum();
            }
            if(current.right().isEmpty()) {
                throw new IllegalArgumentException("Invalid Either passed as current value. Neither left nor right were present");
            }
            Stream<Variable> currentRight = current.right().get();
            return indexEither.map(
                    indexHolder -> {
                        Variable index = indexHolder.variable;
                        return currentRight.mapToInt(variable -> {
                            if (!(variable instanceof IndexableVariable indexable)) {
                                cc.getSource().sendError(Texts.toText(VariablePath.VARIABLE_NOT_INDEXABLE_EXCEPTION.create().getRawMessage()));
                                return 0;
                            }
                            try {
                                return remove(indexable, index, compareMaps);
                            } catch (CommandSyntaxException e) {
                                cc.getSource().sendError(Texts.toText(e.getRawMessage()));
                                return 0;
                            }
                        }).sum();
                    },
                    indexStream -> {
                        Iterator<Variable> currentIterator = currentRight.iterator(), indexIterator = indexStream.iterator();
                        int removed = 0;
                        while(currentIterator.hasNext() && indexIterator.hasNext()) {
                            if (!(currentIterator.next() instanceof IndexableVariable indexable)) {
                                indexIterator.next();
                                cc.getSource().sendError(Texts.toText(VariablePath.VARIABLE_NOT_INDEXABLE_EXCEPTION.create().getRawMessage()));
                                continue;
                            }
                            try {
                                removed += remove(indexable, indexIterator.next(), compareMaps);
                            } catch (CommandSyntaxException e) {
                                cc.getSource().sendError(Texts.toText(e.getRawMessage()));
                            }
                        }
                        return removed;
                    }
            );
        }

        private final Map<CommandContext<ServerCommandSource>, List<CriterionPath.ChildDescriptor>> childDescriptors = new HashMap<>();

        @Override
        public List<CriterionPath.ChildDescriptor> getChildDescriptors(Variable value, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
            if(!(value instanceof IndexableVariable)) {
                throw VariablePath.VARIABLE_NOT_INDEXABLE_EXCEPTION.create();
            }
            if(childDescriptors.containsKey(cc)) {
                return childDescriptors.get(cc);
            }
            Either<VariableHolder, Stream<Variable>> calculatedIndex = index.calculate(cc);
            List<CriterionPath.ChildDescriptor> result = calculatedIndex.map(
                    holder -> List.of(new CriterionPath.ChildDescriptor(CriterionPath.ChildType.INDEXED, holder.variable)),
                    stream -> stream.map(index -> new CriterionPath.ChildDescriptor(CriterionPath.ChildType.INDEXED, index)).toList()
            );
            childDescriptors.put(cc, result);
            return result;
        }

        /**
         * Gets the value of the index in the {@link IndexableVariable} or, if the inndexable variable is a list of maps and the index is map,
         * gets all content of the list with matching values for all keys present in the index.
         * @param indexable The indexable variable to get values of
         * @param index The index of the value to get in the indexable variable
         * @return The value(s)
         * @throws CommandSyntaxException An error happened when casting the index
         */
        public static Either<VariableHolder, Stream<Variable>> get(IndexableVariable indexable, Variable index, boolean compareMaps) throws CommandSyntaxException {
            if(compareMaps && indexable instanceof ListVariable list && list.getContentType() instanceof MapVariable.MapVariableType && index instanceof MapVariable map) {
                return Either.right(StreamSupport.stream(Spliterators.spliteratorUnknownSize(getIndicesOfMapInList(list, map), 0), false).map(indexable::get));
            }
            return Either.left(new VariableHolder(indexable.ensureIndexAndGetNonNull(index)));
        }

        /**
         * Sets the index in the {@link IndexableVariable} to the specified value or, if the inndexable variable is a list of maps and the index
         * is a map, sets all indices that contain maps with matching values for all keys present in the index.
         * @param indexable The indexable variable to set values of
         * @param index The index of the value to set in the indexable variable
         * @param value The value to set with the index in the indexable variable
         * @return The amount of set values
         * @throws CommandSyntaxException An error happened when casting the index or value
         */
        public static int set(IndexableVariable indexable, Variable index, Variable value, boolean compareMaps) throws CommandSyntaxException {
            Variable castedValue = VariableManager.castVariable(indexable.getContentType(), value);
            if(compareMaps && indexable instanceof ListVariable list && list.getContentType() instanceof MapVariable.MapVariableType && index instanceof MapVariable mapIndex) {
                Iterator<IntVariable> indexIterator = getIndicesOfMapInList(list, mapIndex);
                int changed = 0;
                while(indexIterator.hasNext()) {
                    if(indexable.set(indexIterator.next(), castedValue)) {
                        ++changed;
                    }
                }
                return changed;
            }
            return indexable.ensureIndexAndSet(indexable.ensureIndexCompatible(index), castedValue) ? 1 : 0;
        }

        /**
         * Removes the index in the {@link IndexableVariable} or, if the inndexable variable is a list of maps and the index is map,
         * removes all indices that contain maps with matching values for all keys present in the index.
         * @param indexable The indexable variable to remove values of
         * @param index The index of the value to remove in the indexable variable
         * @return The amount of removed values
         * @throws CommandSyntaxException An error happened when casting the index
         */
        public static int remove(IndexableVariable indexable, Variable index, boolean compareMaps) throws CommandSyntaxException {
            if(compareMaps && indexable instanceof ListVariable list && list.getContentType() instanceof MapVariable.MapVariableType && index instanceof MapVariable mapIndex) {
                Iterator<IntVariable> indexIterator = getIndicesOfMapInList(list, mapIndex);
                int removed = 0;
                while(indexIterator.hasNext()) {
                    indexable.remove(indexIterator.next());
                    ++removed;
                }
                return removed;
            }
            return indexable.remove(indexable.ensureIndexCompatible(index)) ? 1 : 0;
        }

        /**
         * Creates an iterator for finding maps in a list that have matching values for all keys present in the map parameter
         * @param list The list to iterate. The content must be of type {@link MapVariable.MapVariableType}, otherwise an {@link IllegalArgumentException} is thrown
         * @param map The map to compare against the contents of the list
         * @return The iterator, which will always return the same instance of {@link IntVariable} per call of this method.
         * The value of it will change to the index of matching maps.
         * If the IntVariable of the resulting iterator is changed, the current index used by the iterator also changes to that value
         * @throws IllegalArgumentException If the content of the list is not of type {@link MapVariable.MapVariableType}
         */
        public static Iterator<IntVariable> getIndicesOfMapInList(ListVariable list, MapVariable map) {
            if(!(list.getContentType() instanceof MapVariable.MapVariableType)) {
                throw new IllegalArgumentException("The list content type must be a map");
            }
            return new AbstractIterator<>() {

                private final IntVariable index = new IntVariable(-1);
                private final int size = list.intValue();

                @Nullable
                @Override
                protected IntVariable computeNext() {
                    for(int i = index.intValue() + 1; i < size; ++i) {
                        index.setValue(i);
                        Variable var = list.get(index);
                        if(var != null && map.existingKeysMatch((MapVariable) var)) {
                            return index;
                        }
                    }
                    return endOfData();
                }
            };
        }
    }

    /**
     * A static variable child is a child, that has its own accessor. The parent variable usually has a constant amount of children.
     * Used by {@link KeyAccessor#setChildren}, {@link ValueAccessor#setChildren}, {@link XAccessor#setChildren} and similar to set all children of a map entry or position to a value:
     * What child to set is defined by the setter, it gets the variable and value and should also cast the value to the type of the child.<br/>
     * If only one current value is present and only one value to set it to is present, the setter is invoked on that current value and the value to set it to. <br/>
     * If only one current value is present, but multiple values to set it to are present, {@link VariablePath#MULTIPLE_VALUES_TO_SINGLE_VARIABLE_EXCEPTION} is thrown.<br/>
     * If multiple current values are present and only one value to set it to is present, the setter is invoked on each current value and the value to set it to.<br/>
     * If multiple current values are present and multiple values to set it to are present, the setter is invoked on each current value with one value to set it to each until to more current values or values to set them to are present
     * @param current The current value of the variable path
     * @param value The value to set the children to
     * @param cc The command context for sending errors
     * @param setter The setter to use to set the children
     * @param parentCaster Casts the {@link Variable} from the current value to the correct type or throws an exception, if that is not possible
     * @param <ParentType> The parent contained in the current value. This is a {@link Variable}, <i>not</i> {@link Variable.VariableType}. For example {@code <MapEntryVariable>}
     * @return The amount of children set
     * @throws CommandSyntaxException The parentCaster threw an error, multiple values to set a single current value to were present or the setter threw an exception when only a single current value and a single value to set it to were present
     */
    static <ParentType extends Variable> int setStaticVariableChildren(Either<VariableHolder, Stream<Variable>> current, Either<VariableHolder, Stream<Variable>> value, CommandContext<ServerCommandSource> cc, StaticChildSetter<ParentType> setter, VariableCaster<ParentType> parentCaster) throws CommandSyntaxException {
        if(current.left().isPresent()) {
            ParentType parent = parentCaster.cast(current.left().get().variable);
            if(value.left().isPresent()) {
                setter.set(parent, value.left().get().variable);
                return 1;
            }
            throw VariablePath.MULTIPLE_VALUES_TO_SINGLE_VARIABLE_EXCEPTION.create();
        }
        if(current.right().isEmpty()) {
            throw new IllegalArgumentException("Invalid Either passed as current value. Neither left nor right were present");
        }
        Stream<Variable> currentStream = current.right().get();
        return value.map(
                valueHolder -> currentStream.mapToInt(variable -> {
                    try {
                        ParentType parent = parentCaster.cast(variable);
                        setter.set(parent, valueHolder.variable);
                        return 1;
                    } catch (CommandSyntaxException e) {
                        cc.getSource().sendError(Texts.toText(e.getRawMessage()));
                    }
                    return 0;
                }).sum(),
                valueStream -> {
                    int changed = 0;
                    Iterator<Variable> currentIterator = currentStream.iterator(), valueIterator = valueStream.iterator();
                    while (currentIterator.hasNext() && valueIterator.hasNext()) {
                        try {
                            ParentType parent = parentCaster.cast(currentIterator.next());
                            setter.set(parent, valueIterator.next());
                            ++changed;
                        } catch (CommandSyntaxException e) {
                            cc.getSource().sendError(Texts.toText(e.getRawMessage()));
                        }
                    }
                    return changed;
                });
    }

    static <ParentType extends Variable> Either<VariableHolder, Stream<Variable>> getStaticChildren(Either<VariableHolder, Stream<Variable>> current, CommandContext<ServerCommandSource> cc, StaticChildGetter<ParentType> getter, VariableCaster<ParentType> parentCaster, StaticChildSetter<ParentType> optionalSetter) {
        return current.map(
                holder -> {
                    if (holder == null) return null;
                    try {
                        ParentType parent = parentCaster.cast(holder.variable);
                        Variable child = getter.get(parent);
                        if(child == null && optionalSetter != null) {
                            child = parent.getType().getChild(0).createVariable();
                            optionalSetter.set(parent, child);
                        }
                        return Either.left(new VariableHolder(child));
                    } catch(CommandSyntaxException e) {
                        cc.getSource().sendError(Texts.toText(e.getRawMessage()));
                        return null;
                    }
                },
                stream -> Either.right(stream.flatMap(variable -> {
                    if (variable == null) return null;
                    try {
                        ParentType parent = parentCaster.cast(variable);
                        Variable child = getter.get(parent);
                        if(child == null && optionalSetter != null) {
                            child = parent.getType().getChild(0).createVariable();
                            optionalSetter.set(parent, child);
                        }
                        return Stream.of(child);
                    } catch(CommandSyntaxException e) {
                        cc.getSource().sendError(Texts.toText(e.getRawMessage()));
                        return Stream.of();
                    }
                })));
    }

    @FunctionalInterface
    interface StaticChildSetter<ParentType extends Variable> {

        /**
         * Casts the value to the type of the child of the variable and sets the child to the value
         * @param variable The variable to set the child of
         * @param value The value to set the child to
         * @throws CommandSyntaxException An exception was thrown when casting the value to the type of the child
         */
        void set(ParentType variable, Variable value) throws CommandSyntaxException;
    }

    @FunctionalInterface
    interface StaticChildGetter<ParentType extends Variable> {

        Variable get(ParentType variable);
    }

    @FunctionalInterface
    interface VariableCaster<TargetType extends Variable> {

        /**
         * Casts the instance to the specified type and returns that same instance.
         * If the given instance is not of the correct type, an error is thrown.
         * @param value The value to cast
         * @return The casted value
         */
        TargetType cast(Variable value) throws CommandSyntaxException;

        VariableCaster<MapEntryVariable> MAP_ENTRY_VARIABLE_CASTER = value -> {
            if(value instanceof MapEntryVariable entry) {
                return entry;
            }
            throw VariablePath.VARIABLE_NOT_ENTRY_EXCEPTION.create();
        };
        VariableCaster<PosVariable> POSITION_VARIABLE_CASTER = value -> {
            if(value instanceof PosVariable entry) {
                return entry;
            }
            throw VariablePath.VARIABLE_NOT_POS_EXCEPTION.create();
        };
    }

    /**
     * Accesses all content of an {@link IndexableVariable}
     */
    class AllContentAccessor implements PathChildrenAccessor {

        public static final AllContentAccessor INSTANCE = new AllContentAccessor();

        @Override
        public Either<VariableHolder, Stream<Variable>> getChildren(Either<VariableHolder, Stream<Variable>> current, CommandContext<ServerCommandSource> cc, boolean createIfMissing) throws CommandSyntaxException {
            if(current.left().isPresent()) {
                if(!(current.left().get().variable instanceof IndexableVariable indexable)) {
                    throw VariablePath.VARIABLE_NOT_INDEXABLE_EXCEPTION.create();
                }
                if(createIfMissing && indexable instanceof ListVariable list) {
                    // Only a list can have 'null' contents, because a map contains 'MapEntryVariable's
                    IntVariable index = new IntVariable();
                    for(int i = 0; i < list.intValue(); ++i) {
                        index.set(i);
                        list.ensureIndexAndGetNonNull(index);
                    }
                }
                return Either.right(indexable.getContents());
            }
            if(current.right().isEmpty()) {
                throw new IllegalArgumentException("Invalid Either passed as current value. Neither left nor right were present");
            }
            return Either.right(current.right().get().flatMap(variable -> {
                if(!(variable instanceof IndexableVariable indexable)) {
                    cc.getSource().sendError(Texts.toText(VariablePath.VARIABLE_NOT_INDEXABLE_EXCEPTION.create().getRawMessage()));
                    return Stream.empty();
                }
                return indexable.getContents();
            }));
        }

        @Override
        public int setChildren(Either<VariableHolder, Stream<Variable>> current, Either<VariableHolder, Stream<Variable>> value, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
            if(current.left().isPresent()) {
                if(!(current.left().get().variable instanceof IndexableVariable indexable)) {
                    throw VariablePath.VARIABLE_NOT_INDEXABLE_EXCEPTION.create();
                }
                if(value.left().isPresent()) {
                    return indexable.setAll(VariableManager.castVariable(indexable.getContentType(), value.left().get().variable));
                }
                throw VariablePath.MULTIPLE_VALUES_TO_SINGLE_VARIABLE_EXCEPTION.create();
            }
            if(current.right().isEmpty()) {
                throw new IllegalArgumentException("Invalid Either passed as current value. Neither left nor right were present");
            }
            Stream<Variable> currentStream = current.right().get();
            return value.map(
                    valueHolder -> currentStream.mapToInt(var -> {
                        if(!(var instanceof IndexableVariable indexable)) {
                            cc.getSource().sendError(Texts.toText(VariablePath.VARIABLE_NOT_INDEXABLE_EXCEPTION.create().getRawMessage()));
                            return 0;
                        }
                        return indexable.setAll(valueHolder.variable);
                    }).sum(),
                    valueStream -> {
                        Iterator<Variable> currentIterator = currentStream.iterator(), valueIterator = valueStream.iterator();
                        int changed = 0;
                        while(currentIterator.hasNext() && valueIterator.hasNext()) {
                            if(!(currentIterator.next() instanceof IndexableVariable indexable)) {
                                valueIterator.next();
                                cc.getSource().sendError(Texts.toText(VariablePath.VARIABLE_NOT_INDEXABLE_EXCEPTION.create().getRawMessage()));
                                continue;
                            }
                            changed += indexable.setAll(valueIterator.next());
                        }
                        return changed;
                    }
            );
        }

        @Override
        public int removeChildren(Either<VariableHolder, Stream<Variable>> current, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
            if(current.left().isPresent()) {
                if(!(current.left().get().variable instanceof IndexableVariable indexable)) {
                    throw VariablePath.VARIABLE_NOT_INDEXABLE_EXCEPTION.create();
                }
                return indexable.clear();
            }
            if(current.right().isEmpty()) {
                throw new IllegalArgumentException("Invalid Either passed as current value. Neither left nor right were present");
            }
            return current.right().get().mapToInt(variable -> {
                if(!(variable instanceof IndexableVariable indexable)) {
                    cc.getSource().sendError(Texts.toText(VariablePath.VARIABLE_NOT_INDEXABLE_EXCEPTION.create().getRawMessage()));
                    return 0;
                }
                return indexable.clear();
            }).sum();
        }

        private final Function<IndexableVariable, List<CriterionPath.ChildDescriptor>> childDescriptors = Util.memoize(
                indexable -> indexable.getIndices().map(var -> new CriterionPath.ChildDescriptor(CriterionPath.ChildType.INDEXED, var)).collect(Collectors.toList()));

        @Override
        public List<CriterionPath.ChildDescriptor> getChildDescriptors(Variable value, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
            if(!(value instanceof IndexableVariable indexable)) {
                throw VariablePath.VARIABLE_NOT_INDEXABLE_EXCEPTION.create();
            }
            return childDescriptors.apply(indexable);
        }
    }

    /**
     * Accesses the keys of all map entry variables of the current value.<br/>
     * It is not possible to remove the key, so a {@link VariablePath#UNABLE_TO_REMOVE_FROM_ENTRY_EXCEPTION} is thrown when {@link KeyAccessor#removeChildren} is called.
     */
    class KeyAccessor implements PathChildrenAccessor {

        public static final KeyAccessor INSTANCE = new KeyAccessor();

        @Override
        public Either<VariableHolder, Stream<Variable>> getChildren(Either<VariableHolder, Stream<Variable>> current, CommandContext<ServerCommandSource> cc, boolean createIfMissing) {
            return getStaticChildren(current, cc, entry -> entry.key, VariableCaster.MAP_ENTRY_VARIABLE_CASTER, createIfMissing ? KeyAccessor::castAndSet : null);
        }

        @Override
        public int setChildren(Either<VariableHolder, Stream<Variable>> current, Either<VariableHolder, Stream<Variable>> value, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
            return setStaticVariableChildren(current, value, cc, KeyAccessor::castAndSet, VariableCaster.MAP_ENTRY_VARIABLE_CASTER);
        }

        private static void castAndSet(MapEntryVariable entry, Variable entryKey) throws CommandSyntaxException {
            entry.key = VariableManager.castVariable(entry.getType().getChild(0), entryKey);
        }

        @Override
        public int removeChildren(Either<VariableHolder, Stream<Variable>> current, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
            throw VariablePath.UNABLE_TO_REMOVE_FROM_ENTRY_EXCEPTION.create();
        }

        private static final List<CriterionPath.ChildDescriptor> childDescriptors = List.of(new CriterionPath.ChildDescriptor(CriterionPath.ChildType.ENTRY_KEY));

        @Override
        public List<CriterionPath.ChildDescriptor> getChildDescriptors(Variable value, CommandContext<ServerCommandSource> cc) {
            return childDescriptors;
        }
    }

    /**
     * Accesses the values of all map entry variables of the current value.<br/>
     * It is not possible to remove the value, so a {@link VariablePath#UNABLE_TO_REMOVE_FROM_ENTRY_EXCEPTION} is thrown when {@link ValueAccessor#removeChildren} is called.
     */
    class ValueAccessor implements PathChildrenAccessor {

        public static final ValueAccessor INSTANCE = new ValueAccessor();

        @Override
        public Either<VariableHolder, Stream<Variable>> getChildren(Either<VariableHolder, Stream<Variable>> current, CommandContext<ServerCommandSource> cc, boolean createIfMissing) {
            return getStaticChildren(current, cc, entry -> entry.value, VariableCaster.MAP_ENTRY_VARIABLE_CASTER, createIfMissing ? ValueAccessor::castAndSet : null);
        }

        @Override
        public int setChildren(Either<VariableHolder, Stream<Variable>> current, Either<VariableHolder, Stream<Variable>> value, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
            return setStaticVariableChildren(current, value, cc, ValueAccessor::castAndSet, VariableCaster.MAP_ENTRY_VARIABLE_CASTER);

        }

        private static void castAndSet(MapEntryVariable entry, Variable entryValue) throws CommandSyntaxException {
            entry.value = VariableManager.castVariable(entry.getType().getChild(0), entryValue);
        }

        @Override
        public int removeChildren(Either<VariableHolder, Stream<Variable>> current, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
            throw VariablePath.UNABLE_TO_REMOVE_FROM_ENTRY_EXCEPTION.create();
        }

        private static final List<CriterionPath.ChildDescriptor> childDescriptors = List.of(new CriterionPath.ChildDescriptor(CriterionPath.ChildType.ENTRY_VALUE));

        @Override
        public List<CriterionPath.ChildDescriptor> getChildDescriptors(Variable value, CommandContext<ServerCommandSource> cc) {
            return childDescriptors;
        }
    }

    /**
     * Accesses the x-coordinates of all position variables of the current value.<br/>
     * It is not possible to remove the coordinate, so a {@link VariablePath#UNABLE_TO_REMOVE_FROM_POS_EXCEPTION} is thrown when {@link ValueAccessor#removeChildren} is called.
     */
    class XAccessor implements PathChildrenAccessor {

        public static final XAccessor INSTANCE = new XAccessor();

        @Override
        public Either<VariableHolder, Stream<Variable>> getChildren(Either<VariableHolder, Stream<Variable>> current, CommandContext<ServerCommandSource> cc, boolean createIfMissing) {
            return getStaticChildren(current, cc, PosVariable::getX, VariableCaster.POSITION_VARIABLE_CASTER, createIfMissing ? XAccessor::castAndSet : null);
        }

        @Override
        public int setChildren(Either<VariableHolder, Stream<Variable>> current, Either<VariableHolder, Stream<Variable>> value, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
            return setStaticVariableChildren(current, value, cc, XAccessor::castAndSet, VariableCaster.POSITION_VARIABLE_CASTER);

        }

        private static void castAndSet(PosVariable pos, Variable entryValue) throws CommandSyntaxException {
            pos.setX(VariableManager.castVariable(DoubleVariable.DoubleVariableType.INSTANCE, entryValue));
        }

        @Override
        public int removeChildren(Either<VariableHolder, Stream<Variable>> current, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
            throw VariablePath.UNABLE_TO_REMOVE_FROM_POS_EXCEPTION.create();
        }

        private static final List<CriterionPath.ChildDescriptor> childDescriptors = List.of(new CriterionPath.ChildDescriptor(CriterionPath.ChildType.POS_X));

        @Override
        public List<CriterionPath.ChildDescriptor> getChildDescriptors(Variable value, CommandContext<ServerCommandSource> cc) {
            return childDescriptors;
        }
    }

    /**
     * Accesses the y-coordinates of all position variables of the current value.<br/>
     * It is not possible to remove the coordinate, so a {@link VariablePath#UNABLE_TO_REMOVE_FROM_POS_EXCEPTION} is thrown when {@link ValueAccessor#removeChildren} is called.
     */
    class YAccessor implements PathChildrenAccessor {

        public static final YAccessor INSTANCE = new YAccessor();

        @Override
        public Either<VariableHolder, Stream<Variable>> getChildren(Either<VariableHolder, Stream<Variable>> current, CommandContext<ServerCommandSource> cc, boolean createIfMissing) {
            return getStaticChildren(current, cc, PosVariable::getY, VariableCaster.POSITION_VARIABLE_CASTER, createIfMissing ? YAccessor::castAndSet : null);
        }

        @Override
        public int setChildren(Either<VariableHolder, Stream<Variable>> current, Either<VariableHolder, Stream<Variable>> value, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
            return setStaticVariableChildren(current, value, cc, YAccessor::castAndSet, VariableCaster.POSITION_VARIABLE_CASTER);

        }

        private static void castAndSet(PosVariable pos, Variable entryValue) throws CommandSyntaxException {
            pos.setY(VariableManager.castVariable(DoubleVariable.DoubleVariableType.INSTANCE, entryValue));
        }

        @Override
        public int removeChildren(Either<VariableHolder, Stream<Variable>> current, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
            throw VariablePath.UNABLE_TO_REMOVE_FROM_POS_EXCEPTION.create();
        }

        private static final List<CriterionPath.ChildDescriptor> childDescriptors = List.of(new CriterionPath.ChildDescriptor(CriterionPath.ChildType.POS_Y));

        @Override
        public List<CriterionPath.ChildDescriptor> getChildDescriptors(Variable value, CommandContext<ServerCommandSource> cc) {
            return childDescriptors;
        }
    }

    /**
     * Accesses the x-coordinates of all position variables of the current value.<br/>
     * It is not possible to remove the coordinate, so a {@link VariablePath#UNABLE_TO_REMOVE_FROM_POS_EXCEPTION} is thrown when {@link ValueAccessor#removeChildren} is called.
     */
    class ZAccessor implements PathChildrenAccessor {

        public static final ZAccessor INSTANCE = new ZAccessor();

        @Override
        public Either<VariableHolder, Stream<Variable>> getChildren(Either<VariableHolder, Stream<Variable>> current, CommandContext<ServerCommandSource> cc, boolean createIfMissing) {
            return getStaticChildren(current, cc, PosVariable::getZ, VariableCaster.POSITION_VARIABLE_CASTER, createIfMissing ? ZAccessor::castAndSet : null);
        }

        @Override
        public int setChildren(Either<VariableHolder, Stream<Variable>> current, Either<VariableHolder, Stream<Variable>> value, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
            return setStaticVariableChildren(current, value, cc, ZAccessor::castAndSet, VariableCaster.POSITION_VARIABLE_CASTER);

        }

        private static void castAndSet(PosVariable pos, Variable entryValue) throws CommandSyntaxException {
            pos.setZ(VariableManager.castVariable(DoubleVariable.DoubleVariableType.INSTANCE, entryValue));
        }

        @Override
        public int removeChildren(Either<VariableHolder, Stream<Variable>> current, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
            throw VariablePath.UNABLE_TO_REMOVE_FROM_POS_EXCEPTION.create();
        }

        private static final List<CriterionPath.ChildDescriptor> childDescriptors = List.of(new CriterionPath.ChildDescriptor(CriterionPath.ChildType.POS_Z));

        @Override
        public List<CriterionPath.ChildDescriptor> getChildDescriptors(Variable value, CommandContext<ServerCommandSource> cc) {
            return childDescriptors;
        }
    }
}
