package net.papierkorb2292.command_expander.variables.immediate;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.entity.Entity;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.provider.number.LootNumberProvider;
import net.minecraft.loot.provider.number.LootNumberProviderType;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.JsonSerializer;
import net.minecraft.util.math.Vec2f;
import net.papierkorb2292.command_expander.variables.Variable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.stream.DoubleStream;

public class VariableLootNumberProvider implements LootNumberProvider {

    public static final Map<String, Reducer> REDUCERS;

    private static LootNumberProviderType type;

    private final String rawValue;
    private final ImmediateValue value;
    private final LootContext.EntityTarget entity;
    private final Reducer reducer;


    public VariableLootNumberProvider(String rawValue, ImmediateValue value, LootContext.EntityTarget entity, Reducer reducer) {
        this.rawValue = rawValue;
        this.value = value;
        this.entity = entity;
        this.reducer = reducer;
    }

    public VariableLootNumberProvider(String rawValue, LootContext.EntityTarget entity, Reducer reducer) {
        this.rawValue = rawValue;
        this.entity = entity;
        this.reducer = reducer;
        ImmediateValue value;
        try {
            value = ImmediateValueCompiler.compile(new StringReader(rawValue));
        } catch (CommandSyntaxException e) {
            value = null;
        }
        this.value = value;
    }

    @Override
    public float nextFloat(LootContext context) {
        Entity target = context.get(entity.getParameter());
        ServerCommandSource source = new ServerCommandSource(CommandOutput.DUMMY, context.get(LootContextParameters.ORIGIN), Vec2f.ZERO, context.getWorld(), 4, target == null ? null : target.getEntityName(), target == null ? null : target.getDisplayName(), context.getWorld().getServer(), target);
        CommandContext<ServerCommandSource> cc = new CommandContext<>(source, null, null, null, null, null, null, null, null, false);
        try {
            return value.calculate(cc).map(
                    holder -> holder.variable.floatValue(),
                    stream -> (float)reducer.reduce(stream.mapToDouble(Variable::doubleValue), context.getRandom())
            );
        } catch (CommandSyntaxException e) {
            return 0;
        }
    }

    @Override
    public LootNumberProviderType getType() {
        return type;
    }

    public static void register(BiFunction<String, JsonSerializer<? extends LootNumberProvider>, LootNumberProviderType> callback) {
        type = callback.apply("command_expander:variable", new Serializer());
    }

    private static class Serializer implements JsonSerializer<VariableLootNumberProvider> {
        @Override
        public void toJson(JsonObject json, VariableLootNumberProvider object, JsonSerializationContext context) {
            json.addProperty("variable", object.rawValue);
            json.add("target", context.serialize(object.entity));
            for(Map.Entry<String, Reducer> entry : REDUCERS.entrySet()) {
                if(entry.getValue() == object.reducer) {
                    json.addProperty("reducer", entry.getKey());
                    break;
                }
            }
        }

        @Override
        public VariableLootNumberProvider fromJson(JsonObject json, JsonDeserializationContext context) {
            String value = JsonHelper.getString(json, "variable");
            LootContext.EntityTarget target = JsonHelper.deserialize(json, "target", context, LootContext.EntityTarget.class);
            String rawReducer = json.has("reducer") ? JsonHelper.getString(json, "reducer") : "sum";
            Reducer reducer = REDUCERS.get(rawReducer);
            if(reducer == null) {
                throw new JsonSyntaxException("Unknown variable number provider reducer: " + rawReducer);
            }
            return new VariableLootNumberProvider(value, target, reducer);
        }
    }

    static {
        REDUCERS = new HashMap<>();
        REDUCERS.put("sum", (values, rnd) ->
                values.reduce(0, Double::sum));
        REDUCERS.put("avg", (values, rnd) -> {
            double[] ar = values.toArray();
            double sum = 0;
            for(double s : ar) {
                sum += s;
            }
            return sum / ar.length;
        });
        REDUCERS.put("rnd", (values, rnd) -> {
            double[] ar = values.toArray();
            return ar[rnd.nextInt(ar.length)];
        });
    }

    @FunctionalInterface
    interface Reducer {

        double reduce(DoubleStream values, Random rnd);
    }
}
