package net.papierkorb2292.command_expander.variables.immediate.operator;

import net.papierkorb2292.command_expander.variables.Variable;

/**
 * Interface for {@link Variable.VariableType}s where variables of that type can be bitwise ANDed in an immediate value using the '&' operator between two values
 */
public interface BitwiseAndAbleOperatorVariableType {

    Variable andVariables(Variable left, Variable right);
}

