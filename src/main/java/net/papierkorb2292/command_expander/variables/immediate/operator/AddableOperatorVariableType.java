package net.papierkorb2292.command_expander.variables.immediate.operator;

import net.papierkorb2292.command_expander.variables.Variable;

/**
 * Interface for {@link Variable.VariableType}s where variables of that type can be added in an immediate value using the '+' operator between two values
 */
public interface AddableOperatorVariableType {

    Variable addVariables(Variable left, Variable right);
}
