package net.foxgenesis.rolestorage;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.cache.SnowflakeCacheView;
import net.foxgenesis.property.IProperty;
import net.foxgenesis.watame.WatameBot;
import net.foxgenesis.watame.property.IGuildPropertyMapping;

/**
 * Class for listening to role updates in a guild. All role updates are stored
 * into a database.
 * 
 * @author Ashley
 *
 */
public class GuildListener extends ListenerAdapter implements AutoCloseable {

	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger("Role Storage Listener");

	private static final IProperty<String, Guild, IGuildPropertyMapping> enabled = WatameBot.INSTANCE
			.getPropertyProvider().getProperty("rolestorage_enabled");

	/**
	 * Database to use for role storage
	 */
	private RoleStorageDatabase database;

	/**
	 * Construct a new listener to listen to guild updates.
	 * 
	 * @throws UnsupportedOperationException
	 * @throws SQLException
	 * @throws IOException
	 */
	public GuildListener(RoleStorageDatabase database) throws UnsupportedOperationException, SQLException, IOException {
		this.database = Objects.requireNonNull(database);
	}

	@Override
	public void onGuildJoin(GuildJoinEvent event) {
		scanGuild(event.getGuild());
	}

	@Override
	public void onGuildLeave(GuildLeaveEvent event) {
		database.removeGuild(event.getGuild());
	}

	@Override
	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		Guild guild = event.getGuild();
		if (enabled.get(guild, false, IGuildPropertyMapping::getAsBoolean)) {
			Member member = event.getMember();
			Member bot = guild.getSelfMember();

			if (bot.hasPermission(Permission.MANAGE_ROLES)) {
				List<Role> roles = database.getAllMemberRolesInGuild(member, bot::canInteract);
				if (!roles.isEmpty()) {
					logger.debug("Giving roles {} to {} in {}", roles, member, guild);
					guild.modifyMemberRoles(member, roles).queue();
				}
			}
		}
	}

	@Override
	public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
		Guild guild = event.getGuild();

		if (enabled.get(guild, false, IGuildPropertyMapping::getAsBoolean)) {
			Member member = event.getMember();
			List<Role> roles = event.getRoles();

			logger.debug("Adding roles ({}) for {} in {}", roles, member, guild);
			database.addMemberRoles(member, roles);
		}
	}

	@Override
	public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
		Guild guild = event.getGuild();

		if (enabled.get(guild, false, IGuildPropertyMapping::getAsBoolean)) {
			Member member = event.getMember();
			List<Role> roles = event.getRoles();

			logger.debug("Removing roles ({}) for {} in {}", roles, member, guild);
			database.removeMemberRoles(member, roles);
		}
	}

	@Override
	public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
		// Guild guild = event.getGuild();
		// if (RoleStoragePlugin.enabled.optFrom(guild)) {
		// // TODO on guild member leave, set roles if caching is enabled
		// }
	}

	@Override
	public void close() throws Exception {
		database.close();
	}

	/**
	 * Perform a full scan of all guilds in the cache.
	 * 
	 * @param cache - {@link JDA} guild cache
	 */
	public void initialScan(SnowflakeCacheView<Guild> cache) {
		logger.info("Performing initial guild scan");

		long start = System.currentTimeMillis();
		cache.acceptStream(stream -> stream.forEach(this::scanGuild));
		double end = (System.currentTimeMillis() - start) / 1000D;

		logger.info("Finished inital scan in {}", "%.2f ms".formatted(end));
	}

	/**
	 * Scan a guild for all member roles and upsert them into the database.
	 * 
	 * @param guild - Guild to scan
	 */
	private void scanGuild(Guild guild) {
		if (enabled.get(guild, true, IGuildPropertyMapping::getAsBoolean)) {
			logger.info("Scanning {} for roles...", guild.getName());

			try (BatchWorker worker = database.getBatchWorker()) {
				long startTime = System.currentTimeMillis();

				guild.getMemberCache().acceptStream(stream -> {
					// FIXME WHY WONT U LET ME DO PARALLEL AAAAAAAAAA
					// if(guild.getMemberCount() > 1024)
					// stream = stream.parallel();

					stream.forEach(member -> worker.addMemberRoles(member, member.getRoles()));
					double end = (System.currentTimeMillis() - startTime) / 1000D;
					logger.info("Finished scanning {} for roles in {}", guild.getName(), "%.2f ms".formatted(end));
				});
			}
		}
	}
}
