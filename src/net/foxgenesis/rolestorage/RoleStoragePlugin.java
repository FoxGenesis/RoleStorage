package net.foxgenesis.rolestorage;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;

import org.apache.commons.configuration2.Configuration;

import net.dv8tion.jda.api.entities.Guild;
import net.foxgenesis.property.IProperty;
import net.foxgenesis.watame.WatameBot;
import net.foxgenesis.watame.plugin.IEventStore;
import net.foxgenesis.watame.plugin.Plugin;
import net.foxgenesis.watame.plugin.PluginConfiguration;
import net.foxgenesis.watame.plugin.SeverePluginException;
import net.foxgenesis.watame.property.IGuildPropertyMapping;

/**
 * A {@link WatameBot} plugin used for storing roles of guild members.
 * 
 * @author Ashley
 *
 */
@PluginConfiguration(defaultFile = "/META-INF/worker.ini", identifier = "worker", outputFile = "worker.ini")
public class RoleStoragePlugin extends Plugin {

	/**
	 * A boolean storage field to check if the plugin is enabled for a guild. <br>
	 * <br>
	 * <b>Key</b> = {@code rolestorage.enabled} <br>
	 * <b>Default</b> = {@code true} <br>
	 * <b>Editable</b> = {@code true} <br>
	 */
	// static final BooleanField enabled = new BooleanField("rolestorage-enabled",
	// guild -> true, true);
	static final IProperty<String, Guild, IGuildPropertyMapping> enabled = WatameBot.INSTANCE.getPropertyProvider()
			.getProperty("rolestorage_enabled");
	// ===================================================

	/**
	 * Listener for role updates
	 */
	private GuildListener guildListener;
	private int batchSize;

	@Override
	protected void onPropertiesLoaded(Properties properties) {}

	@Override
	protected void onConfigurationLoaded(String id, Configuration properties) {
		switch (id) {
			case "worker" -> { batchSize = properties.getInt("batchSize", 1000); }
		}
	}

	@Override
	protected void preInit() {
		try {
			@SuppressWarnings("resource") RoleStorageDatabase database = new RoleStorageDatabase(batchSize);
			WatameBot.INSTANCE.getDatabaseManager().register(this, database);
			guildListener = new GuildListener(database);
		} catch (UnsupportedOperationException | SQLException | IOException e) {
			throw new SeverePluginException(e, true);
		}
	}

	@Override
	protected void init(IEventStore builder) {
		builder.registerListeners(this, guildListener);
	}

	@Override
	protected void postInit(WatameBot bot) {}

	@Override
	protected void onReady(WatameBot bot) {
		// Perform initial scan of all guilds in cache
		guildListener.initialScan(bot.getJDA().getGuildCache());
	}

	@Override
	protected void close() throws Exception {
		guildListener.close();
	}
}
