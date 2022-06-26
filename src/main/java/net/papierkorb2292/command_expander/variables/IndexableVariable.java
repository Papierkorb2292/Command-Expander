package net.papierkorb2292.command_expander.variables;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.stream.Stream;

public abstract class IndexableVariable extends Variable {

    public abstract Variable get(Variable indexVar);

    /**
     * @return whether the index existed already
     */
    public abstract boolean ensureIndexExists(Variable indexVar);
    public abstract void set(Variable indexVar, Variable value);
    public abstract void remove(Variable indexVar);
    public abstract Variable ensureIndexCompatible(Variable indexVar) throws CommandSyntaxException;

    public abstract VariableType getContentType();
    public abstract Stream<Variable> getContents();

    public Variable ensureIndexAndGet(Variable indexVar) {
        if (ensureIndexExists(indexVar)) {
            return get(indexVar);
        }
        Variable value = getContentType().createVariable();
        set(indexVar, value);
        return value;
    }

    public void ensureIndexAndSet(Variable indexVar, Variable value) {
        ensureIndexExists(indexVar);
        set(indexVar, value);
    }
}
