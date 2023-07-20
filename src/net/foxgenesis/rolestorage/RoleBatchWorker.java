package net.foxgenesis.rolestorage;

import java.util.Collection;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

/**
 * Interface containing methods used for updating roles in mass.
 * 
 * @author Ashley
 *
 */
public interface RoleBatchWorker extends AutoCloseable {

	/**
	 * Add a role to a guild member in the database.
	 * 
	 * @param member - member to add role to
	 * @param role   - role to insert
	 * 
	 * @return This instance used for chaining
	 */

	public RoleBatchWorker addMemberRole(Member member, Role role);

	/**
	 * Remove a role from a guild member in the database.
	 * 
	 * @param member - member to remove role from
	 * @param role   - role to remove
	 * 
	 * @return This instance used for chaining
	 */

	public RoleBatchWorker removeMemberRole(Member member, Role role);

	/**
	 * Add roles to a guild member in the database.
	 * 
	 * @param member  - member to add roles to
	 * @param roleIDs - a collection of roles to add to the member
	 * 
	 * @return This instance used for chaining
	 */

	public default RoleBatchWorker addMemberRoles(Member member, Collection<Role> roleIDs) {
		for (Role role : roleIDs)
			addMemberRole(member, role);
		return this;
	}

	/**
	 * Remove roles from a guild member in the database.
	 * 
	 * @param member  - member to remove roles from
	 * @param roleIDs - a collection of roles to remove from the member
	 * 
	 * @return This instance used for chaining
	 */

	public default RoleBatchWorker removeMemberRoles(Member member, Collection<Role> roleIDs) {
		for (Role role : roleIDs)
			removeMemberRole(member, role);
		return this;
	}

	/**
	 * Flush all queues.
	 */
	public void flush();
}
