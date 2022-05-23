package net.papierkorb2292.command_expander.variables;


import com.mojang.brigadier.exceptions.CommandSyntaxException;

import javax.annotation.Nullable;

public class TypedVariable {

    public Variable.VariableType type;
    public @Nullable Variable var;

    public TypedVariable(Variable.VariableType type, @Nullable Variable var) {
        this.type = type;
        this.var = var;
    }

    public void castAndSet(@Nullable Variable var) throws CommandSyntaxException {
        this.var = VariableManager.castVariable(type, var);
    }
}
