package me.arboriginal.Creepersday;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class CreepersdayPlayerListener implements Listener {
	private final Creepersday	plugin;

	public CreepersdayPlayerListener(final Creepersday plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onPlayerRespawn(PlayerRespawnEvent event) {
		plugin.getServer().getScheduler()
		    .scheduleAsyncDelayedTask(plugin, new CreepersdayPlayerListenerRunnable(event.getPlayer(), "respawn"), 10L);
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		plugin.getServer().getScheduler()
		    .scheduleAsyncDelayedTask(plugin, new CreepersdayPlayerListenerRunnable(event.getPlayer(), "join"), 10L);
	}

	@EventHandler
	public void onPlayerBedLeave(PlayerBedLeaveEvent event) {
		Player player = event.getPlayer();
		
		if (player != null) {
			World world = player.getWorld();

			if (plugin.isCreepersday(world)) {
				plugin.stopCreepersday(world);
			}
			else {
				if (plugin.shouldCreepersdayStart(world)) {
					plugin.startCreepersday(world);
				}
			}
		}
	}

	// Internal class

	private class CreepersdayPlayerListenerRunnable implements Runnable {
		private String	event;
		private Player	player;

		public CreepersdayPlayerListenerRunnable(Player player, String event) {
			this.event = event;
			this.player = player;
		}

		@Override
		public void run() {
			if (player != null) {
				World world = player.getWorld();

				if (plugin.isCreepersday(world)) {
					plugin.giveBonusToPlayer(player, event);

					if (event == "join") {
						if (plugin.shouldWarnPlayer(world, "during")) {
							player.sendMessage(plugin.getMessage(world, "day_active"));
						}
					}
				}
			}
		}
	}
}
