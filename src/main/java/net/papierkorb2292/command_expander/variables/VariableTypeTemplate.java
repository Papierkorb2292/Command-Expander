package net.papierkorb2292.command_expander.variables;

import java.util.function.Supplier;

public class VariableTypeTemplate {
    public final int childrenCount;
    byte id;
    public final Supplier<Variable.VariableType> typeFactory;
    public final VariableManager.Caster caster;
    public final VariableCodec codec;

    VariableTypeTemplate(int childrenCount, Supplier<Variable.VariableType> typeFactory, VariableManager.Caster caster, VariableCodec codec) {
        this.childrenCount = childrenCount;
        this.typeFactory = typeFactory;
        this.caster = (type, var) -> var == null ? null : caster.cast(type, var);
        this.codec = codec;
    }

    public int getId() {
        return id;
    }
}
