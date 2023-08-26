package net.foxgenesis.rolestorage;

import java.util.EnumSet;

import net.foxgenesis.util.resource.ConfigType;
import net.foxgenesis.watame.WatameBot;
import net.foxgenesis.watame.plugin.IEventStore;
import net.foxgenesis.watame.plugin.Plugin;
import net.foxgenesis.watame.plugin.SeverePluginException;
import net.foxgenesis.watame.plugin.require.PluginConfiguration;
import net.foxgenesis.watame.plugin.require.RequiresIntents;
import net.foxgenesis.watame.plugin.require.RequiresMemberCachePolicy;

import org.apache.commons.configuration2.Configuration;

import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

/**
 * A {@link WatameBot} plugin used for storing roles of guild members.
 * 
 * @author Ashley
 *
 */
@PluginConfiguration(defaultFile = "/META-INF/worker.ini", identifier = "worker", outputFile = "worker.ini", type = ConfigType.INI)
public class RoleStoragePlugin extends Plugin implements RequiresIntents, RequiresMemberCachePolicy {

	/**
	 * Listener for role updates
	 */
	private GuildListener guildListener;
	private RoleStorageDatabase database;
	private final int batchSize;

	public RoleStoragePlugin() {
		super();
		int size = 1000;

		for (String id : configurationKeySet()) {
			Configuration config = getConfiguration(id);
			switch (id) {
				case "worker" -> { size = config.getInt("BatchWorker.batchSize", size); }
			}
		}

		this.batchSize = size;
	}

	@Override
	protected void preInit() {
		try {
			database = new RoleStorageDatabase(batchSize);
			registerDatabase(database);
		} catch (Exception e) {
			throw new SeverePluginException(e, true);
		}
	}

	@Override
	protected void init(IEventStore builder) {
		guildListener = new GuildListener(this, getPropertyProvider(), database);
		builder.registerListeners(this, guildListener);
	}

	@Override
	protected void postInit() {}

	@Override
	protected void onReady() {
		// Perform initial scan of all guilds in cache
		guildListener.initialScan(WatameBot.getJDA().getGuildCache());
	}

	@Override
	protected void close() throws Exception {
		if (guildListener != null)
			guildListener.close();
	}

	@Override
	public EnumSet<GatewayIntent> getRequiredIntents() {
		return EnumSet.of(GatewayIntent.GUILD_MEMBERS);
	}

	@Override
	public MemberCachePolicy getPolicy() {
		return MemberCachePolicy.ALL;
	}
}
