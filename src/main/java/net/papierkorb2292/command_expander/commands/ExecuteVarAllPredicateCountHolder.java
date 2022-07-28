package net.papierkorb2292.command_expander.commands;

public record ExecuteVarAllPredicateCountHolder(int count, int filteredCount) {

    public static final ExecuteVarAllPredicateCountHolder IDENTITY = new ExecuteVarAllPredicateCountHolder(0, 0);

    public ExecuteVarAllPredicateCountHolder add(ExecuteVarAllPredicateCountHolder other) {
        return new ExecuteVarAllPredicateCountHolder(count + other.count, filteredCount + other.filteredCount);
    }
}
