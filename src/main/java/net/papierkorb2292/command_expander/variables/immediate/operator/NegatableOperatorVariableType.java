package net.papierkorb2292.command_expander.variables.immediate.operator;

import net.papierkorb2292.command_expander.variables.Variable;

/**
 * Interface for {@link Variable.VariableType}s where variables of that type can be negated in an immediate value using the '-' operator before the value
 */
public interface NegatableOperatorVariableType {

    Variable negateVariable(Variable value);
}
