package net.papierkorb2292.command_expander.variables;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ListVariable extends IndexableVariable {

    final ListVariableType type;
    final List<Variable> value = new ArrayList<>();

    public ListVariable(ListVariableType type) {
        this.type = type;
    }

    @Override
    public int intValue() {
        return value.size();
    }

    @Override
    public long longValue() {
        return value.size();
    }

    @Override
    public float floatValue() {
        return value.size();
    }

    @Override
    public double doubleValue() {
        return value.size();
    }

    @Override
    public String stringValue() {
        StringBuilder sb = new StringBuilder();
        sb.append("[ ");
        if(value.size() > 0) {
            sb.append(value.get(0).stringValue());
            for(int i = 1; i < value.size(); ++i) {
                sb.append(", ");
                Variable var = value.get(i);
                sb.append(var == null ? "null" : value.get(i).stringValue());
            }
        }
        sb.append(" ]");
        return sb.toString();
    }

    @Override
    public VariableType getType() {
        return type;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
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
    public void set(Variable indexVar, Variable value) {
        int index = indexVar.intValue();
        if(index >= 0 && index < this.value.size()) {
            this.value.set(index, value);
        }
    }

    @Override
    protected VariableType getContent() {
        return type.content;
    }

    public static class ListVariableType implements VariableType {
        VariableType content;

        @Override
        public Variable createVariable() {
            return new ListVariable(this);
        }

        @Override
        public VariableTypeTemplate getTemplate() {
            return template;
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

        public static final VariableTypeTemplate template = new VariableTypeTemplate(1, ListVariable.ListVariableType::new, (type, var) -> {
            if(!(var instanceof ListVariable list)) {
                throw VariableManager.INCOMPATIBLE_TYPES_EXCEPTION.create("ListVariable", var.getClass().getName());
            }
            ListVariable.ListVariableType listType = (ListVariable.ListVariableType)type;
            Variable.VariableType childrenType = listType.content;
            VariableManager.Caster childrenCaster = childrenType.getTemplate().caster;
            Variable[] castedChildren = new Variable[list.value.size()];
            for(int i = 0; i < castedChildren.length; ++i) {
                castedChildren[i] = childrenCaster.cast(childrenType, list.value.get(i));
            }
            ListVariable result = new ListVariable(listType);
            result.value.addAll(Arrays.asList(castedChildren));
            return result;
        }, new VariableCodec() {

            @Override
            public <T> DataResult<T> write(Variable input, DynamicOps<T> ops, T prefix) {
                //ListVariable list = (ListVariable)input;
                //VariableCodec contentCodec = TYPES_BY_CLASS.get(list.type.content.getClass()).codec;
                //ListBuilder<T> builder = ops.listBuilder();
                //StringBuilder errorBuilder = new StringBuilder();
                //T empty = ops.emptyMap();
                //for(int i = 0; i < list.value.size(); ++i) {
                //    Either<T, DataResult.PartialResult<T>> result = contentCodec.encode(list.value.get(i), ops, empty)
                //            .promotePartial(error -> { }).get();
                //    builder.add(result.left().isPresent() ? result.left().get() : empty);
                //    if(result.right().isPresent()) {
                //        errorBuilder.append(result.right().get().message()).append(" at index: ").append(i).append("; ");
                //    }
                //}
                //String error = errorBuilder.append(")").toString();
                //DataResult<T> result = builder.build(prefix);
                //return error.isEmpty() ? result : result.flatMap(value -> DataResult.error("(" + error, value));
                ListVariable list = (ListVariable) input;
                return encodeList(list.value, ops, prefix, list.type.content);
            }
            @Override
            public <T> DataResult<Pair<Variable, T>> read(DynamicOps<T> ops, T input, Variable.VariableType type) {
                ListVariable.ListVariableType listType = (ListVariable.ListVariableType)type;
                return decodeList(ops, input, listType.content)
                        .map(pair ->
                                pair.mapFirst(list -> {
                                    ListVariable result = new ListVariable(listType);
                                    result.value.addAll(list);
                                    return result;
                                }));
            }
        });
    }
}
