package net.papierkorb2292.command_expander.variables;

import java.util.Optional;

/**
 * Used instead of {@link Variable} when allowing null variables in {@link Optional}
 */
public class VariableHolder {

    public Variable variable;

    public VariableHolder(Variable variable) {
        this.variable = variable;
    }

    public Variable getVariable() {
        return variable;
    }
}