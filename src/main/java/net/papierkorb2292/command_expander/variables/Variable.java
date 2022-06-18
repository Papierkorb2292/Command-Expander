package net.papierkorb2292.command_expander.variables;

public abstract class Variable {

    public abstract int intValue();

    public abstract long longValue();

    public abstract float floatValue();

    public abstract double doubleValue();

    public abstract String stringValue();

    public byte byteValue() {
        return (byte)intValue();
    }

    public short shortValue() {
        return (short)intValue();
    }

    public abstract VariableType getType();

    @Override
    public abstract int hashCode();

    public interface VariableType {

        Variable createVariable();
        VariableTypeTemplate getTemplate();
        default void setChild(int index, VariableType child) { }
        default VariableType getChild(int index) {
            return null;
        }
    }
}
