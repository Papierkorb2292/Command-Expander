package net.papierkorb2292.command_expander.variables;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Texts;
import net.papierkorb2292.command_expander.CommandExpander;
import net.papierkorb2292.command_expander.variables.path.VariablePath;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.ToIntBiFunction;

/**
 * The value of {@link VariableManager.BoundCriteriaPersistentState}.
 * It contains the {@link CriterionPath} to all bound variables and a set of all bound
 * variables, so they can be updated without traversing all paths.
 * Its nbt form saves {@link #paths} as a compound with the tags representing the variable ids and
 * the nbt form of the corresponding {@link CriterionPath} as values
 */
public class CriterionBinding {

    public final Map<VariableIdentifier, CriterionPath> paths = new HashMap<>();

    /**
     * The set of all bound variables to update with the criterion.
     * Contains the same references as the {@link CriterionPath#variableToUpdate}
     * contained in {@link #paths}
     */
    public final Set<CriterionPath.BoundVariable> variables = new HashSet<>();

    public static CriterionBinding read(VariableManager manager, String criterion, NbtCompound data, ServerScoreboard scoreboard) {
        CriterionBinding result = new CriterionBinding();
        for(String key : data.getKeys()) {
            try {
                VariableIdentifier id = new VariableIdentifier(key);
                TypedVariable typed = manager.get(id);
                result.paths.put(id, CriterionPath.read(data.getCompound(key), typed.var, result.variables, scoreboard));
            } catch (CommandSyntaxException e) {
                CommandExpander.LOGGER.error("Error reading criterion binding for id {} with criterion {}", key, criterion, e);
            }
        }
        return result;
    }

    public NbtElement write(NbtCompound nbt) {
        for(Map.Entry<VariableIdentifier, CriterionPath> entry : paths.entrySet()) {
            nbt.put(entry.getKey().toString(), entry.getValue().write(new NbtCompound()));
        }
        return nbt;
    }

    /**
     * Binds all variables the path points to
     * @param path The path to the variables to bind
     * @param manager The manager to get the {@link TypedVariable} and {@link ServerScoreboard}
     * @param cc The context for evaluating the path and sending errors to
     * @return The amount of variables that were bound
     * @throws CommandSyntaxException An error happened when evaluating the first accessor of the path
     */
    public int bind(VariablePath path, VariableManager manager, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
        CriterionPath criterionPath = paths.computeIfAbsent(path.getBase(), id -> new CriterionPath());
        TypedVariable typed = manager.get(path.getBase());
        return followChildrenPath(criterionPath, path, 0, typed.var, cc, true, (endCriterionPath, value) -> {
            if(endCriterionPath.variableToUpdate != null || value == null) {
                return 0;
            }
            try {
                endCriterionPath.bind(value, variables, manager.scoreboard);
            } catch (CommandSyntaxException e) {
                cc.getSource().sendError(Texts.toText(e.getRawMessage()));
            }
            return endCriterionPath.variableToUpdate == null ? 0 : 1;
        });
    }


    /**
     * Unbinds all variables the path points to. Doesn't load the path when it contains to accessor,
     * because the id can be directly removed from {@link #paths}
     * @param path The path to the variables to unbind
     * @param manager The manager to get the {@link TypedVariable}
     * @param cc The context for evaluating the path and sending errors to
     * @return The amount of variables that were bound
     * @throws CommandSyntaxException An error happened when evaluating the first accessor of the path
     */
    public int unbind(VariablePath path, VariableManager manager, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
        VariableIdentifier id = path.getBase();
        CriterionPath criterionPath = paths.get(id);
        if(criterionPath == null) {
            return 0;
        }
        TypedVariable typed = manager.get(id);
        int result = followChildrenPath(criterionPath, path, 0, typed.var, cc, false, (endCriterionPath, value) -> {
            if(endCriterionPath.variableToUpdate != null) {
                variables.remove(endCriterionPath.variableToUpdate);
                endCriterionPath.variableToUpdate = null;
                return 1;
            }
            return 0;
        });
        if(criterionPath.paths.size() == 0 && criterionPath.variableToUpdate == null) {
            paths.remove(id);
        }
        return result;
    }

    /**
     * Updates the references of {@link CriterionPath#variableToUpdate} and {@link #variables} after
     * "/var set" was used on the given path. Also updates all children of the set variables
     * @param path The path to the variables to update
     * @param manager The manager to get the {@link TypedVariable}
     * @param cc The context for evaluating the path and sending errors to
     * @throws CommandSyntaxException An error happened when evaluating the first accessor of the path
     */
    public void updateReferences(VariablePath path, VariableManager manager, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
        VariableIdentifier id = path.getBase();
        CriterionPath criterionPath = paths.get(id);
        TypedVariable typed = manager.get(id);
        followChildrenPath(criterionPath, path, 0, typed.var, cc, false, (endCriterionPath, value) -> {
            forRootAndAllChildren(endCriterionPath, value, (childCriterionPath, childValue) -> {
                if(childCriterionPath.variableToUpdate != null) {
                    if(childValue == null) {
                        variables.remove(childCriterionPath.variableToUpdate);
                        childCriterionPath.variableToUpdate = null;
                        return;
                    }
                    try {
                        childCriterionPath.variableToUpdate.setVariable(childValue);
                    } catch (CommandSyntaxException e) {
                        cc.getSource().sendError(Texts.toText(e.getRawMessage()));
                        variables.remove(childCriterionPath.variableToUpdate);
                        childCriterionPath.variableToUpdate = null;
                    }
                }
            }, cc);
            return 0;
        });
    }

    /**
     * Removes bindings for all variables the path points to and for all descendants of these variables.
     * Doesn't load the variable when the path contains no accessors, because the value is only needed for
     * {@link net.papierkorb2292.command_expander.variables.path.PathChildrenAccessor#getChildDescriptors}
     * @param path The path pointing to the variables to unbind
     * @param manager The manager to get the {@link TypedVariable}
     * @param cc The context for evaluating the path and sending errors to
     * @throws CommandSyntaxException An error happened when evaluating the first accessor of the path
     */
    public void removeReferences(VariablePath path, VariableManager manager, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
        VariableIdentifier id = path.getBase();
        CriterionPath criterionPath = paths.get(id);
        if(path.getAccessorLength() == 0) {
            paths.remove(id);
            forRootAndAllChildren(criterionPath, (childCriterionPath) -> {
                if (childCriterionPath.variableToUpdate == null) {
                    return;
                }
                variables.remove(childCriterionPath.variableToUpdate);
            });
            return;
        }
        if(criterionPath == null) {
            return;
        }
        TypedVariable typed = manager.get(id);
        followChildrenPath(criterionPath, path, 0, typed.var, cc, false, (endCriterionPath, value) -> {
            forRootAndAllChildren(endCriterionPath, childCriterionPath -> {
                if(childCriterionPath.variableToUpdate != null) {
                    variables.remove(childCriterionPath.variableToUpdate);
                    childCriterionPath.variableToUpdate = null;
                }
            });
            return 0;
        });
        if(criterionPath.isEmpty()) {
            paths.remove(id);
        }
    }

    /**
     * Recursively follows the {@link VariablePath} and calls the callback for each {@link CriterionPath} at the end of the path.
     * The callback is called regardless of whether the value is null. Sums the results of all callback invocations
     * @param criterionPath The current criterion path either containing the children to follow next
     *                      or the criterion path at the end of the path, when {@code path.getAccessorLength() == accessorIndex}.
     *                      It's also considered as the end when {@code path.getAccessorLength() < accessorIndex}, so
     *                      getting the accessor would throw an {@link IndexOutOfBoundsException}
     * @param path The path to follow
     * @param accessorIndex The current index in the accessors of the path. Increased through recursion and usually starts at zero
     * @param value The value of the variable at the current position
     * @param context The context for evaluating the path and sending errors thrown when accessing the children to
     * @param createBranches Whether to create new {@link CriterionPath}s when no {@link CriterionPath} exists for the children the path points to
     * @param endCallback The callback to call on each value at the end of the path. The returned value of all callbacks is added to the result
     * @return The result of the callback when at the end of the path, otherwise the sum of the results obtained by following the children
     * @throws CommandSyntaxException An error happened when evaluating the next accessor of the path
     */
    private int followChildrenPath(CriterionPath criterionPath, VariablePath path, int accessorIndex, Variable value, CommandContext<ServerCommandSource> context, boolean createBranches, ToIntBiFunction<CriterionPath, Variable> endCallback) throws CommandSyntaxException {
        if(path.getAccessorLength() <= accessorIndex) {
            return endCallback.applyAsInt(criterionPath, value);
        }
        if(value == null) {
            return 0;
        }
        List<CriterionPath.ChildDescriptor> children = path.getAccessor(accessorIndex).getChildDescriptors(value, context);
        int addedBindings = 0;
        for(CriterionPath.ChildDescriptor child : children) {
            CriterionPath childCriterionPath;
            if(createBranches) {
                childCriterionPath = criterionPath.paths.computeIfAbsent(child, id -> new CriterionPath());
            } else {
                childCriterionPath = criterionPath.paths.get(child);
                if(childCriterionPath == null) {
                    return 0;
                }
            }
            try {
                Variable childValue = child.type.getChild(value, child.value);
                addedBindings += followChildrenPath(childCriterionPath, path, accessorIndex + 1, childValue, context, createBranches, endCallback);
            } catch(CommandSyntaxException e) {
                context.getSource().sendError(Texts.toText(e.getRawMessage()));
            }
            if(childCriterionPath.isEmpty()) {
                criterionPath.paths.remove(child);
            }
        }
        return addedBindings;
    }

    /**
     * Recursively calls the callback for the given criterion path and all present descendants of it. Also provides the
     * corresponding value of that criterion path using the child descriptors. If those aren't needed,
     * {@link #forRootAndAllChildren(CriterionPath, Consumer)} should be used instead
     * @param criterionPath The criterion path to start at
     * @param value The value of the given criterion path
     * @param callback The callback to call for the given criterion path and all descendants with their value
     * @param cc The context to send errors thrown when traversing the children to
     * @see #forRootAndAllChildren(CriterionPath, Consumer)
     */
    private void forRootAndAllChildren(CriterionPath criterionPath, Variable value, BiConsumer<CriterionPath, Variable> callback, CommandContext<ServerCommandSource> cc) {
        callback.accept(criterionPath, value);
        for(Map.Entry<CriterionPath.ChildDescriptor, CriterionPath> child : criterionPath.paths.entrySet()) {
            try {
                Variable childValue = child.getKey().type.getChild(value, child.getKey().value);
                forRootAndAllChildren(child.getValue(), childValue, callback, cc);
            } catch(CommandSyntaxException e) {
                cc.getSource().sendError(Texts.toText(e.getRawMessage()));
            }
            if(child.getValue().isEmpty()) {
                criterionPath.paths.remove(child.getKey());
            }
        }
    }

    /**
     * Recursively calls the callback for the given criterion path and all present descendants of it. Does NOT provide
     * the corresponding value of that criterion path. If the values are needed,
     * {@link #forRootAndAllChildren(CriterionPath, Variable, BiConsumer, CommandContext)} should be used instead.
     * @param criterionPath The criterion path to start at
     * @param callback The callback to call for the given criterion path and all descendants
     * @see #forRootAndAllChildren(CriterionPath, Variable, BiConsumer, CommandContext)
     */
    private void forRootAndAllChildren(CriterionPath criterionPath, Consumer<CriterionPath> callback) {
        callback.accept(criterionPath);
        for(Map.Entry<CriterionPath.ChildDescriptor, CriterionPath> child : criterionPath.paths.entrySet()) {
            forRootAndAllChildren(child.getValue(), callback);
            if(child.getValue().isEmpty()) {
                criterionPath.paths.remove(child.getKey());
            }
        }
    }
}
