package net.papierkorb2292.command_expander.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.ScoreboardCriterionArgumentType;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.stat.Stat;
import net.minecraft.stat.StatType;
import net.minecraft.util.registry.Registry;
import net.papierkorb2292.command_expander.variables.VariableManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * An argument type for scoreboard criterion, like {@link ScoreboardCriterionArgumentType}, but
 * this allows for the wildcard '*' and (TODO) tags '#' to be used in the criterion identifier, so multiple criterias can be specified. It
 * uses strings instead of {@link ScoreboardCriterion}, because {@link VariableManager.BoundCriteriaPersistentState} also uses strings.
 */
public class MultiScoreboardCriterionArgumentType implements ArgumentType<List<String>> {

    private static final Collection<String> EXAMPLES = Arrays.asList("dummy", "trigger", "minecraft.used:minecraft.dirt", "*", "minecraft.used:*", "killedByTeam.*blue");

    public static MultiScoreboardCriterionArgumentType scoreboardCriterion() {
        return new MultiScoreboardCriterionArgumentType();
    }

    public static List<String> getScoreboardCriteria(CommandContext<ServerCommandSource> context, String criteria) {
        //noinspection unchecked
        return context.getArgument(criteria, List.class);
    }

    @Override
    public List<String> parse(StringReader reader) throws CommandSyntaxException {
        int startCursor = reader.getCursor();
        List<String> starSeparatedParts = new ArrayList<>();
        int currentCursor = startCursor;
        boolean startsWithStar = reader.canRead() && reader.peek() == '*';
        while(reader.canRead() && reader.peek() != ' ') {
            if(reader.peek() == '*') {
                starSeparatedParts.add(reader.getString().substring(currentCursor, reader.getCursor()));
                reader.skip();
                // Consecutive stars can be treated as a single star
                while (reader.canRead() && reader.peek() == '*') {
                    reader.skip();
                }
                currentCursor = reader.getCursor();
                continue;
            }
            reader.skip();
        }
        boolean endsWithStar = currentCursor == reader.getCursor();
        if(!endsWithStar) {
            starSeparatedParts.add(reader.getString().substring(currentCursor, reader.getCursor()));
        }
        if(!(startsWithStar || endsWithStar) && starSeparatedParts.size() == 1) {
            return List.of(ScoreboardCriterion.getOrCreateStatCriterion(starSeparatedParts.get(0)).orElseThrow(() -> {
                reader.setCursor(startCursor);
                return ScoreboardCriterionArgumentType.INVALID_CRITERION_EXCEPTION.create(starSeparatedParts.get(0));
            }).getName()); // Check whether the criterion exists
        }
        if(starSeparatedParts.size() == 0) {
            if(startsWithStar) {
                return getCriteria();
            }
            reader.setCursor(startCursor);
            throw ScoreboardCriterionArgumentType.INVALID_CRITERION_EXCEPTION.create("");
        }
        //TODO: Add optimized wildcard matching (Toggleable with gamerule)
        List<String> criteria = getCriteria();
        Iterator<String> criteriaIt = criteria.iterator();
        while(criteriaIt.hasNext()) {
            String criterion = criteriaIt.next();
            if(startsWithStar) {
                if(!matchesWildcardSimple(criterion, 0, starSeparatedParts, 0, endsWithStar)) {
                    criteriaIt.remove();
                }
                continue;
            }
            if(criterion.startsWith(starSeparatedParts.get(0)) && matchesWildcardSimple(criterion, starSeparatedParts.get(0).length(), starSeparatedParts, 1, endsWithStar)) {
                continue;
            }
            criteriaIt.remove();
        }
        return criteria;
    }

    public static List<String> getCriteria() {
        List<String> result = Lists.newArrayList(ScoreboardCriterion.getAllSimpleCriteria());
        for (StatType<?> statType : Registry.STAT_TYPE) {
            addStats(statType, result);
        }
        return result;
    }

    private static <T> void addStats(StatType<T> type, List<String> list) {
        for(T object : type.getRegistry()) {
            list.add(Stat.getName(type, object));
        }
    }

    private static boolean matchesWildcardSimple(String id, int stringStartIndex, List<String> wildcard, int wildcardIndex, boolean endsWithStar) {
        if(wildcardIndex == wildcard.size()) {
            return endsWithStar || id.length() == stringStartIndex;
        }
        String wildcardPart = wildcard.get(wildcardIndex);
        for(int i = stringStartIndex; i < id.length(); ++i) {
            if(id.startsWith(wildcardPart, i)) {
                if(matchesWildcardSimple(id, i + wildcard.get(wildcardIndex).length(), wildcard, wildcardIndex + 1, endsWithStar)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if(builder.getRemaining().isEmpty()) {
            builder.suggest("*");
        }
        return CommandSource.suggestMatching(getCriteria(), builder); //TODO
    }
}
