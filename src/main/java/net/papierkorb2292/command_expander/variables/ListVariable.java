package net.papierkorb2292.command_expander.variables;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.papierkorb2292.command_expander.variables.immediate.operator.AddableOperatorVariableType;
import net.papierkorb2292.command_expander.variables.immediate.operator.SubtractableOperatorVariableType;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ListVariable extends IndexableVariable {

    final ListVariableType type;
    final List<Variable> value = new ArrayList<>();

    public ListVariable(ListVariableType type) {
        this.type = type;
    }

    @Override
    public int intValue() {
        removeEndingNulls();
        return value.size();
    }

    @Override
    public long longValue() {
        return intValue();
    }

    @Override
    public float floatValue() {
        return intValue();
    }

    @Override
    public double doubleValue() {
        return intValue();
    }

    @Override
    public String stringValue() {
        removeEndingNulls();
        StringBuilder sb = new StringBuilder();
        sb.append("{ ");
        if(value.size() > 0) {
            Variable var = value.get(0);
            sb.append(var == null ? "null" : var.stringValue());
            for(int i = 1; i < value.size(); ++i) {
                sb.append(", ");
                var = value.get(i);
                sb.append(var == null ? "null" : var.stringValue());
            }
        }
        sb.append(" }");
        return sb.toString();
    }

    @Override
    public VariableType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ListVariable && value.equals(((ListVariable)o).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public NbtElement toNbt() throws CommandSyntaxException {
        removeEndingNulls();
        NbtList result = new NbtList();
        for(Variable var : value) {
            result.add(Variable.createNbt(var));
        }
        return result;
    }

    @Override
    public Variable get(Variable indexVar) {
        int index = indexVar.intValue();
        return index >= 0 && index < value.size() ? value.get(indexVar.intValue()) : null;
    }

    @Override
    public boolean ensureIndexExists(Variable indexVar) {
        int index = indexVar.intValue();
        if(index >= 0 && index < value.size()) {
            return true;
        }
        int indexDifference = index - value.size();
        if(indexDifference == 0) { //Adding single element without array
            value.add(null);
            return false;
        }
        value.addAll(Collections.nCopies(indexDifference + 1, null));
        return false;
    }

    @Override
    public boolean set(Variable indexVar, Variable value) {
        int index = indexVar.intValue();
        if(index >= 0 && index < this.value.size()) {
            Variable prev = this.value.set(index, value);
            if(value == null && index == this.value.size() - 1) {
                removeEndingNulls();
            }
            return !Objects.equals(prev, value);
        }
        return false;
    }

    @Override
    public boolean remove(Variable indexVar) {
        int index = indexVar.intValue(), lastIndex = value.size() - 1;
        if(index >= 0 && index < lastIndex) {
            value.set(index, null);
            return true;
        }
        if(index == lastIndex) {
            value.remove(lastIndex);
            return true;
        }
        return false;
    }

    @Override
    public Variable ensureIndexCompatible(Variable indexVar) {
        return indexVar;
    }

    @Override
    public int clear() {
        int size = value.size();
        value.clear();
        return size;
    }

    @Override
    public int setAll(Variable value) {
        if(value == null) {
            return clear();
        }
        for(int i = 0; i < this.value.size(); ++i) {
            this.value.set(i, value);
        }
        return this.value.size();
    }

    @Override
    public VariableType getContentType() {
        return type.content;
    }

    @Override
    public Stream<Variable> getContents() {
        return value.stream();
    }

    @Override
    public Stream<Variable> getIndices() {
        return IntStream.range(0, value.size()).mapToObj(IntVariable::new);
    }

    public void removeEndingNulls() {
        int i = value.size() - 1;
        while(i >= 0 && value.get(i) == null) {
            value.remove(i);
            i--;
        }
    }

    public static class ListVariableType implements VariableType, AddableOperatorVariableType, SubtractableOperatorVariableType {

        public VariableType content;

        public ListVariableType() { }
        public ListVariableType(VariableType content) {
            this.content = content;
        }

        @Override
        public Variable createVariable() {
            return new ListVariable(this);
        }

        @Override
        public VariableTypeTemplate getTemplate() {
            return TEMPLATE;
        }

        @Override
        public VariableType getNextLoweredType() {
            return IntVariable.IntVariableType.INSTANCE;
        }

        @Override
        public void setChild(int index, VariableType child) {
            if (index == 0) {
                content = child;
            }
        }

        @Override
        public VariableType getChild(int index) {
            return index == 0 ? content : null;
        }

        @Override
        public String getName() {
            return "list";
        }

        public static final VariableTypeTemplate TEMPLATE = new VariableTypeTemplate(1, ListVariableType::new, (type, var) -> {
            ListVariableType listType = (ListVariableType)type;
            VariableType childrenType = listType.content;
            boolean nullType = childrenType == null;
            VariableManager.Caster childrenCaster = nullType ? null : childrenType.getTemplate().caster;
            if(var instanceof MapVariable map) {
                MapEntryVariable.MapEntryVariableType mapEntryType = (MapEntryVariable.MapEntryVariableType)map.getType().getNextLoweredType().getChild(0);
                if(nullType) {
                    listType = new ListVariableType(mapEntryType);
                    childrenType = mapEntryType;
                    childrenCaster = mapEntryType.getTemplate().caster;
                }
                Variable[] castedChildren = new Variable[map.value.size()];
                Iterator<Variable> keys = map.value.keySet().iterator(), values = map.value.values().iterator();
                MapEntryVariable entry = new MapEntryVariable(mapEntryType);
                for(int i = 0; i < castedChildren.length; ++i) {
                    entry.key = keys.next();
                    entry.value = values.next();
                    castedChildren[i] = childrenCaster.cast(childrenType, entry);
                }
                ListVariable result = new ListVariable(listType);
                result.value.addAll(Arrays.asList(castedChildren));
                return result;
            }
            if(var instanceof StringVariable string && ShortVariable.ShortVariableType.INSTANCE.instanceOf(listType.content)) {
                ListVariable result = new ListVariable(listType);
                String value = string.value;
                result.value.addAll(Collections.nCopies(value.length(), null));
                ShortVariable character = new ShortVariable();
                for(int i = 0; i < value.length(); ++i) {
                    character.setValue((short) value.charAt(i));
                    result.value.set(i, listType.content.getTemplate().caster.cast(listType.content, character));
                }
                return result;
            }
            if(var instanceof IteratorVariable it) {
                if(nullType) {
                    VariableType itContentType = it.getType().getChild(0);
                    if(itContentType == null) {
                        while(it.hasNext()) {
                            if(it.next() != null) {
                                throw VariableManager.CHILD_TYPE_WAS_NULL_BUT_CHILDREN_WERE_PRESENT_EXCEPTION.create();
                            }
                        }
                        return new ListVariable(listType);
                    }
                    childrenCaster = itContentType.getTemplate().caster;
                }
                ListVariable result = new ListVariable(listType);
                while(it.hasNext()) {
                    result.value.add(childrenCaster.cast(listType, it.next()));
                }
                return result;
            }
            if(!(var instanceof ListVariable list)) {
                throw VariableManager.INCOMPATIBLE_TYPES_EXCEPTION.create(type.asString(), var == null ? "null" : var.getType().asString());
            }
            if(nullType) {
                VariableType originalChildType = list.type.content;
                listType = new ListVariableType(originalChildType);
                childrenCaster = originalChildType == null ? null : originalChildType.getTemplate().caster;
            }
            Variable[] castedChildren = new Variable[list.value.size()];
            for(int i = 0; i < castedChildren.length; ++i) {
                Variable value = list.value.get(i);
                if(value != null) {
                    if(childrenCaster == null) {
                        throw VariableManager.CHILD_TYPE_WAS_NULL_BUT_CHILDREN_WERE_PRESENT_EXCEPTION.create();
                    }
                    value = childrenCaster.cast(childrenType, value);
                }
                castedChildren[i] = value;
            }
            ListVariable result = new ListVariable(listType);
            result.value.addAll(Arrays.asList(castedChildren));
            return result;
        }, new VariableCodec() {

            @Override
            public <T> DataResult<T> write(Variable input, DynamicOps<T> ops, T prefix) {
                ListVariable list = (ListVariable) input;
                list.removeEndingNulls();
                return encodeList(list.value, ops, prefix, list.type.content);
            }
            @Override
            public <T> DataResult<Pair<Variable, T>> read(DynamicOps<T> ops, T input, VariableType type) {
                ListVariableType listType = (ListVariableType)type;
                return decodeList(ops, input, listType.content)
                        .map(pair ->
                                pair.mapFirst(list -> {
                                    ListVariable result = new ListVariable(listType);
                                    result.value.addAll(list);
                                    result.removeEndingNulls();
                                    return result;
                                }));
            }
        });

        @Override
        public Variable addVariables(Variable left, Variable right) {
            if(!(left instanceof ListVariable && right instanceof ListVariable)) {
                return null;
            }
            ListVariable result = new ListVariable((ListVariableType)left.getType());
            result.value.addAll(((ListVariable)left).value);
            result.value.addAll(((ListVariable)right).value);
            return result;
        }

        @Override
        public Variable subtractVariables(Variable left, Variable right) {
            if(!(left instanceof ListVariable && right instanceof ListVariable)) {
                return null;
            }
            ListVariable result = new ListVariable((ListVariableType)left.getType());
            result.value.addAll(((ListVariable)left).value);
            result.value.removeAll(((ListVariable)right).value);
            return result;
        }
    }
}
