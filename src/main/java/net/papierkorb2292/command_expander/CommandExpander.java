package net.papierkorb2292.command_expander;

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CommandExpander implements ModInitializer {

	public static final Logger LOGGER = LogManager.getLogger();

	@Override
	public void onInitialize() {


		LOGGER.info("Loaded Command Expander");
	}
}
