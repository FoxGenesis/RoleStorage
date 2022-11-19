package net.foxgenesis.watame.rolestorage;

import java.util.Collection;

import javax.annotation.Nonnull;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

/**
 * Interface containing methods used for updating roles in mass.
 * 
 * @author Ashley
 *
 */
public interface RoleBatchWorker {

	/**
	 * Add a role to a guild member in the database.
	 * 
	 * @param member - member to add role to
	 * @param role   - role to insert
	 * @return This instance used for chaining
	 */
	@Nonnull
	public RoleBatchWorker addMemberRole(@Nonnull Member member, @Nonnull Role role);

	/**
	 * Remove a role from a guild member in the database.
	 * 
	 * @param member - member to remove role from
	 * @param role   - role to remove
	 * @return This instance used for chaining
	 */
	@Nonnull
	public RoleBatchWorker removeMemberRole(@Nonnull Member member, @Nonnull Role role);

	/**
	 * Add roles to a guild member in the database.
	 * 
	 * @param member  - member to add roles to
	 * @param roleIDs - a collection of roles to add to the member
	 * @return This instance used for chaining
	 */
	@Nonnull
	public default RoleBatchWorker addMemberRoles(@Nonnull Member member, @Nonnull Collection<Role> roleIDs) {
		for (Role role : roleIDs)
			addMemberRole(member, role);
		return this;
	}

	/**
	 * Remove roles from a guild member in the database.
	 * 
	 * @param member  - member to remove roles from
	 * @param roleIDs - a collection of roles to remove from the member
	 * @return This instance used for chaining
	 */
	@Nonnull
	public default RoleBatchWorker removeMemberRoles(@Nonnull Member member, @Nonnull Collection<Role> roleIDs) {
		for (Role role : roleIDs)
			removeMemberRole(member, role);
		return this;
	}

	/**
	 * Flush all queues.
	 */
	public void flush();
}
