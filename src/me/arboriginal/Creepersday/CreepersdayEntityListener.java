package me.arboriginal.Creepersday;

import org.bukkit.World;
import org.bukkit.entity.CreatureType;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public class CreepersdayEntityListener implements Listener {
	private final Creepersday plugin;

	public CreepersdayEntityListener(final Creepersday plugin) {
		this.plugin = plugin;
	}
	
	@EventHandler
	public void onCreatureSpawn(CreatureSpawnEvent event) {
		CreatureType type = event.getCreatureType();

		if (type != CreatureType.CREEPER) {
			Entity entity = event.getEntity();
			World world = entity.getWorld();

			if (plugin.isCreepersday(world)
					&& plugin.shouldConvertEntity(entity, type, false)) {
				entity = plugin.convertEntity(entity, CreatureType.CREEPER);

				if (plugin.shouldPowerCreeper(world, type, false)) {
					plugin.givePower(entity);
				}
			}
		}
	}

	@EventHandler
	public void onEntityDeath(EntityDeathEvent event) {
		Entity entity = event.getEntity();
		World world = entity.getWorld();

		if (plugin.isCreepersday(world) && plugin.shouldDisplayStats(world)) {
			if (entity instanceof Player) {
				String looser = ((Player) entity).getName();

				if (looser != null) {// Looser IS null! :D
					plugin.logStat(world, looser, "deaths");
				}
			} else if (entity instanceof Creeper) {
				String killer = getKillerName(entity);

				if (killer != null) {
					plugin.logStat(world, killer, "kills");
				}
			}
		}
	}

	private String getKillerName(Entity entity) {
		String playerName = null;
		EntityDamageEvent cause = entity.getLastDamageCause();

		if (cause instanceof EntityDamageByEntityEvent) {
			Entity killBy = ((EntityDamageByEntityEvent) cause).getDamager();

			if (killBy instanceof Player) {
				return ((Player) killBy).getName();
			}

			if (killBy instanceof Projectile) {
				killBy = ((Projectile) killBy).getShooter();

				if (killBy instanceof Player) {
					return ((Player) killBy).getName();
				}
			}
		}

		return playerName;
	}
}
