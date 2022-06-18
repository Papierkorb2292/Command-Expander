package net.papierkorb2292.command_expander.variables;

public abstract class IndexableVariable extends Variable {

    public abstract Variable get(Variable indexVar);

    /**
     * @return whether the index existed already
     */
    public abstract boolean ensureIndexExists(Variable indexVar);
    public abstract void set(Variable indexVar, Variable value);

    protected abstract VariableType getContent();

    public Variable ensureIndexAndGet(Variable indexVar) {
        if (ensureIndexExists(indexVar)) {
            return get(indexVar);
        }
        Variable value = getContent().createVariable();
        set(indexVar, value);
        return value;
    }

    public void ensureIndexAndSet(Variable indexVar, Variable value) {
        ensureIndexExists(indexVar);
        set(indexVar, value);
    }
}
