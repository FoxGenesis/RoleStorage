module watamebot.rolestorage {
	exports net.foxgenesis.rolestorage;
	requires transitive net.dv8tion.jda;
	requires transitive watamebot;
	requires com.zaxxer.hikari;
	requires java.sql;
	requires jsr305;
	requires org.slf4j;
	
	provides net.foxgenesis.watame.plugin.IPlugin with net.foxgenesis.rolestorage.RoleStoragePlugin;
	
	opens assets;
}