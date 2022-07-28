package net.papierkorb2292.command_expander.variables;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.NbtByteArray;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentStateManager;
import net.papierkorb2292.command_expander.CommandExpander;

import java.io.File;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;

public class VariableManager {

    private static final String COMMAND_VARIABLE_PREFIX = "variables/";

    static final Map<String, VariableTypeTemplate> TYPES_BY_STRING = new HashMap<>();
    static final ArrayList<VariableTypeTemplate> TYPES_BY_ID = new ArrayList<>();

    public static final Dynamic2CommandExceptionType INCOMPATIBLE_TYPES_EXCEPTION = new Dynamic2CommandExceptionType((targetType, sourceType) -> new LiteralMessage(String.format("Variable of type '%s' cannot be converted to type '%s'", sourceType, targetType)));
    public static final DynamicCommandExceptionType UNKNOWN_TYPE_EXCEPTION = new DynamicCommandExceptionType(type -> new LiteralMessage(String.format("Encountered unknown type '%s' while parsing variable type", type)));
    public static final Dynamic2CommandExceptionType INVALID_CHILDREN_COUNT_EXCEPTION = new Dynamic2CommandExceptionType((expectedCount, retrievedCount) -> new LiteralMessage(String.format("Encountered unexpected children: Expected %s, got %s", expectedCount, retrievedCount)));
    public static final DynamicCommandExceptionType VARIABLE_NOT_FOUND_EXCEPTION = new DynamicCommandExceptionType(name -> new LiteralMessage(String.format("Unable to find variable '%s'", name)));
    public static final DynamicCommandExceptionType VARIABLE_DATA_NOT_COMPOUND_EXCEPTION = new DynamicCommandExceptionType(name -> new LiteralMessage(String.format("Data of variable '%s' isn't a compound", name)));
    public static final DynamicCommandExceptionType TYPE_DATA_TOO_SHORT_EXCEPTION = new DynamicCommandExceptionType(name -> new LiteralMessage(String.format("Type in the data of variable '%s' has invalid length", name)));
    public static final DynamicCommandExceptionType UNABLE_TO_DECODE_VARIABLE_EXCEPTION = new DynamicCommandExceptionType(name -> new LiteralMessage(String.format("Unable to decode variable '%s'", name)));
    public static final Dynamic2CommandExceptionType INVALID_TYPE_ID = new Dynamic2CommandExceptionType((name, id)-> new LiteralMessage(String.format("Encountered invalid type id '%s' while decoding variable '%s'", id, name)));
    public static final Dynamic2CommandExceptionType VARIABLE_DATA_MISSING_ELEMENT_EXCEPTION = new Dynamic2CommandExceptionType((name, tag) -> new LiteralMessage(String.format("Unable to find nbt tag '%s' in variable '%s'", tag, name)));
    public static final DynamicCommandExceptionType VARIABLE_ALREADY_EXISTS_EXCEPTION = new DynamicCommandExceptionType(name -> new LiteralMessage(String.format("The variable '%s' already exists", name)));

    private final PersistentStateManager stateManager;
    private final Map<String, PersistentState> namespaces = new HashMap<>();

    public VariableManager(PersistentStateManager stateManager) {
        this.stateManager = stateManager;
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
        PersistentState state = stateManager.get(data -> createPersistentState(data, id.namespace), COMMAND_VARIABLE_PREFIX + id.namespace);
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
        PersistentState state = stateManager.get(data -> createPersistentState(data, id.namespace), COMMAND_VARIABLE_PREFIX + id.namespace);
        if(state == null) {
            throw VARIABLE_NOT_FOUND_EXCEPTION.create(id);
        }
        return state.getReadonly(id.path);
    }

    public void add(VariableIdentifier id, Variable.VariableType type) throws CommandSyntaxException {
        stateManager.getOrCreate(data -> createPersistentState(data, id.namespace), () -> createPersistentState(new NbtCompound(), id.namespace), COMMAND_VARIABLE_PREFIX + id.namespace).add(id.path, type);
    }

    public void remove(VariableIdentifier id) throws CommandSyntaxException {
        PersistentState state = stateManager.get(data -> createPersistentState(data, id.namespace), COMMAND_VARIABLE_PREFIX + id.namespace);
        if(state == null) {
            throw VARIABLE_NOT_FOUND_EXCEPTION.create(id);
        }
        state.remove(id.path);
    }

    public Stream<VariableIdentifier> getIds() {
        return this.namespaces.entrySet().stream().flatMap(entry -> entry.getValue().getIds());
    }

    private PersistentState createPersistentState(NbtCompound data, String namespace) {
        PersistentState state = new PersistentState(data, namespace);
        namespaces.put(namespace, state);
        return state;
    }

    private static void registerType(String name, Class<? extends Variable.VariableType> type, VariableTypeTemplate template) {
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
         * @throws CommandSyntaxException The type of var is incompatible with the target type
         * @see VariableManager#castVariable
         */
        Variable cast(Variable.VariableType type, Variable var) throws CommandSyntaxException;
    }

    public static class PersistentState extends net.minecraft.world.PersistentState {

        private final NbtCompound data;
        private final Map<String, TypedVariable> loadedVariables = new HashMap<>();
        private final String namespace;

        public PersistentState(NbtCompound data, String namespace) {
            this.data = data;
            this.namespace = namespace;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            nbt.copyFrom(data);
            Deque<Variable.VariableType> typeEncoderStack = new LinkedBlockingDeque<>();
            Queue<VariableTypeTemplate> typeEncoderEncounteredTypes = new LinkedBlockingQueue<>();
            for(Map.Entry<String, TypedVariable> entry : loadedVariables.entrySet()) {
                int typeSize = 1;
                typeEncoderStack.add(entry.getValue().type);
                while(!typeEncoderStack.isEmpty()) {
                    Variable.VariableType type = typeEncoderStack.pop();
                    VariableTypeTemplate typeTemplate = type.getTemplate();
                    typeEncoderEncounteredTypes.add(typeTemplate);
                    int childrenCount = typeTemplate.childrenCount;
                    typeSize += childrenCount;
                    for(int i = 0; i < childrenCount; ++i) {
                        typeEncoderStack.add(type.getChild(i));
                    }
                }
                byte[] typeArray = new byte[typeSize];
                int index = 0;
                while(!typeEncoderEncounteredTypes.isEmpty()) {
                    typeArray[index] = typeEncoderEncounteredTypes.remove().id;
                    ++index;
                }
                NbtByteArray typeNbt = new NbtByteArray(typeArray);
                DataResult<NbtElement> var =
                        entry.getValue().type.getTemplate()
                        .codec.encode(entry.getValue().var, NbtOps.INSTANCE, NbtOps.INSTANCE.empty());
                if(var.error().isPresent()) {
                    if(var.resultOrPartial(VariableManager::dumpError).isPresent()) {
                        CommandExpander.LOGGER.error("Error encoding variable '{}': {}", new Identifier(namespace, entry.getKey()), var.error().get().message());
                        var = var.promotePartial(VariableManager::dumpError);
                    }
                    else {
                        CommandExpander.LOGGER.error("FATAL error encoding variable '{}', no data could be recovered: {}", new Identifier(namespace, entry.getKey()), var.error().get().message());
                        continue;
                    }
                }
                var = var.flatMap(element -> NbtOps.INSTANCE.mapBuilder().add("type", typeNbt).build(element));
                if(var.error().isPresent()) {
                    CommandExpander.LOGGER.error("FATAL error adding type to encoded variable '{}', no data could be recovered: {}", new Identifier(namespace, entry.getKey()), var.error().get().message());
                    continue;
                }
                nbt.put(entry.getKey(), var.result().get());
            }
            return nbt;
        }

        @Override
        public void save(File file) {
            if(!isDirty()) {
                return;
            }
            if(!file.exists()) {
                file.getParentFile().mkdirs(); //parent folders have to be created
            }
            super.save(file);
        }

        private Variable.VariableType decodeType(byte[] type, OffsetHolder offset, String variableName) throws CommandSyntaxException {
            if(type.length - offset.value <= 0) {
                throw TYPE_DATA_TOO_SHORT_EXCEPTION.create(variableName);
            }
            byte id = type[offset.value];
            if(id < 0 || id >= TYPES_BY_ID.size()) {
                throw INVALID_TYPE_ID.create(variableName, id);
            }
            VariableTypeTemplate template = TYPES_BY_ID.get(id);
            Variable.VariableType result = template.typeFactory.get();
            ++offset.value;
            for(int i = 0; i < template.childrenCount; ++i) {
                result.setChild(i, decodeType(type, offset, variableName));
            }
            return result;
        }

        private static class OffsetHolder {

            public int value = 0;
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

                byte[] typeArray = variableData.getByteArray("type");
                Variable.VariableType type = decodeType(typeArray, new OffsetHolder(), name);
                DataResult<Pair<VariableHolder, NbtElement>> dataResult = TYPES_BY_ID.get(typeArray[0]).codec.decode(NbtOps.INSTANCE, variableData, type);
                Optional<Pair<VariableHolder, NbtElement>> var = dataResult.resultOrPartial(VariableManager::dumpError);
                if(dataResult.error().isPresent()) {
                    if(var.isPresent()) {
                        CommandExpander.LOGGER.error("Error decoding variable '{}': {} with error elements: {}", new Identifier(namespace, name), dataResult.error().get().message(), var.get().getSecond());
                    }
                    else {
                        CommandExpander.LOGGER.error("FATAL error decoding variable '{}', no data could be recovered: {}", new Identifier(namespace, name), dataResult.error().get().message());
                        throw UNABLE_TO_DECODE_VARIABLE_EXCEPTION.create(new Identifier(namespace, name));
                    }
                }
                result = new TypedVariable(type, var.get().getFirst().variable);
                loadedVariables.put(name, result);
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

    static {
        registerType("int", IntVariable.IntVariableType.class, IntVariable.IntVariableType.TEMPLATE);
        registerType("list", ListVariable.ListVariableType.class, ListVariable.ListVariableType.TEMPLATE);
        registerType("map", MapVariable.MapVariableType.class, MapVariable.MapVariableType.TEMPLATE);
        registerType("double", DoubleVariable.DoubleVariableType.class, DoubleVariable.DoubleVariableType.TEMPLATE);
        registerType("entry", MapEntryVariable.MapEntryVariableType.class, MapEntryVariable.MapEntryVariableType.TEMPLATE);
        registerType("long", LongVariable.LongVariableType.class, LongVariable.LongVariableType.TEMPLATE);
        registerType("entity", EntityVariable.EntityVariableType.class, EntityVariable.EntityVariableType.TEMPLATE);
    }

    public static void dumpError(String error) { }

}
