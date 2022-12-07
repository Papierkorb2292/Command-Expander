package net.papierkorb2292.command_expander.variables.immediate.operator;

import net.papierkorb2292.command_expander.variables.Variable;

/**
 * Interface for {@link Variable.VariableType}s where variables of that type can be bitwise ORed in an immediate value using the '|' operator between two values
 */
public interface BitwiseOrAbleOperatorVariableType {

    Variable orVariables(Variable left, Variable right);
}
