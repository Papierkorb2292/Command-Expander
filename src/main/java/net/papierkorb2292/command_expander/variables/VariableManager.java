package net.papierkorb2292.command_expander.variables;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.papierkorb2292.command_expander.CommandExpander;
import net.papierkorb2292.command_expander.variables.path.VariablePath;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class VariableManager {

    private static final String COMMAND_VARIABLE_PREFIX = "variables/";

    static final Map<String, VariableTypeTemplate> TYPES_BY_STRING = new HashMap<>();
    static final ArrayList<VariableTypeTemplate> TYPES_BY_ID = new ArrayList<>();

    public static final Dynamic2CommandExceptionType INCOMPATIBLE_TYPES_EXCEPTION = new Dynamic2CommandExceptionType((targetType, sourceType) -> new LiteralMessage(String.format("Variable of type '%s' cannot be converted to type '%s'", sourceType, targetType)));
    public static final Dynamic2CommandExceptionType PARSE_EXCEPTION = new Dynamic2CommandExceptionType((source, targetType) -> new LiteralMessage(String.format("Variable value '%s' cannot be converted to type '%s'", source, targetType)));
    public static final DynamicCommandExceptionType UNKNOWN_TYPE_EXCEPTION = new DynamicCommandExceptionType(type -> new LiteralMessage(String.format("Encountered unknown type '%s' while parsing variable type", type)));
    public static final Dynamic2CommandExceptionType INVALID_CHILDREN_COUNT_EXCEPTION = new Dynamic2CommandExceptionType((expectedCount, retrievedCount) -> new LiteralMessage(String.format("Encountered unexpected children: Expected %s, got %s", expectedCount, retrievedCount)));
    public static final DynamicCommandExceptionType VARIABLE_NOT_FOUND_EXCEPTION = new DynamicCommandExceptionType(name -> new LiteralMessage(String.format("Unable to find variable '%s'", name)));
    public static final DynamicCommandExceptionType VARIABLE_DATA_NOT_COMPOUND_EXCEPTION = new DynamicCommandExceptionType(name -> new LiteralMessage(String.format("Data of variable '%s' isn't a compound", name)));
    public static final DynamicCommandExceptionType TYPE_DATA_TOO_SHORT_EXCEPTION = new DynamicCommandExceptionType(name -> new LiteralMessage(String.format("Type in the data of variable '%s' has invalid length", name)));
    public static final DynamicCommandExceptionType UNABLE_TO_DECODE_VARIABLE_EXCEPTION = new DynamicCommandExceptionType(name -> new LiteralMessage(String.format("Unable to decode variable '%s'", name)));
    public static final Dynamic2CommandExceptionType INVALID_TYPE_ID = new Dynamic2CommandExceptionType((name, id)-> new LiteralMessage(String.format("Encountered invalid type id '%s' while decoding variable '%s'", id, name)));
    public static final Dynamic2CommandExceptionType VARIABLE_DATA_MISSING_ELEMENT_EXCEPTION = new Dynamic2CommandExceptionType((name, tag) -> new LiteralMessage(String.format("Unable to find nbt tag '%s' in variable '%s'", tag, name)));
    public static final DynamicCommandExceptionType VARIABLE_ALREADY_EXISTS_EXCEPTION = new DynamicCommandExceptionType(name -> new LiteralMessage(String.format("The variable '%s' already exists", name)));
    public static final SimpleCommandExceptionType CHILD_TYPE_WAS_NULL_BUT_CHILDREN_WERE_PRESENT_EXCEPTION = new SimpleCommandExceptionType(new LiteralMessage("Child type was null but children were present"));

    private final PersistentStateManager stateManager;
    private final Map<String, VariablePersistentState> namespaces = new HashMap<>();
    private final BoundCriteriaPersistentState criteriaPersistentState;
    final ServerScoreboard scoreboard;

    public VariableManager(PersistentStateManager stateManager, ServerScoreboard scoreboard) {
        this.stateManager = stateManager;
        this.scoreboard = scoreboard;
        criteriaPersistentState = stateManager.getOrCreate(this::createBoundCriteriaPersistentState, BoundCriteriaPersistentState::new, COMMAND_VARIABLE_PREFIX + "%bindings");
    }

    /**
     * Parses a VariableType tree from a string, example: "map&lt;int, list&lt;int&gt;&gt;"
     * @throws CommandSyntaxException Unknown type, invalid amount of children or a syntax error
     */
    public static Variable.VariableType parseType(StringReader reader, boolean allowNull) throws CommandSyntaxException {
        int startCursor = reader.getCursor(), childrenCursor = -1;
        char c;
        while(reader.canRead()) {
            c = reader.peek();
            if(c == ',' || c == '>' || c == ')') {
                break;
            }
            if(c == '<') {
                childrenCursor = reader.getCursor() + 1;
                break;
            }
            if(c == ' ') {
                int cursor = reader.getCursor();
                reader.skipWhitespace();
                if(reader.peek() == '<') {
                    reader.skipWhitespace();
                    childrenCursor = reader.getCursor() + 1;
                }
                reader.setCursor(cursor);
                break;
            }
            reader.skip();
        }
        String typeName = reader.getString().substring(startCursor, reader.getCursor());
        if(allowNull && typeName.equals("null")) {
            return null;
        }
        VariableTypeTemplate typeTemplate = TYPES_BY_STRING.get(typeName);
        if(typeTemplate == null) {
            throw UNKNOWN_TYPE_EXCEPTION.createWithContext(reader, typeName);
        }
        List<Variable.VariableType> childrenType = new ArrayList<>();
        if(childrenCursor != -1) {
            reader.setCursor(childrenCursor);
            if(reader.peek() != '>') {
                while (reader.canRead()) {
                    reader.skipWhitespace();
                    childrenType.add(parseType(reader, allowNull));
                    reader.skipWhitespace();
                    if(reader.peek() == '>') {
                        childrenCursor = -1;
                        break;
                    }
                    if (reader.read() != ',') {
                        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedSymbol().createWithContext(reader, ',');
                    }
                }
                if (childrenCursor != -1) { //No '>' found
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedSymbol().createWithContext(reader, '>');
                }
            }
            reader.skip(); //Skip closing bracket
            reader.skipWhitespace();
        }
        if(childrenType.size() != typeTemplate.childrenCount) {
            throw INVALID_CHILDREN_COUNT_EXCEPTION.createWithContext(reader, typeTemplate.childrenCount, childrenType.size());
        }
        Variable.VariableType result = typeTemplate.typeFactory.get();
        for(int i = 0; i < childrenType.size(); ++i) {
            result.setChild(i, childrenType.get(i));
        }
        return result;
    }

    public static Variable castVariable(Variable.VariableType type, Variable value) throws CommandSyntaxException {
        if(type == null) {
            return value;
        }
        return type.getTemplate().caster.cast(type, value);
    }

    public static Set<String> getTypes() {
        return TYPES_BY_STRING.keySet();
    }

    public static VariableTypeTemplate getType(String name) {
        return TYPES_BY_STRING.get(name);
    }

    public TypedVariable get(VariableIdentifier id) throws CommandSyntaxException {
        VariablePersistentState state = stateManager.get(data -> createVariablePersistentState(data, id.namespace), COMMAND_VARIABLE_PREFIX + id.namespace);
        if(state == null) {
            throw VARIABLE_NOT_FOUND_EXCEPTION.create(id);
        }
        return state.get(id.path);
    }

    /**
     * Loads and gets a variable, but doesn't mark it dirty, so changes are
     * not saved unless {@link #get} is used with the same id
     * @param id The id of the variable to load and get
     * @return The value of the variable
     * @throws CommandSyntaxException The variable wasn't found or an error happened when loading it
     */
    public TypedVariable getReadonly(VariableIdentifier id) throws CommandSyntaxException {
        VariablePersistentState state = stateManager.get(data -> createVariablePersistentState(data, id.namespace), COMMAND_VARIABLE_PREFIX + id.namespace);
        if(state == null) {
            throw VARIABLE_NOT_FOUND_EXCEPTION.create(id);
        }
        return state.getReadonly(id.path);
    }

    public void add(VariableIdentifier id, Variable.VariableType type) throws CommandSyntaxException {
        stateManager.getOrCreate(data -> createVariablePersistentState(data, id.namespace), () -> createVariablePersistentState(new NbtCompound(), id.namespace), COMMAND_VARIABLE_PREFIX + id.namespace).add(id.path, type);
    }

    public void remove(VariableIdentifier id) throws CommandSyntaxException {
        VariablePersistentState state = stateManager.get(data -> createVariablePersistentState(data, id.namespace), COMMAND_VARIABLE_PREFIX + id.namespace);
        if(state == null) {
            throw VARIABLE_NOT_FOUND_EXCEPTION.create(id);
        }
        state.remove(id.path);
    }

    public Stream<VariableIdentifier> getIds() {
        return this.namespaces.entrySet().stream().flatMap(entry -> entry.getValue().getIds());
    }

    private VariablePersistentState createVariablePersistentState(NbtCompound data, String namespace) {
        VariablePersistentState state = new VariablePersistentState(data, namespace);
        namespaces.put(namespace, state);
        return state;
    }

    private BoundCriteriaPersistentState createBoundCriteriaPersistentState(NbtCompound data) {
        return new BoundCriteriaPersistentState(data, this, scoreboard);
    }

    private static void registerType(String name, VariableTypeTemplate template) {
        TYPES_BY_STRING.put(name, template);
        template.id = (byte)TYPES_BY_ID.size();
        TYPES_BY_ID.add(template);
    }

    @FunctionalInterface
    public interface Caster {
        /**
         * Casts var to the specified type
         * @param type The type used for getting the types to cast the children, the instance of the caster should be type.getTemplate().caster
         * @param var the variable to cast to the type
         * @throws CommandSyntaxException The type of var is incompatible with the target type or the variable couldn't be parsed
         * @see VariableManager#castVariable
         */
        Variable cast(Variable.VariableType type, Variable var) throws CommandSyntaxException;
    }

    public static class VariablePersistentState extends PersistentState {

        private final NbtCompound data;
        private final Map<String, TypedVariable> loadedVariables = new HashMap<>();
        private final String namespace;

        public VariablePersistentState(NbtCompound data, String namespace) {
            this.data = data;
            this.namespace = namespace;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            nbt.copyFrom(data);
            for(Map.Entry<String, TypedVariable> entry : loadedVariables.entrySet()) {
                DataResult<NbtElement> dataResult = TypedVariable.encode(entry.getValue(), NbtOps.INSTANCE, NbtOps.INSTANCE.empty());
                Optional<NbtElement> parsedElement = dataResult.resultOrPartial(VariableManager.dumpError);
                if(dataResult.error().isPresent()) {
                    if(parsedElement.isPresent()) {
                        CommandExpander.LOGGER.error("Error encoding variable '{}': {}", new Identifier(namespace, entry.getKey()), dataResult.error().get().message());
                        continue;
                    }
                    CommandExpander.LOGGER.error("FATAL error encoding variable '{}', no data could be recovered: {}", new Identifier(namespace, entry.getKey()), dataResult.error().get().message());
                    continue;
                }
                if(parsedElement.isPresent()) {
                    nbt.put(entry.getKey(), parsedElement.get());
                }
            }
            return nbt;
        }

        @Override
        public void save(File file) {
            if(!isDirty()) {
                return;
            }
            if(!file.exists()) {
                //noinspection ResultOfMethodCallIgnored
                file.getParentFile().mkdirs(); //parent folders have to be created
            }
            super.save(file);
        }

        private TypedVariable getOrLoad(String name) throws CommandSyntaxException {
            TypedVariable result = loadedVariables.get(name);
            if(result == null) {
                NbtElement variableDataElement = data.get(name);
                if(variableDataElement == null) {
                    throw VARIABLE_NOT_FOUND_EXCEPTION.create(new Identifier(namespace, name));
                }
                if(!(variableDataElement instanceof NbtCompound variableData)) {
                    throw VARIABLE_DATA_NOT_COMPOUND_EXCEPTION.create(new Identifier(namespace, name));
                }
                if(!variableData.contains("type", 7)) {
                    throw VARIABLE_DATA_MISSING_ELEMENT_EXCEPTION.create(new Identifier(namespace, name), "type");
                }

                DataResult<Pair<TypedVariable, NbtElement>> dataResult = TypedVariable.decode(variableDataElement, NbtOps.INSTANCE);
                Optional<Pair<TypedVariable, NbtElement>> var = dataResult.resultOrPartial(VariableManager.dumpError);
                if(dataResult.error().isPresent()) {
                    if(var.isPresent()) {
                        CommandExpander.LOGGER.error("Error decoding variable '{}': {} with error elements: {}", new Identifier(namespace, name), dataResult.error().get().message(), var.get().getSecond());
                    }
                    else {
                        CommandExpander.LOGGER.error("FATAL error decoding variable '{}', no data could be recovered: {}", new Identifier(namespace, name), dataResult.error().get().message());
                        throw UNABLE_TO_DECODE_VARIABLE_EXCEPTION.create(new Identifier(namespace, name));
                    }
                }
                if(var.isPresent()) {
                    result = var.get().getFirst();
                    loadedVariables.put(name, result);
                }
            }
            return result;
        }

        public TypedVariable get(String name) throws CommandSyntaxException {
            markDirty(); //The variable can be changed without the state knowing
            return getOrLoad(name);
        }

        public TypedVariable getReadonly(String name) throws CommandSyntaxException {
            return getOrLoad(name);
        }

        public Stream<VariableIdentifier> getIds() {
            return data.getKeys().stream().map(key -> {
                try {
                    return new VariableIdentifier(namespace, key);
                } catch (CommandSyntaxException e) {
                    CommandExpander.LOGGER.error("Unexpected exception creating list of existing variable ids: ", e);
                }
                return null;
            }).filter(Objects::nonNull);
        }

        public void add(String name, Variable.VariableType type) throws CommandSyntaxException {
            if(loadedVariables.containsKey(name) || data.contains(name, NbtElement.COMPOUND_TYPE)) {
                throw VARIABLE_ALREADY_EXISTS_EXCEPTION.create(new Identifier(namespace, name));
            }
            loadedVariables.put(name, new TypedVariable(type, null));
            markDirty();
        }

        public void remove(String name) throws CommandSyntaxException {
            boolean found = false;
            if(data.contains(name, NbtElement.COMPOUND_TYPE)) {
                found = true;
                data.remove(name);
            }
            if(loadedVariables.remove(name) != null) {
                found = true;
            }
            if(!found) {
                throw VARIABLE_NOT_FOUND_EXCEPTION.create(new Identifier(namespace, name));
            }
            markDirty();
        }
    }

    public void updateBoundVariables(ScoreboardCriterion criterion, String player, Consumer<ScoreboardPlayerScore> action) {
        CriterionBinding binding = criteriaPersistentState.bindings.get(criterion.getName());
        if(binding != null) {
            for (CriterionPath.BoundVariable boundVariable : binding.variables) {
                action.accept(boundVariable.getScore(player));
            }
        }
    }

    public int bindCriteria(VariablePath path, List<String> criteria, CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int addedBindings = 0;
        for(String criterion : criteria) {
            CriterionBinding criterionBinding = criteriaPersistentState.bindings.computeIfAbsent(criterion, key -> new CriterionBinding());
            addedBindings += criterionBinding.bind(path, this, context);
        }
        if(addedBindings > 0) {
            criteriaPersistentState.markDirty();
        }
        return addedBindings;
    }

    public int unbindCriteria(VariablePath path, List<String> criteria, CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int removedBindings = 0;
        for(String criterion : criteria) {
            CriterionBinding criterionBinding = criteriaPersistentState.bindings.get(criterion);
            if(criterionBinding != null) {
                removedBindings += criterionBinding.unbind(path, this, context);
            }
        }
        if(removedBindings > 0) {
            criteriaPersistentState.markDirty();
        }
        return removedBindings;
    }

    public void updateVariableBindingReferences(VariablePath path, CommandContext<ServerCommandSource> cc) {
        for(CriterionBinding binding : criteriaPersistentState.bindings.values()) {
            try {
                binding.updateReferences(path, this, cc);
            } catch (CommandSyntaxException e) {
                CommandExpander.LOGGER.error("Error updating bound variable references: ", e);
            }
        }
    }

    public void removeVariableBindingReferences(VariablePath path, CommandContext<ServerCommandSource> cc) {
        Iterator<CriterionBinding> iterator = criteriaPersistentState.bindings.values().iterator();
        while (iterator.hasNext()) {
            CriterionBinding binding = iterator.next();
            try {
                binding.removeReferences(path, this, cc);
            } catch (CommandSyntaxException e) {
                CommandExpander.LOGGER.error("Error removing bound variable references: ", e);
            }
            if(binding.variables.isEmpty()) {
                iterator.remove();
            }
        }
    }

    /**
     * A persistent state storing a mapping of criteria names to their {@link CriterionBinding}s.
     * Its nbt form is a compound with the tags representing the criteria names and the nbt form of {@link CriterionBinding} as values
     */
    public static class BoundCriteriaPersistentState extends PersistentState {

        final Map<String, CriterionBinding> bindings = new HashMap<>();

        public BoundCriteriaPersistentState() { }

        public BoundCriteriaPersistentState(NbtCompound data, VariableManager manager, ServerScoreboard scoreboard) {
            for(String criterion : data.getKeys()) {
                if(ScoreboardCriterion.getOrCreateStatCriterion(criterion).isEmpty()) {
                    CommandExpander.LOGGER.warn("Encountered unknown criterion '{}' when reading variable bindings", criterion);
                    continue;
                }
                bindings.put(criterion, CriterionBinding.read(manager, criterion, data.getCompound(criterion), scoreboard));
            }
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            for(Map.Entry<String, CriterionBinding> binding : bindings.entrySet()) {
                if(binding.getValue().paths.size() > 0) {
                    nbt.put(binding.getKey(), binding.getValue().write(new NbtCompound()));
                }
            }
            return nbt;
        }
    }

    static {
        registerType("int", IntVariable.IntVariableType.TEMPLATE);
        registerType("list", ListVariable.ListVariableType.TEMPLATE);
        registerType("map", MapVariable.MapVariableType.TEMPLATE);
        registerType("double", DoubleVariable.DoubleVariableType.TEMPLATE);
        registerType("entry", MapEntryVariable.MapEntryVariableType.TEMPLATE);
        registerType("long", LongVariable.LongVariableType.TEMPLATE);
        registerType("entity", EntityVariable.EntityVariableType.TEMPLATE);
        registerType("byte", ByteVariable.ByteVariableType.TEMPLATE);
        registerType("short", ShortVariable.ShortVariableType.TEMPLATE);
        registerType("float", FloatVariable.FloatVariableType.TEMPLATE);
        registerType("string", StringVariable.StringVariableType.TEMPLATE);
        registerType("pos", PosVariable.PosVariableType.TEMPLATE);
        registerType("iterator", IteratorVariable.IteratorVariableType.TEMPLATE);
    }

    public static final Consumer<String> dumpError = error -> { };
}
