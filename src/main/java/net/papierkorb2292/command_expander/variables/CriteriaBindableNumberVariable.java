package net.papierkorb2292.command_expander.variables;

public abstract class CriteriaBindableNumberVariable extends Variable {

    public abstract void add(int value);
    public abstract void set(int value);

    interface CriteriaBindableNumberVariableType extends VariableType {

        @Override
        CriteriaBindableNumberVariable createVariable();
    }
}
