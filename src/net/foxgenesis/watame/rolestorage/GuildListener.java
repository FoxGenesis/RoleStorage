package net.foxgenesis.watame.rolestorage;

import java.io.IOException;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.cache.SnowflakeCacheView;
import net.foxgenesis.util.ResourceHelper;

/**
 * Class for listening to role updates in a guild. All role updates are stored
 * into a database.
 * 
 * @author Ashley
 *
 */
public class GuildListener extends ListenerAdapter implements AutoCloseable {
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
	public GuildListener() throws UnsupportedOperationException, SQLException, IOException {
		database = new RoleStorageDatabase(this::getDatabase);
		database.setup();
	}

	@Override
	public void onGuildJoin(GuildJoinEvent event) { scanGuild(event.getGuild()); }

	@Override
	public void onGuildLeave(GuildLeaveEvent event) { database.removeGuild(event.getGuild()); }

	@Override
	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		Guild guild = event.getGuild();
		if (RoleStoragePlugin.enabled.optFrom(guild)) {
			Member member = event.getMember();

			guild.modifyMemberRoles(member, database.getAllMemberRolesInGuild(member)).queue();
		}
	}

	@Override
	public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
		if (RoleStoragePlugin.enabled.optFrom(event.getGuild())) {
			database.addMemberRoles(event.getMember(), event.getRoles());
		}
	}

	@Override
	public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
		if (RoleStoragePlugin.enabled.optFrom(event.getGuild())) {
			database.removeMemberRoles(event.getMember(), event.getRoles());
		}
	}

	@Override
	public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
		Guild guild = event.getGuild();
		if (RoleStoragePlugin.enabled.optFrom(guild)) {
			// TODO on guild member leave, set roles if caching is enabled
		}
	}

	@Override
	public void close() throws Exception { database.close(); }

	/**
	 * Perform a full scan of all guilds in the cache.
	 * 
	 * @param cache - {@link JDA} guild cache
	 */
	public void initialScan(SnowflakeCacheView<Guild> cache) {
		cache.acceptStream(stream -> stream.forEach(this::scanGuild));
	}

	/**
	 * Scan a guild for all member roles and upsert them into the database.
	 * 
	 * @param guild - Guild to scan
	 */
	private void scanGuild(Guild guild) {
		if (RoleStoragePlugin.enabled.optFrom(guild)) {
			BatchWorker worker = database.getBatchWorker();
			guild.loadMembers(member -> worker.addMemberRoles(member, member.getRoles())).onSuccess(v -> worker.close())
					.onError(err -> worker.close());
		}
	}

	/**
	 * Construct a new link to a database
	 * 
	 * @return A {@link DataSource} pointing to an SQL connection
	 */
	private DataSource getDatabase() {
		try {
			return new HikariDataSource(new HikariConfig(
					ResourceHelper.getPropertiesResource(this.getClass().getResource("/assets/database.properties"))));
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
}
