package cn.frostcraft.firstjoinverification;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 首次进入服务器验证码验证插件。
 * 玩家第一次输入正确验证码后，会以 UUID 形式写入 verified.yml，之后不再重复验证。
 */
public final class FirstJoinVerificationPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private final Set<UUID> verifiedPlayers = new HashSet<>();
    private final Map<UUID, Integer> failedAttempts = new HashMap<>();

    private File verifiedFile;
    private String verificationCode;
    private int maxAttempts;
    private int promptIntervalSeconds;
    private boolean kickOnMaxAttempts;
    private boolean blockInteractions;
    private boolean blockInventory;
    private boolean blockDamage;
    private boolean blockItemPickup;
    private BukkitTask promptTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            loadSettings();
        } catch (IllegalArgumentException exception) {
            getLogger().severe(exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        verifiedFile = new File(getDataFolder(), "verified.yml");
        loadVerifiedPlayers();

        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("verify") == null || getCommand("verifyadmin") == null) {
            getLogger().severe("plugin.yml 中缺少 verify 或 verifyadmin 命令定义！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getCommand("verify").setExecutor(this);
        getCommand("verifyadmin").setExecutor(this);

        startPromptTask();

        // 兼容插件 /reload 后已经在线的玩家。
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isVerified(player)) {
                sendVerificationPrompt(player);
            }
        }

        getLogger().info("首次进入验证插件已启用。已记录验证玩家数量：" + verifiedPlayers.size());
        getLogger().info("Geyser/Floodgate 识别功能为可选功能；无相关插件时 Java 版验证仍可使用。");
    }

    @Override
    public void onDisable() {
        if (promptTask != null) {
            promptTask.cancel();
            promptTask = null;
        }
        saveVerifiedPlayers();
    }

    private void loadSettings() {
        String configuredCode = getConfig().getString("verification.code", "123456");
        if (configuredCode == null || !configuredCode.matches("[0-9]+")) {
            throw new IllegalArgumentException("配置无效：verification.code 必须是非空数字字符串。");
        }

        verificationCode = configuredCode;
        maxAttempts = Math.max(0, getConfig().getInt("verification.max-attempts", 5));
        kickOnMaxAttempts = getConfig().getBoolean("verification.kick-on-max-attempts", true);
        promptIntervalSeconds = Math.max(1, getConfig().getInt("verification.prompt-interval-seconds", 10));

        blockInteractions = getConfig().getBoolean("freeze.block-interactions", true);
        blockInventory = getConfig().getBoolean("freeze.block-inventory", true);
        blockDamage = getConfig().getBoolean("freeze.block-damage", true);
        blockItemPickup = getConfig().getBoolean("freeze.block-item-pickup", true);
    }

    private void loadVerifiedPlayers() {
        verifiedPlayers.clear();
        if (!verifiedFile.exists()) {
            return;
        }

        YamlConfiguration data = YamlConfiguration.loadConfiguration(verifiedFile);
        for (String rawUuid : data.getStringList("verified")) {
            try {
                verifiedPlayers.add(UUID.fromString(rawUuid));
            } catch (IllegalArgumentException exception) {
                getLogger().warning("verified.yml 中存在无效 UUID，已忽略：" + rawUuid);
            }
        }
    }

    private void saveVerifiedPlayers() {
        if (verifiedFile == null) {
            return;
        }
        YamlConfiguration data = new YamlConfiguration();
        List<String> uuids = verifiedPlayers.stream()
                .map(UUID::toString)
                .sorted()
                .toList();
        data.set("verified", uuids);
        try {
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                throw new IOException("无法创建插件数据目录");
            }
            data.save(verifiedFile);
        } catch (IOException exception) {
            getLogger().severe("保存 verified.yml 失败：" + exception.getMessage());
        }
    }

    /** 先写入文件，再把玩家标记为已验证，避免磁盘保存失败却放行。 */
    private boolean persistVerifiedPlayer(UUID uuid) {
        Set<UUID> updated = new HashSet<>(verifiedPlayers);
        updated.add(uuid);

        YamlConfiguration data = new YamlConfiguration();
        data.set("verified", updated.stream().map(UUID::toString).sorted().toList());
        try {
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                throw new IOException("无法创建插件数据目录");
            }
            data.save(verifiedFile);
            verifiedPlayers.add(uuid);
            return true;
        } catch (IOException exception) {
            getLogger().severe("保存玩家验证状态失败：" + exception.getMessage());
            return false;
        }
    }

    private void startPromptTask() {
        if (promptTask != null) {
            promptTask.cancel();
        }
        long intervalTicks = promptIntervalSeconds * 20L;
        promptTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!isVerified(player)) {
                    sendVerificationPrompt(player);
                }
            }
        }, intervalTicks, intervalTicks);
    }

    private boolean isVerified(Player player) {
        return verifiedPlayers.contains(player.getUniqueId());
    }

    private void sendVerificationPrompt(Player player) {
        String key = isBedrockPlayer(player) ? "messages.prompt-bedrock" : "messages.prompt-java";
        sendMessage(player, getConfig().getString(key,
                "&e请在聊天栏输入 &b/verify <数字验证码>&e 完成验证。"));
    }

    /**
     * 优先使用 Floodgate 判断，再使用 Geyser API。
     * 两个 API 都是 compileOnly + softdepend；没有安装时不会影响插件主体功能。
     */
    private boolean isBedrockPlayer(Player player) {
        UUID uuid = player.getUniqueId();

        if (getServer().getPluginManager().getPlugin("floodgate") != null) {
            try {
                if (BedrockApiHook.isFloodgatePlayer(uuid)) {
                    return true;
                }
            } catch (LinkageError | RuntimeException exception) {
                getLogger().fine("调用 Floodgate API 识别玩家失败：" + exception.getMessage());
            }
        }

        Plugin geyserPlugin = getServer().getPluginManager().getPlugin("Geyser-Spigot");
        if (geyserPlugin == null) {
            geyserPlugin = getServer().getPluginManager().getPlugin("Geyser");
        }
        if (geyserPlugin != null) {
            try {
                return BedrockApiHook.isGeyserPlayer(uuid);
            } catch (LinkageError | RuntimeException exception) {
                getLogger().fine("调用 Geyser API 识别玩家失败：" + exception.getMessage());
            }
        }

        return false;
    }

    private void sendMessage(CommandSender sender, String message) {
        String prefix = getConfig().getString("messages.prefix", "&8[&b首次验证&8] &r");
        sender.sendMessage(color(prefix + message));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private boolean samePosition(Location from, Location to) {
        if (to == null || from.getWorld() != to.getWorld()) {
            return false;
        }
        return Double.compare(from.getX(), to.getX()) == 0
                && Double.compare(from.getY(), to.getY()) == 0
                && Double.compare(from.getZ(), to.getZ()) == 0;
    }

    // -------------------- 玩家验证命令 --------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("verify")) {
            return handleVerifyCommand(sender, args);
        }
        if (command.getName().equalsIgnoreCase("verifyadmin")) {
            return handleAdminCommand(sender, args);
        }
        return true;
    }

    private boolean handleVerifyCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("该命令只能由玩家执行。");
            return true;
        }

        if (isVerified(player)) {
            sendMessage(player, getConfig().getString("messages.already-verified", "&a你已经完成验证。"));
            return true;
        }

        if (args.length != 1) {
            sendMessage(player, getConfig().getString("messages.usage", "&e用法：&b/verify <数字验证码>"));
            return true;
        }

        if (verificationCode.equals(args[0])) {
            if (!persistVerifiedPlayer(player.getUniqueId())) {
                sendMessage(player, getConfig().getString("messages.save-error", "&c验证状态保存失败，请联系管理员。"));
                return true;
            }

            failedAttempts.remove(player.getUniqueId());
            sendMessage(player, getConfig().getString("messages.success", "&a验证成功！现在可以正常游玩了。"));
            player.sendTitle(color("&a验证成功"), color("&f欢迎来到服务器！"), 5, 40, 10);
            return true;
        }

        int attempts = failedAttempts.merge(player.getUniqueId(), 1, Integer::sum);
        sendMessage(player, getConfig().getString("messages.incorrect", "&c验证码错误，请重新输入。"));

        if (maxAttempts > 0) {
            int remaining = Math.max(0, maxAttempts - attempts);
            sendMessage(player, getConfig().getString("messages.remaining-attempts", "&e剩余尝试次数：&c{remaining}")
                    .replace("{remaining}", String.valueOf(remaining)));

            if (attempts >= maxAttempts && kickOnMaxAttempts) {
                Bukkit.getScheduler().runTask(this, () -> {
                    if (player.isOnline() && !isVerified(player)) {
                        player.kickPlayer(color(getConfig().getString("messages.kick-limit",
                                "&c验证码错误次数过多，请重新进入服务器后再试。")));
                    }
                });
            }
        }
        return true;
    }

    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("firstjoinverification.admin")) {
            sendMessage(sender, getConfig().getString("messages.admin-no-permission", "&c你没有权限执行此命令。"));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            try {
                loadSettings();
                startPromptTask();
                sendMessage(sender, getConfig().getString("messages.admin-reloaded", "&a配置文件已重新加载。"));
            } catch (IllegalArgumentException exception) {
                sendMessage(sender, getConfig().getString("messages.admin-invalid-config",
                        "&c配置无效：verification.code 必须是非空数字字符串。"));
                getLogger().severe(exception.getMessage());
            }
            return true;
        }

        sender.sendMessage(color("&e用法：/verifyadmin reload"));
        return true;
    }

    // -------------------- 登录与移动限制 --------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!isVerified(player)) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (player.isOnline() && !isVerified(player)) {
                    sendVerificationPrompt(player);
                    player.sendTitle(color("&6首次进入验证"), color("&f请在聊天栏输入验证码"), 5, 50, 10);
                }
            }, 20L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (isVerified(event.getPlayer())) {
            return;
        }
        // 允许转动视角，但任何坐标变化（包括跳跃、走路、传送）都会被取消。
        if (!samePosition(event.getFrom(), event.getTo())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        failedAttempts.remove(event.getPlayer().getUniqueId());
    }

    // -------------------- 未验证期间的可选限制 --------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (blockInteractions && !isVerified(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (blockInteractions && !isVerified(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (blockInteractions && !isVerified(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (blockInteractions && !isVerified(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (blockInventory && !isVerified(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        Entity entity = event.getEntity();
        if (blockItemPickup && entity instanceof Player player && !isVerified(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (blockInventory && event.getWhoClicked() instanceof Player player && !isVerified(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (blockInventory && event.getWhoClicked() instanceof Player player && !isVerified(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (blockInventory && !isVerified(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (blockDamage && event.getEntity() instanceof Player player && !isVerified(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player && !isVerified(player)) {
            event.setCancelled(true);
        }
    }
}
