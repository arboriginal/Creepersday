package me.arboriginal.Creepersday;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.reader.UnicodeReader;
import org.yaml.snakeyaml.representer.Representer;

public class Creepersday extends JavaPlugin {
	protected Map<String, Object>	pluginConfig	= new HashMap<String, Object>();

	// Bukkit hooks

	@Override
	public void onEnable() {
		copyDocumentation();
		initConfig();
		registerEvents();
		registerRepeatingTask();
		System.out.println("[Creepersday] Plugin loaded and running.");
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (command.getName().equals("creepersday")) {
			return runCommandForceState(sender, args);
		}

		if (command.getName().equals("creepersday-reload-config")) {
			return runCommandReloadConfig(sender, args);
		}

		return false;
	}

	// Public methods

	public void startCreepersday(World world) {
		if (world == null) {
			return;
		}

		String key = world.getEnvironment() + "." + world.getName() + ".last_check_day";
		convertEntities(world, true);

		setCurrentStatus(world, "active");
		setProperty(key, getCurrentDay(world));
		System.out.println("** " + world.getName() + ": Creepersday is starting.");

		if (shouldDisplayStats(world)) {
			resetStats(world);
		}
	}

	public void stopCreepersday(World world) {
		if (world == null) {
			return;
		}

		String key = world.getEnvironment() + "." + world.getName() + ".last_check_day";

		resetCurrentStatus(world);
		setProperty(key, getCurrentDay(world));
		System.out.println("** " + world.getName() + ": Creepersday is over.");

		convertEntities(world, false);

		if (shouldDisplayStats(world)) {
			displayStats(world);
		}
	}

	public boolean isCreepersday(World world) {
		return (world != null) && (getCurrentStatus(world).equals("active"));
	}

	public boolean shouldCreepersdayStart(World world) {
		if (world != null) {
			String key = world.getEnvironment() + "." + world.getName() + ".last_check_day";
			int currentDay = getCurrentDay(world);

			if (currentDay > (Integer) getProperty(key)) {
				setProperty(key, currentDay);

				return getStringProperty(world, "status").equals("random")
				    && world.getTime() <= getIntProperty(world, "advanced.start_before")
				    && testPercentChance(world, "creepersday_chance");
			}
		}

		return false;
	}

	public boolean shouldCreepersdayStop(World world) {
		if (world == null) {
			return true;
		}

		String key = world.getEnvironment() + "." + world.getName() + ".last_check_day";

		return (getCurrentDay(world) > (Integer) getProperty(key))
		    || (world.getTime() >= getIntProperty(world, "advanced.stop_after"));
	}

	public boolean shouldConvertEntity(Entity entity, EntityType type, boolean start) {
		if (type == EntityType.WOLF && ((Wolf) entity).isTamed()) {
			return false;
		}

		String key = (start ? "start" : "during") + "_creepersday.mobs_transformation.";

		return testPercentChance(entity.getWorld(), key + type + ".to_creeper");
	}

	public boolean shouldPowerCreeper(World world, EntityType type, boolean start) {
		String key = (start ? "start" : "during") + "_creepersday.mobs_transformation.";

		return testPercentChance(world, key + type + ".get_power");
	}

	public boolean shouldDisplayStats(World world) {
		return getBooleanProperty(world, "display_stats");
	}

	public void logStat(World world, String playerName, String event) {
		String key = world.getEnvironment() + "." + world.getName() + ".stats";
		@SuppressWarnings("unchecked")
		LinkedHashMap<String, LinkedHashMap<String, Integer>> stats = (LinkedHashMap<String, LinkedHashMap<String, Integer>>) getProperty(key);
		LinkedHashMap<String, Integer> playerStats = stats.containsKey(playerName) ? stats.get(playerName)
		    : new LinkedHashMap<String, Integer>();
		playerStats.put(event, playerStats.containsKey(event) ? playerStats.get(event) + 1 : 1);
		stats.put(playerName, playerStats);
		setProperty(key, stats);
	}

	public void resetStats(World world) {
		setProperty(world.getEnvironment() + "." + world.getName() + ".stats",
		    new LinkedHashMap<String, LinkedHashMap<String, Integer>>());
	}

	public void displayStats(World world) {
		String[] stats = buildStats(world);

		if (stats != null && stats.length > 0) {
			for (Entity entity : world.getLivingEntities()) {
				if (entity instanceof Player) {
					for (String stat : stats) {
						((Player) entity).sendMessage(stat);
					}
				}
			}
		}
	}

	public boolean testPercentChance(World world, String key) {
		return (getIntProperty(world, key) >= 100 * Math.random());
	}

	public Entity convertEntity(Entity entity, EntityType type) {
		Location loc = entity.getLocation();
		World world = entity.getWorld();

		entity.remove();

		if (type != null) {
			entity = world.spawnCreature(loc, type);
		}

		return entity;
	}

	public void givePower(Entity entity) {
		((Creeper) entity).setPowered(true);
	}

	public void giveBonusToPlayer(Player player, String event) {
		if (player == null) {
			return;
		}

		World world = player.getWorld();
		LinkedHashMap<String, Integer> bonus = getPlayerBonus(world, event);

		if (bonus != null && !bonus.isEmpty()) {
			giveStuffToPlayer(player, bonus);

			if (shouldWarnPlayer(world, "on_" + event + "_bonus")) {
				player.sendMessage(ChatColor.LIGHT_PURPLE + getMessage(world, "bonus_" + event));
			}
		}
	}

	public String getInitialStatus(World world) {
		if (world == null) {
			return "random";
		}

		return getStringProperty(world, "status");
	}

	public String getCurrentStatus(World world) {
		if (world == null) {
			return getInitialStatus(world);
		}

		return getStringProperty(world, "current_status");
	}

	public void setCurrentStatus(World world, String status) {
		setProperty(world.getEnvironment() + "." + world.getName() + ".current_status", status);
	}

	public void resetCurrentStatus(World world) {
		setCurrentStatus(world, getInitialStatus(world));
	}

	@SuppressWarnings("rawtypes")
	public LinkedHashMap getPlayerBonusOnStartCreepersday(World world) {
		return getMapProperty(world, "start_creepersday.player_bonus");
	}

	@SuppressWarnings("unchecked")
	public LinkedHashMap<String, Integer> getPlayerBonus(World world, String event) {
		LinkedHashMap<String, Integer> bonus = null;

		if (world == null) {
		}
		else if (event.equals("start")) {
			bonus = getPlayerBonusOnStartCreepersday(world);
		}
		else if (event.equals("respawn")) {
			bonus = getPlayerBonusOnRespawnDuringCreepersday(world);
		}
		else if (event.equals("join")) {
			bonus = getPlayerBonusOnJoinDuringCreepersday(world);
		}
		else if (event.equals("stop")) {
			bonus = getPlayerBonusOnStopCreepersday(world);
		}

		return bonus;
	}

	@SuppressWarnings("rawtypes")
	public LinkedHashMap getPlayerBonusOnJoinDuringCreepersday(World world) {
		return getMapProperty(world, "during_creepersday.player_bonus.on_join");
	}

	@SuppressWarnings("rawtypes")
	public LinkedHashMap getPlayerBonusOnRespawnDuringCreepersday(World world) {
		return getMapProperty(world, "during_creepersday.player_bonus.on_respawn");
	}

	@SuppressWarnings("rawtypes")
	public LinkedHashMap getPlayerBonusOnStopCreepersday(World world) {
		return getMapProperty(world, "stop_creepersday.player_bonus");
	}

	public String getLanguage(World world) {
		if (world == null) {
			return "EN";
		}

		return getStringProperty(world, "language");
	}

	public String getMessage(World world, String key) {
		String message = getStringProperty(world, "messages." + key + "." + getLanguage(world));

		if (message != null) {
			return message;
		}

		return getStringProperty(world, "messages." + key + ".EN");
	}

	public boolean shouldWarnPlayer(World world, String event) {
		return getBooleanProperty(world, "warn_player." + event);
	}

	@SuppressWarnings("rawtypes")
	public LinkedHashMap getCreeperTransformMatrix(World world, boolean powered) {
		String is_powered = powered ? "powered" : "normal";

		return getMapProperty(world, "stop_creepersday.creepers_transformation." + is_powered);
	}

	// Private methods

	private void initConfig() {
		String configDir = getDataFolder().getPath();
		getConfig(configDir + "/config.yml", "res/configs/default_config.yml", "default");
		configDir += "/Worlds_configs/";

		for (World world : getServer().getWorlds()) {
			String name = world.getName();
			Environment env = world.getEnvironment();

			String directory = configDir + env + "/";
			String emptyFile = "res/configs/" + ((env == Environment.NORMAL) ? "normal" : "others") + "_empty_config.yml";

			getConfig(directory + "default_" + env + "_worlds.yml", emptyFile, env + ".default");
			getConfig(directory + name + ".yml", emptyFile, env + "." + name);
			setProperty(env + "." + name + ".last_check_day", getCurrentDay(world));

			resetCurrentStatus(world);

			if (isCreepersday(world)) {
				startCreepersday(world);
			}
		}
	}

	private int getCurrentDay(World world) {
		if (world == null) {
			return 0;
		}

		return (int) Math.floor(world.getFullTime() / 24000);
	}

	private void registerEvents() {
		PluginManager pluginManager = getServer().getPluginManager();

		pluginManager.registerEvents(new CreepersdayEntityListener(this), this);
		pluginManager.registerEvents(new CreepersdayPlayerListener(this), this);
	}

	private void registerRepeatingTask() {
		for (World world : getServer().getWorlds()) {
			getServer().getScheduler().scheduleAsyncRepeatingTask(this, new CreepersdayRunnable(world), 0,
			    getIntProperty(world, "advanced.time_delay"));
		}
	}

	private void getConfig(String configFile, String defaultFile, String key) {
		File file = new File(configFile);

		if (!file.exists()) {
			copyDefaultConfig(defaultFile, file);
		}

		attachConfig(file, key);
	}

	private void attachConfig(File file, String key) {
		Yaml yaml = new Yaml(new SafeConstructor(), new Representer());
		FileInputStream in = null;

		try {
			in = new FileInputStream(file);
		}
		catch (FileNotFoundException e) {
		}
		finally {
			try {
				if (in != null) {
					@SuppressWarnings("unchecked")
					Map<String, Object> conf = (Map<String, Object>) yaml.load(new UnicodeReader(in));
					setProperty(key, conf);
					in.close();
				}
			}
			catch (IOException e) {
			}
		}
	}

	private void copyDefaultConfig(String source, File destination) {
		File directory = destination.getParentFile();

		if (directory != null) {
			directory.mkdirs();
		}

		InputStream is = getClass().getResourceAsStream(source);
		FileOutputStream os = null;

		try {
			os = new FileOutputStream(destination);
			int b;

			while ((b = is.read()) != -1) {
				os.write(b);
			}
		}
		catch (FileNotFoundException e) {
		}
		catch (IOException e) {
		}
		finally {
			try {
				os.close();
				is.close();
			}
			catch (IOException e) {
			}
		}
	}

	private String getStringProperty(World world, String property) {
		if (world == null) {
			return "";
		}

		String name = world.getName();
		String env = world.getEnvironment().toString();

		String value = (String) getProperty(env + "." + name + "." + property);

		if (value != null) {
			return value;
		}

		value = (String) getProperty(env + ".default." + property);

		if (value != null) {
			return value;
		}

		value = (String) getProperty("default." + property);

		return value;
	}

	private boolean getBooleanProperty(World world, String property) {
		if (world == null) {
			return false;
		}

		String name = world.getName();
		String env = world.getEnvironment().toString();

		Object value = getProperty(env + "." + name + "." + property);

		if (value != null) {
			return value.equals(true);
		}

		value = getProperty(env + ".default." + property);

		if (value != null) {
			return value.equals(true);
		}

		value = getProperty("default." + property);

		if (value != null) {
			return value.equals(true);
		}

		return true;
	}

	private Integer getIntProperty(World world, String property) {
		if (world != null) {
			String name = world.getName();
			String env = world.getEnvironment().toString();

			Object value = getProperty(env + "." + name + "." + property);

			if (value != null) {
				return (Integer) value;
			}

			value = getProperty(env + ".default." + property);

			if (value != null) {
				return (Integer) value;
			}

			value = getProperty("default." + property);

			if (value != null) {
				return (Integer) value;
			}
		}

		return 0;
	}

	@SuppressWarnings("rawtypes")
	private LinkedHashMap getMapProperty(World world, String property) {
		if (world != null) {
			String name = world.getName();
			String env = world.getEnvironment().toString();

			Object value = getProperty(env + "." + name + "." + property);

			if (value != null) {
				return (LinkedHashMap) value;
			}

			value = getProperty(env + ".default." + property);

			if (value != null) {
				return (LinkedHashMap) value;
			}

			value = getProperty("default." + property);

			if (value != null) {
				return (LinkedHashMap) value;
			}
		}

		return new LinkedHashMap();
	}

	private void giveStuffToPlayer(Player player, LinkedHashMap<String, Integer> stuff) {
		boolean behind = false;
		World world = player.getWorld();
		Location loc = player.getLocation();
		Inventory inv = player.getInventory();

		for (Iterator<String> i = stuff.keySet().iterator(); i.hasNext();) {
			String name = i.next();
			ItemStack item = new ItemStack(Material.getMaterial(name), stuff.get(name));

			if (inv.firstEmpty() == -1) {
				world.dropItem(loc, item);
				behind = true;
			}
			else {
				inv.addItem(item);
			}
		}

		if (behind) {
			player.teleport(loc);
		}
	}

	private void convertEntities(World world, boolean start) {
		ArrayList<EntityType> normal = new ArrayList<EntityType>();
		ArrayList<EntityType> powered = new ArrayList<EntityType>();

		if (!start) {
			populateRandomsMobs(normal, world, false);
			populateRandomsMobs(powered, world, true);
		}

		for (Chunk chunk : world.getLoadedChunks()) {
			for (Entity entity : chunk.getEntities()) {
				String className = getEntityType(entity);

				// For Spout / SpoutCraft, we need to check also "SpoutPlayer"
				if (className.equals("PLAYER") || className.equals("SPOUTPLAYER")) {
					String event = start ? "start" : "stop";

					giveBonusToPlayer((Player) entity, event);

					if (shouldWarnPlayer(world, "on_" + event)) {
						((Player) entity).sendMessage(ChatColor.DARK_GREEN + getMessage(world, "day_" + event));
					}
				}
				else {
					if (start) {
						convertEntityOnStart(entity, className);
					}
					else if (className.equals("CREEPER")) {
						convertEntityOnStop(entity, className, ((Creeper) entity).isPowered() ? powered : normal);
					}
				}
			}
		}
	}

	private void convertEntityOnStart(Entity entity, String className) {
		try {
			EntityType type = EntityType.valueOf(className);

			if (shouldConvertEntity(entity, type, true)) {
				entity = convertEntity(entity, EntityType.CREEPER);

				if (shouldPowerCreeper(entity.getWorld(), type, true)) {
					givePower(entity);
				}
			}
		}
		catch (IllegalArgumentException e) {
		}
	}

	private void convertEntityOnStop(Entity entity, String className, ArrayList<EntityType> list) {
		EntityType type = null;
		int random = (int) (Math.random() * 100);

		if (random < list.size()) {
			type = list.get(random);
		}

		convertEntity(entity, type);
	}

	private void populateRandomsMobs(ArrayList<EntityType> matrix, World world, boolean powered) {
		@SuppressWarnings("unchecked")
		LinkedHashMap<String, Integer> mobs = getCreeperTransformMatrix(world, powered);

		for (Iterator<String> i = mobs.keySet().iterator(); i.hasNext();) {
			String name = i.next();
			int number = mobs.get(name);

			for (int j = 0; j < number; j++) {
				try {
					matrix.add(EntityType.valueOf(name));
				}
				catch (IllegalArgumentException e) {
				}
			}
		}
	}

	private String[] buildStats(World world) {
		ArrayList<LinkedHashMap<String, Object>> scores = calculateScore(world);

		if (scores.size() == 0) {
			return null;
		}

		Collections.sort(scores, new Comparator<LinkedHashMap<String, Object>>() {
			@Override
			public int compare(LinkedHashMap<String, Object> o1, LinkedHashMap<String, Object> o2) {
				return (Integer) o2.get("score") - (Integer) o1.get("score");
			}
		});

		int maxPlayer = getIntProperty(world, "max_player_in_stats");
		String[] top = new String[Math.min(maxPlayer, scores.size()) + 2];
		LinkedHashMap<String, Object> playerDatas;

		top[0] = ChatColor.LIGHT_PURPLE + getMessage(world, "stats_title").replace("<max_player_in_stats>", "" + maxPlayer);
		top[1] = ChatColor.GRAY + getMessage(world, "stats_explanations");

		for (int i = 2; i < top.length; i++) {
			playerDatas = (LinkedHashMap<String, Object>) scores.get(i - 2);

			top[i] = ChatColor.YELLOW + "" + (i - 1) + ". " + ChatColor.GOLD + "" + playerDatas.get("score")
			    + ChatColor.WHITE + " (" + ChatColor.GREEN + "" + playerDatas.get("kills") + ChatColor.WHITE + "/"
			    + ChatColor.RED + playerDatas.get("deaths") + ChatColor.WHITE + ") " + ChatColor.YELLOW + ""
			    + playerDatas.get("name");

			gratifyPlayer(world, i - 1, (String) playerDatas.get("name"));
		}

		return top;
	}

	private ArrayList<LinkedHashMap<String, Object>> calculateScore(World world) {
		ArrayList<LinkedHashMap<String, Object>> scores = new ArrayList<LinkedHashMap<String, Object>>();
		LinkedHashMap<String, Object> playerDatas;

		@SuppressWarnings("unchecked")
		LinkedHashMap<String, LinkedHashMap<String, Integer>> stats = (LinkedHashMap<String, LinkedHashMap<String, Integer>>) getProperty(world
		    .getEnvironment() + "." + world.getName() + ".stats");

		for (Iterator<String> i = stats.keySet().iterator(); i.hasNext();) {
			playerDatas = new LinkedHashMap<String, Object>();
			String playerName = i.next();
			LinkedHashMap<String, Integer> playerStats = stats.get(playerName);
			int kills = playerStats.containsKey("kills") ? playerStats.get("kills") : 0;
			int deaths = playerStats.containsKey("deaths") ? playerStats.get("deaths") : 0;

			playerDatas.put("name", playerName);
			playerDatas.put("kills", kills);
			playerDatas.put("deaths", deaths);
			playerDatas.put("score", calculatePlayerScore(world, kills, deaths));

			scores.add(playerDatas);
		}

		return scores;
	}

	private int calculatePlayerScore(World world, int kills, int deaths) {
		return kills * getIntProperty(world, "points.kill_creeper") + deaths * getIntProperty(world, "points.player_death");
	}

	private void gratifyPlayer(World world, int rank, String playerName) {
		String command = getStringProperty(world, "greetings.rank" + rank + ".command");
		String message = getStringProperty(world, "greetings.rank" + rank + ".message");

		if (command != null && !command.equals("")) {
			getServer().dispatchCommand(getServer().getConsoleSender(), command.replace("<player>", (String) playerName));
		}

		if (message != null && !message.equals("")) {
			Player player = getServer().getPlayer(playerName);

			if (player != null) {
				player.sendMessage(message.replace("<player>", (String) playerName));
			}
		}
	}

	private void copyDocumentation() {
		String[] docs = { "EN.txt", "FR.txt" };

		for (String doc : docs) {
			File file = new File(getDataFolder().getPath() + "/Docs/" + doc);

			if (!file.exists()) {
				copyDefaultConfig("res/docs/" + doc, file);
			}
		}
	}

	/**
	 * Ugly method to know the type of a creature outside of spawn event.
	 */
	private String getEntityType(Entity entity) {
		String type = entity.getClass().getSimpleName().replace("Craft", "").toUpperCase();

		if (type.equals("CAVESPIDER")) {
			type = "CAVE_SPIDER";
		}
		else if (type.equals("PIGZOMBIE")) {
			type = "PIG_ZOMBIE";
		}
		else if (type.equals("ENDERDRAGON")) {
			type = "ENDER_DRAGON";
		}
		else if (type.equals("MUSHROOMCOW")) {
			type = "MUSHROOM_COW";
		}
		else if (type.equals("MAGMACUBE")) {
			type = "MAGMA_CUBE";
		}

		return type;
	}

	private boolean runCommandReloadConfig(CommandSender sender, String[] args) {
		if (!sender.hasPermission("creepersday.reload")) {
			sender.sendMessage(ChatColor.RED + "You don't have permission to reload creepersday config!");
			return true;
		}

		if (args.length == 0) {
			initConfig();
			sender.sendMessage(ChatColor.GREEN + "Creepersday config has been reloaded!");
			return true;
		}

		return false;
	}

	private boolean runCommandForceState(CommandSender sender, String[] args) {
		if (!sender.hasPermission("creepersday.force")) {
			sender.sendMessage(ChatColor.RED + "You don't have permission to force creepersday state!");
			return true;
		}

		boolean inGame = (sender instanceof Player);
		World world = null;

		if (inGame && args.length == 1) {
			world = ((Player) sender).getWorld();
		}
		else if (args.length != 2) {
			return false;
		}

		if (world == null) {
			world = getServer().getWorld(args[1]);

			if (world == null) {
				sender.sendMessage(ChatColor.RED + "This world doesn't exist!");
				return false;
			}
		}

		if (args[0].equals("start")) {
			startCreepersday(world);
			return true;
		}

		if (args[0].equals("stop")) {
			stopCreepersday(world);
			return true;
		}

		return false;
	}

	/**
	 * Grab from org.bukkit.util.config.ConfigurationNode (I've simply replaced
	 * "root" by "pluginConfig") and set the method to private.
	 */
	@SuppressWarnings("unchecked")
	private Object getProperty(String path) {
		if (!path.contains(".")) {
			Object val = pluginConfig.get(path);

			if (val == null) {
				return null;
			}
			return val;
		}

		String[] parts = path.split("\\.");
		Map<String, Object> node = pluginConfig;

		for (int i = 0; i < parts.length; i++) {
			Object o = node.get(parts[i]);

			if (o == null) {
				return null;
			}

			if (i == parts.length - 1) {
				return o;
			}

			try {
				node = (Map<String, Object>) o;
			}
			catch (ClassCastException e) {
				return null;
			}
		}

		return null;
	}

	/**
	 * Grab from org.bukkit.util.config.ConfigurationNode (I've simply replaced
	 * "root" by "pluginConfig") and set the method to private.
	 */
	@SuppressWarnings("unchecked")
	private void setProperty(String path, Object value) {
		if (!path.contains(".")) {
			pluginConfig.put(path, value);
			return;
		}

		String[] parts = path.split("\\.");
		Map<String, Object> node = pluginConfig;

		for (int i = 0; i < parts.length; i++) {
			Object o = node.get(parts[i]);

			if (i == parts.length - 1) {
				node.put(parts[i], value);
				return;
			}

			if (o == null || !(o instanceof Map)) {
				o = new HashMap<String, Object>();
				node.put(parts[i], o);
			}

			node = (Map<String, Object>) o;
		}
	}

	// Internal class

	private class CreepersdayRunnable implements Runnable {
		private World	world;

		public CreepersdayRunnable(World world) {
			this.world = world;
		}

		@Override
		public void run() {
			if (isCreepersday(world)) {
				if (shouldCreepersdayStop(world)) {
					stopCreepersday(world);
				}
			}
			else {
				if (shouldCreepersdayStart(world)) {
					startCreepersday(world);
				}
			}
		}
	}
}
