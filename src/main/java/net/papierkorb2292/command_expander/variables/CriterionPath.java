package net.papierkorb2292.command_expander.variables;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.papierkorb2292.command_expander.CommandExpander;
import net.papierkorb2292.command_expander.mixin.ServerScoreboardAccessor;

import javax.annotation.Nullable;
import java.util.*;

/**
 * A node of a tree, which defines the path to bound variables.<br>
 * The children are stored in a map with a {@link ChildDescriptor} as key to define how to access the value of that child.
 * The {@link #variableToUpdate} field is not null, when the variable at this position is bound. The value of that field
 * is used to set the variable at this position and needs to be updated, when the variable is set to another object.
 * The value of {@link #variableToUpdate} is also added to the {@link CriterionBinding#variables} set of the {@link CriterionBinding} that contains
 * this criterion path.<br>
 * Its nbt form is a compound containing <i>1b</i> for <i>isBound</i> when the variable at this position is bound.
 * The children are represented as a list of their nbt form under <i>paths</i>. The nbt form of their {@link ChildDescriptor} is merged with their nbt.
 */
public class CriterionPath {

    private static final DynamicCommandExceptionType INVALID_VARIABLE_TYPE_EXCEPTION = new DynamicCommandExceptionType(type -> new LiteralText("Invalid variable type for variable binding: " + type.toString()));

    public final Map<ChildDescriptor, CriterionPath> paths = new HashMap<>();
    @Nullable
    public BoundVariable variableToUpdate;

    public static CriterionPath read(NbtCompound compound, Variable value, Set<BoundVariable> variables, ServerScoreboard scoreboard) {
        CriterionPath result = new CriterionPath();
        if(compound.contains("paths", NbtElement.LIST_TYPE)) {
            NbtList paths = compound.getList("paths", NbtElement.COMPOUND_TYPE);
            for(NbtElement path : paths) {
                NbtCompound pathCompound = (NbtCompound) path;
                try {
                    ChildDescriptor childDescriptor = ChildDescriptor.read(pathCompound);
                    Variable child = childDescriptor.type.getChild(value, childDescriptor.value);
                    result.paths.put(childDescriptor, CriterionPath.read(pathCompound, child, variables, scoreboard));
                }
                catch(CommandSyntaxException e) {
                    CommandExpander.LOGGER.error("Error reading or using criterion path child descriptor", e);
                }
            }
        }
        if(compound.contains("isBound", NbtElement.BYTE_TYPE) && compound.getBoolean("isBound")) {
            try {
                result.bind(value, variables, scoreboard);
            } catch (CommandSyntaxException e) {
                CommandExpander.LOGGER.error("Error binding variable after reading the binding: ", e);
            }
        }
        return result;
    }

    public NbtCompound write(NbtCompound nbt) {
        if(variableToUpdate != null) {
            nbt.putBoolean("isBound", true);
        }
        if(!paths.isEmpty()) {
            NbtList paths = new NbtList();
            for(Map.Entry<ChildDescriptor, CriterionPath> entry : this.paths.entrySet()) {
                try {
                    paths.add( //Writing child descriptor and path to the same compound
                            entry.getValue().write(
                                    entry.getKey().write(new NbtCompound())));
                } catch (CommandSyntaxException e) {
                    //An error shouldn't prevent other paths from being written
                    CommandExpander.LOGGER.error("Error writing criterion path", e);
                }
            }
            if(!paths.isEmpty()) {
                nbt.put("paths", paths);
            }
        }
        return nbt;
    }

    /**
     * Binds the criterion path by setting {@link #variableToUpdate} to a {@link BoundVariable} corresponding
     * to the given value. Also adds that {@link BoundVariable} to the set.
     * @param var The value of the variable that this criterion path belongs to
     * @param variables The {@link CriterionBinding#variables} set containing all bound variables of that binding to add the new binding to
     * @param scoreboard The scoreboard instance of the server. Used when creating the {@link BoundVariable}
     * @throws CommandSyntaxException The type isn't bindable.
     * Only types extending {@link CriteriaBindableNumberVariable} or a map with
     * {@link EntityVariable} keys and {@link CriteriaBindableNumberVariable} values can be bound
     */
    public void bind(Variable var, Set<BoundVariable> variables, ServerScoreboard scoreboard) throws CommandSyntaxException {
        if(var instanceof CriteriaBindableNumberVariable number) {
            variableToUpdate = new BoundVariable.BoundNumberVariable(scoreboard, number);
            variables.add(variableToUpdate);
        } else if(var instanceof MapVariable map && map.getType().getChild(0) instanceof EntityVariable.EntityVariableType && map.getType().getChild(1) instanceof CriteriaBindableNumberVariable.CriteriaBindableNumberVariableType) {
            variableToUpdate = new BoundVariable.BoundMapVariable(scoreboard, map);
            variables.add(variableToUpdate);
        } else {
            throw INVALID_VARIABLE_TYPE_EXCEPTION.create(var.getType());
        }
    }

    /**
     * The path is empty when it has no variableToUpdate and no children. In that case
     * the path can be removed
     * @return Whether the path is empty
     */
    public boolean isEmpty() {
        return paths.isEmpty() && variableToUpdate == null;
    }

    /**
     * Interface for classes providing the {@link ScoreboardPlayerScore} used when changing bound variables.
     */
    public interface BoundVariable {

        /**
         * Updates the current variable reference. This method is only called when the variable changes,
         * it isn't called after instantiation in {@link CriterionPath#bind}, because the current value is passed into
         * the constructor
         * @param var The new value of the variable
         * @throws CommandSyntaxException The variable is of the wrong type
         */
        void setVariable(Variable var) throws CommandSyntaxException;
        ScoreboardPlayerScore getScore(String playerName);

        /**
         * {@link BoundVariable} for {@link CriteriaBindableNumberVariable}s
         */
        class BoundNumberVariable extends ScoreboardPlayerScore implements BoundVariable {

            private CriteriaBindableNumberVariable variable;

            public BoundNumberVariable(ServerScoreboard scoreboard, CriteriaBindableNumberVariable variable) {
                super(scoreboard, null, "");
                this.variable = variable;
            }

            @Override
            public void setVariable(Variable var) throws CommandSyntaxException {
                if(!(var instanceof CriteriaBindableNumberVariable number)) {
                    throw INVALID_VARIABLE_TYPE_EXCEPTION.create(var.getType());
                }
                variable = number;
            }

            @Override
            public ScoreboardPlayerScore getScore(String playerName) {
                return this;
            }

            @Override
            public void setScore(int score) {
                variable.set(score);
            }

            @Override
            public void incrementScore(int amount) {
                variable.add(amount);
            }

            @Override
            public int getScore() {
                return variable.intValue();
            }
        }

        /**
         * {@link BoundVariable} for {@link MapVariable}s with {@link EntityVariable} keys and {@link CriteriaBindableNumberVariable} values
         */
        class BoundMapVariable implements BoundVariable {

            private final ServerScoreboard scoreboard;
            private final PlayerManager playerManager;
            private MapVariable variable;
            private final Map<UUID, BoundMapValue> children = new HashMap<>();

            public BoundMapVariable(ServerScoreboard scoreboard, MapVariable variable) {
                this.variable = variable;
                this.playerManager = ((ServerScoreboardAccessor) scoreboard).getServer().getPlayerManager();
                this.scoreboard = scoreboard;
            }

            @Override
            public void setVariable(Variable var) throws CommandSyntaxException {
                if(!(var instanceof MapVariable map)) {
                    throw INVALID_VARIABLE_TYPE_EXCEPTION.create(var.getType());
                }
                this.variable = map;

            }

            @Override
            public ScoreboardPlayerScore getScore(String playerName) {
                ServerPlayerEntity player = playerManager.getPlayer(playerName);
                UUID uuid = player == null ? null : player.getUuid();
                return children.computeIfAbsent(uuid, key -> new BoundMapValue(scoreboard, playerName, key));
            }

            private class BoundMapValue extends ScoreboardPlayerScore {

                private final EntityVariable player;

                public BoundMapValue(Scoreboard scoreboard, String playerName, UUID playerUUID) {
                    super(scoreboard, null, playerName);
                    this.player = new EntityVariable(playerUUID);
                }

                @Override
                public void setScore(int score) {
                    CriteriaBindableNumberVariable var = getVariable();
                    if(var != null) {
                        var.set(score);
                    }
                }

                @Override
                public void incrementScore(int amount) {
                    CriteriaBindableNumberVariable var = getVariable();
                    if(var != null) {
                        var.add(amount);
                    }
                }

                @Override
                public int getScore() {
                    CriteriaBindableNumberVariable var = getVariable();
                    if(var != null) {
                        return var.intValue();
                    }
                    return 0;
                }

                private CriteriaBindableNumberVariable getVariable() {
                    try {
                        return (CriteriaBindableNumberVariable)BoundMapVariable.this.variable.ensureIndexAndGet(player);
                    } catch (CommandSyntaxException e) {
                        CommandExpander.LOGGER.error("Error getting variable when updating map bound to criterion: ", e);
                        return null;
                    }
                }
            }
        }
    }

    /**
     * Defines how a child should be accessed in a criterion path.
     * The nbt form is a compound containing the ordinal of the {@link ChildType} under <i>type</i>
     * and optionally the {@link #value} under <i>value</i>
     */
    public static class ChildDescriptor {

        /**
         * The type of the child
         */
        public final ChildType type;

        /**
         * Optional: The value that is needed to access the child (for example an index)
         */
        @Nullable
        public final Variable value;

        public ChildDescriptor(ChildType type) {
            this(type, null);
        }

        public ChildDescriptor(ChildType type, @Nullable Variable value) {
            this.type = type;
            this.value = value;
        }

        private static final Dynamic2CommandExceptionType VALUE_READ_ERROR = new Dynamic2CommandExceptionType((error, elements) -> new LiteralMessage(String.format("Error reading value of child descriptor: %s with error elements %s", error, elements)));
        private static final DynamicCommandExceptionType VALUE_WRITE_ERROR = new DynamicCommandExceptionType(error -> new LiteralMessage("Error writing value of child descriptor: " + error.toString()));

        public static ChildDescriptor read(NbtCompound pathCompound) throws CommandSyntaxException {
            Variable value = null;
            if(pathCompound.contains("value", NbtElement.COMPOUND_TYPE)) {
                DataResult<Pair<Variable, NbtElement>> valueDataResult = TypedVariable.decode(pathCompound.getCompound("value"), NbtOps.INSTANCE).map(pair -> pair.mapFirst(typed -> typed.var));
                Optional<Pair<Variable, NbtElement>> pair = valueDataResult.promotePartial(VariableManager.dumpError).result();
                if(pair.isPresent()) {
                    value = pair.get().getFirst();
                    if(valueDataResult.error().isPresent()) {
                        throw VALUE_READ_ERROR.create(valueDataResult.error().get(), pair.get().getSecond());
                    }
                } else if(valueDataResult.error().isPresent()) {
                    throw VALUE_READ_ERROR.create(valueDataResult.error().get(), null);
                }
            }
            return new ChildDescriptor(ChildType.values()[pathCompound.getByte("type")], value);
        }

        public NbtCompound write(NbtCompound compound) throws CommandSyntaxException {
            compound.putByte("type", (byte) type.ordinal());
            if(value != null) {
                DataResult<NbtElement> valueDataResult = TypedVariable.encode(value.toTypedVariable(), NbtOps.INSTANCE, NbtOps.INSTANCE.empty());
                Optional<NbtElement> element = valueDataResult.promotePartial(VariableManager.dumpError).result();
                if(element.isPresent()) {
                    compound.put("value", element.get());
                }
                if(valueDataResult.error().isPresent()) {
                    throw VALUE_WRITE_ERROR.create(valueDataResult.error().get());
                }
            }
            return compound;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof ChildDescriptor that)) {
                return false;
            }
            return Objects.equals(this.type, that.type) &&
                    Objects.equals(this.value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(type) ^ Objects.hashCode(value);
        }
    }

    public enum ChildType {

        INDEXED {
            @Override
            public Variable getChild(Variable parent, @Nullable Variable value) throws CommandSyntaxException {
                if(!(parent instanceof IndexableVariable indexable)) {
                    throw UNEXPECTED_VARIABLE_TYPE_EXCEPTION.create(parent.getType());
                }
                return indexable.get(indexable.ensureIndexCompatible(value));
            }
        },
        ENTRY_KEY {
            @Override
            public Variable getChild(Variable parent, @Nullable Variable value) throws CommandSyntaxException {
                if(!(parent instanceof MapEntryVariable entry)) {
                    throw UNEXPECTED_VARIABLE_TYPE_EXCEPTION.create(parent.getType());
                }
                return entry.key;
            }
        },
        ENTRY_VALUE {
            @Override
            public Variable getChild(Variable parent, @Nullable Variable value) throws CommandSyntaxException {
                if(!(parent instanceof MapEntryVariable entry)) {
                    throw UNEXPECTED_VARIABLE_TYPE_EXCEPTION.create(parent.getType());
                }
                return entry.value;
            }
        };

        private static final DynamicCommandExceptionType UNEXPECTED_VARIABLE_TYPE_EXCEPTION = new DynamicCommandExceptionType(variableType -> new LiteralMessage(String.format("Unexpected variable type %s when getting child for criteria binding", variableType)));

        public abstract Variable getChild(Variable parent, @Nullable Variable value) throws CommandSyntaxException;
    }
}
