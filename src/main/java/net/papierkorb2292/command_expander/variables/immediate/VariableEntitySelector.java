package net.papierkorb2292.command_expander.variables.immediate;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Either;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.EntitySelectorReader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.predicate.NumberRange;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Texts;
import net.papierkorb2292.command_expander.variables.EntityVariable;
import net.papierkorb2292.command_expander.variables.Variable;
import net.papierkorb2292.command_expander.variables.VariableHolder;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public class VariableEntitySelector extends EntitySelector {

    private final ImmediateValue value;

    public VariableEntitySelector(ImmediateValue value) {
        super(0, true, false, entity -> true, NumberRange.FloatRange.ANY, pos -> pos, null, EntitySelectorReader.ARBITRARY, false, null, null, null, true);
        this.value = value;
    }

    private Either<VariableHolder, Stream<Variable>> computeValue(ServerCommandSource source) throws CommandSyntaxException {
        return value.calculate(new CommandContext<>(source, null, null, null, null, null, null, null, null, false));
    }

    public static final SimpleCommandExceptionType VALUE_NOT_ENTITY_EXCEPTION = new SimpleCommandExceptionType(new LiteralMessage("Value is not an entity"));

    @Override
    public Entity getEntity(ServerCommandSource source) throws CommandSyntaxException {
        Either<VariableHolder, Stream<Variable>> value = computeValue(source);
        if(value.left().isEmpty()) {
            throw EntityArgumentType.TOO_MANY_ENTITIES_EXCEPTION.create();
        }
        Variable var = value.left().get().variable;
        if(var == null) {
            throw EntityArgumentType.ENTITY_NOT_FOUND_EXCEPTION.create();
        }
        if(!(var instanceof EntityVariable entity)) {
            throw VALUE_NOT_ENTITY_EXCEPTION.create();
        }
        for(ServerWorld world : source.getServer().getWorlds()) {
            Entity result = world.getEntity(entity.uuid);
            if(result != null) {
                return result;
            }
        }
        throw EntityArgumentType.ENTITY_NOT_FOUND_EXCEPTION.create();
    }

    @Override
    public List<? extends Entity> getEntities(ServerCommandSource source) throws CommandSyntaxException {
        Either<VariableHolder, Stream<Variable>> value = computeValue(source);
        if(value.left().isPresent()) {
            Variable var = value.left().get().variable;
            if(var == null) {
                throw EntityArgumentType.ENTITY_NOT_FOUND_EXCEPTION.create();
            }
            if(!(var instanceof EntityVariable entity)) {
                throw VALUE_NOT_ENTITY_EXCEPTION.create();
            }
            for(ServerWorld world : source.getServer().getWorlds()) {
                Entity result = world.getEntity(entity.uuid);
                if(result != null) {
                    return List.of(result);
                }
            }
            throw EntityArgumentType.ENTITY_NOT_FOUND_EXCEPTION.create();
        }
        if(value.right().isEmpty()) {
            throw new IllegalStateException("Invalid Either passed as entity value. Neither left nor right were present");
        }
        return value.right().get().flatMap(
                var -> {
                    try {
                        if (var == null) {
                            throw EntityArgumentType.ENTITY_NOT_FOUND_EXCEPTION.create();
                        }
                        if (!(var instanceof EntityVariable entity)) {
                            throw VALUE_NOT_ENTITY_EXCEPTION.create();
                        }
                        for (ServerWorld world : source.getServer().getWorlds()) {
                            Entity result = world.getEntity(entity.uuid);
                            if (result != null) {
                                return Stream.of(result);
                            }
                        }
                    } catch(CommandSyntaxException e) {
                        source.sendError(Texts.toText(e.getRawMessage()));
                    }
                    return Stream.of();
                }
        ).toList();
    }

    @Override
    public ServerPlayerEntity getPlayer(ServerCommandSource source) throws CommandSyntaxException {
        Entity entity = getEntity(source);
        if(entity instanceof ServerPlayerEntity player) {
            return player;
        }
        throw EntityArgumentType.PLAYER_NOT_FOUND_EXCEPTION.create();
    }

    @Override
    public List<ServerPlayerEntity> getPlayers(ServerCommandSource source) throws CommandSyntaxException {
        return getEntities(source).stream()
                .filter(entity -> entity instanceof ServerPlayerEntity)
                .map(entity -> (ServerPlayerEntity) entity)
                .toList();
    }
}
