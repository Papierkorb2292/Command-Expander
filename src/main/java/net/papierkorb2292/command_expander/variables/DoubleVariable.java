package net.papierkorb2292.command_expander.variables;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;
import net.papierkorb2292.command_expander.variables.immediate.operator.NumberOperatorVariableType;

public class DoubleVariable extends CriteriaBindableNumberVariable {

    private double value = 0;

    public DoubleVariable() { }
    public DoubleVariable(double value) {
        this.value = value;
    }

    public void setValue(double value) {
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
        return (float)value;
    }

    @Override
    public double doubleValue() {
        return value;
    }

    @Override
    public String stringValue() {
        return String.valueOf(value) + 'D';
    }

    @Override
    public VariableType getType() {
        return DoubleVariableType.INSTANCE;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DoubleVariable && ((DoubleVariable)o).value == value;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(value);
    }

    @Override
    public NbtElement toNbt() {
        return NbtDouble.of(value);
    }

    public static DoubleVariable parse(String value) {
        return new DoubleVariable(Double.parseDouble(value));
    }

    @Override
    public void add(int value) {
        this.value += value;
    }

    @Override
    public void set(int value) {
        this.value = value;
    }

    public static class DoubleVariableType implements CriteriaBindableNumberVariableType, NumberOperatorVariableType {

        public static final DoubleVariableType INSTANCE = new DoubleVariableType();

        @Override
        public DoubleVariable createVariable() {
            return new DoubleVariable();
        }

        public VariableTypeTemplate getTemplate() {
            return TEMPLATE;
        }

        @Override
        public VariableType getNextLoweredType() {
            return null;
        }

        @Override
        public String getName() {
            return "double";
        }

        public static final VariableTypeTemplate TEMPLATE = new VariableTypeTemplate(
                0, () -> DoubleVariableType.INSTANCE,
                (type, var) -> new DoubleVariable(var.doubleValue()),
                new VariableCodec() {

                    @Override
                    public <T> DataResult<T> write(Variable input, DynamicOps<T> ops, T prefix) {
                        return ops.mergeToPrimitive(prefix, ops.createDouble(input.doubleValue()));
                    }

                    @Override
                    public <T> DataResult<Pair<Variable, T>> read(DynamicOps<T> ops, T input, VariableType type) {
                        return ops.getNumberValue(input).map(value -> Pair.of(new DoubleVariable(value.doubleValue()), ops.empty()));
                    }
                });

        @Override
        public Variable addVariables(Variable left, Variable right) {
            return new DoubleVariable(left.doubleValue() + right.doubleValue());
        }

        @Override
        public Variable multiplyVariables(Variable left, Variable right) {
            return new DoubleVariable(left.doubleValue() * right.doubleValue());
        }

        @Override
        public Variable negateVariable(Variable value) {
            return new DoubleVariable(-value.doubleValue());
        }


        @Override
        public Variable divideVariables(Variable left, Variable right) {
            return new DoubleVariable(left.doubleValue() / right.doubleValue());
        }

        @Override
        public Variable subtractVariables(Variable left, Variable right) {
            return new DoubleVariable(left.doubleValue() - right.doubleValue());
        }
    }
}
