package it.crystalnest.cobweb;

import it.crystalnest.cobweb.platform.Services;
import it.crystalnest.cobweb.platform.services.ConfigHelper;
import it.crystalnest.cobweb.platform.services.PlatformHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common shared constants across all loaders.
 */
public final class Constants {
	private Constants() {}

	/**
	 * Mod id.
	 */
	public static final String MOD_ID = "cobweb";

	/**
	 * Mod logger.
	 */
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/**
	 * Provides information about what platform the mod is running on.
	 */
	public static final PlatformHelper PLATFORM = Services.load(PlatformHelper.class);

	/**
	 * Provides registration for configuration specs.
	 */
	public static final ConfigHelper CONFIG = Services.load(ConfigHelper.class);
}
