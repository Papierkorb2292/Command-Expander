package net.papierkorb2292.command_expander.variables;

public abstract class IndexableVariable extends Variable {

    public abstract Variable get(Variable indexVar);

    public abstract Variable getOrCreate(Variable indexVar);
}
