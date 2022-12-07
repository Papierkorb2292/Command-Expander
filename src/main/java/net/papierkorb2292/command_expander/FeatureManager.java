package net.papierkorb2292.command_expander;

import net.fabricmc.fabric.api.gamerule.v1.CustomGameRuleCategory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameRules;

import java.util.HashMap;
import java.util.function.BiConsumer;

public class FeatureManager {

    private final HashMap<String, GameRules.Key<GameRules.BooleanRule>> featureGameRules = new HashMap<>();
    private final CustomGameRuleCategory category;

    public FeatureManager(CustomGameRuleCategory gameRuleCategory) {
        this.category = gameRuleCategory;
    }

    public void addFeature(String name, boolean enabled, BiConsumer<MinecraftServer, GameRules.BooleanRule> changedCallback) {
        if(featureGameRules.containsKey(name)) {
            throw new IllegalArgumentException("Feature of name '" + name + "' already exist");
        }
        featureGameRules.put(name, GameRuleRegistry.register("command_expander:" + name, category, GameRuleFactory.createBooleanRule(enabled, changedCallback)));
    }

    public void addFeature(String name, boolean enabled) {
        addFeature(name, enabled, (server, rule) -> { });
    }

    public boolean isEnabled(MinecraftServer server, String name) {
        GameRules.Key<GameRules.BooleanRule> rule = featureGameRules.get(name);
        if(rule == null) {
            throw new IllegalArgumentException("Feature of name '" + name + "' doesn't exist");
        }
        return server.getGameRules().getBoolean(rule);
    }

    public String[] getFeatures() {
        return featureGameRules.keySet().toArray(String[]::new);
    }
}
