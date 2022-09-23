package net.papierkorb2292.command_expander.variables;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.papierkorb2292.command_expander.variables.immediate.operator.NumberOperatorVariableType;

public class FloatVariable extends CriteriaBindableNumberVariable {

    private float value = 0;

    public FloatVariable() { }
    public FloatVariable(float value) {
        this.value = value;
    }

    public void setValue(float value) {
        this.value = value;
    }

    @Override
    public int intValue() {
        return (int)value;
    }

    @Override
    public long longValue() {
        return (long)value;
    }

    @Override
    public float floatValue() {
        return value;
    }

    @Override
    public double doubleValue() {
        return value;
    }

    @Override
    public String stringValue() {
        return String.valueOf(value) + 'F';
    }

    @Override
    public Variable.VariableType getType() {
        return FloatVariableType.INSTANCE;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FloatVariable && ((FloatVariable)o).value == value;
    }

    @Override
    public int hashCode() {
        return Float.hashCode(value);
    }

    public static FloatVariable parse(String value) {
        return new FloatVariable(Float.parseFloat(value));
    }

    @Override
    public void add(int value) {
        this.value += value;
    }

    @Override
    public void set(int value) {
        this.value = value;
    }

    public static class FloatVariableType implements CriteriaBindableNumberVariableType, NumberOperatorVariableType {

        public static final FloatVariableType INSTANCE = new FloatVariableType();

        @Override
        public FloatVariable createVariable() {
            return new FloatVariable();
        }

        public VariableTypeTemplate getTemplate() {
            return TEMPLATE;
        }

        @Override
        public VariableType getNextLoweredType() {
            return DoubleVariable.DoubleVariableType.INSTANCE;
        }

        @Override
        public String getName() {
            return "float";
        }

        public static final VariableTypeTemplate TEMPLATE = new VariableTypeTemplate(
                0, () -> INSTANCE,
                (type, var) -> new FloatVariable(var.floatValue()),
                new VariableCodec() {

                    @Override
                    public <T> DataResult<T> write(Variable input, DynamicOps<T> ops, T prefix) {
                        return ops.mergeToPrimitive(prefix, ops.createFloat(input.floatValue()));
                    }

                    @Override
                    public <T> DataResult<Pair<Variable, T>> read(DynamicOps<T> ops, T input, Variable.VariableType type) {
                        return ops.getNumberValue(input).map(value -> Pair.of(new FloatVariable(value.floatValue()), ops.empty()));
                    }
                });

        @Override
        public Variable addVariables(Variable left, Variable right) {
            return new FloatVariable(left.floatValue() + right.floatValue());
        }

        @Override
        public Variable multiplyVariables(Variable left, Variable right) {
            return new FloatVariable(left.floatValue() * right.floatValue());
        }

        @Override
        public Variable negateVariable(Variable value) {
            return new FloatVariable(-value.floatValue());
        }

        @Override
        public Variable divideVariables(Variable left, Variable right) {
            return new FloatVariable(left.floatValue() / right.floatValue());
        }

        @Override
        public Variable subtractVariables(Variable left, Variable right) {
            return new FloatVariable(left.floatValue() - right.floatValue());
        }
    }
}
