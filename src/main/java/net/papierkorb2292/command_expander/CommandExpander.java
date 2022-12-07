package net.papierkorb2292.command_expander;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.gamerule.v1.CustomGameRuleCategory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_expander.commands.VarCommand;
import net.papierkorb2292.command_expander.mixin_method_interfaces.VariableManagerContainer;
import net.papierkorb2292.command_expander.variables.VariableManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CommandExpander implements ModInitializer {
//TODO: Add command block output watcher
	public static final Logger LOGGER = LogManager.getLogger();
	private static final FeatureManager FEATURE_MANAGER = new FeatureManager(
			new CustomGameRuleCategory(
					new Identifier("command_expander", "features"),
					Text.literal("Command Expander")
							.styled(style -> style.withColor(Formatting.AQUA))));

	public static final String VARIABLE_FEATURE = "Variables";
	public static final DynamicCommandExceptionType USED_DISABLED_FEATURE = new DynamicCommandExceptionType(feature -> Text.literal("Usage of disabled feature: " + feature));

	public void onInitialize() {

		CommandRegistrationCallback.EVENT.register(VarCommand::register);

		FEATURE_MANAGER.addFeature(VARIABLE_FEATURE, false, (server, rule) -> {
			// Update usability of '/var' for the clients
			for(ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				server.getPlayerManager().sendCommandTree(player);
			}
		});

		LOGGER.info("Loaded Command Expander");
	}

	public static VariableManager getVariableManager(CommandContext<ServerCommandSource> context) {
		return ((VariableManagerContainer)context.getSource().getServer()).command_expander$getVariableManager();
	}

	public static Text buildCopyableText(String text) {
		return Text.literal(text).styled(style ->
				style.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text))
						.withUnderline(true)
						.withColor(0x55FF55)
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of("Click to copy"))));
	}

	public static boolean isFeatureEnabled(MinecraftServer server, String name) {
		return FEATURE_MANAGER.isEnabled(server, name);
	}

	@SuppressWarnings("RedundantThrows")
	public static <Return, Throws extends Throwable> Return callThrowingWrapOperation(Operation<Return> op, Object... args) throws Throws {
		return op.call(args);
	}
}
