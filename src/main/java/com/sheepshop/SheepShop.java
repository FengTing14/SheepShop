package com.sheepshop;

import io.github.sheepdungeon.api.SheepDungeonAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SheepShop —— 地牢币商店。
 *
 * <p>两种入口：</p>
 * <ul>
 *   <li><b>房间商店</b>：站在 SheepDungeon 的 SHOP 房间里自动打开，商品由那个房间的配置决定；</li>
 *   <li><b>常驻商店</b>：{@code /shop open <商店id>}，商品写在 config.yml。</li>
 * </ul>
 *
 * <p>所有价格都扣 SheepDungeon 的地牢币，走 {@link SheepDungeonAPI}，不直接碰它的内部账本。</p>
 */
public final class SheepShop extends JavaPlugin {

    /** 房间商店在界面归属里的 id；和 config.yml 里的商店 id 区分开。 */
    static final String ROOM_SHOP_ID = "__room__";

    private ShopConfig shopConfig;
    private ShopGui shopGui;
    private BukkitTask watcher;

    /** 玩家是否已经因为「进入这个商店房」开过界面；离开房间后清掉。 */
    private final Map<UUID, Boolean> inRoom = new HashMap<>();
    private final Map<UUID, BukkitTask> pendingOpen = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        shopConfig = new ShopConfig(this);
        shopGui = new ShopGui(this);

        getServer().getPluginManager().registerEvents(new ShopListener(this), this);

        ShopCommand command = new ShopCommand(this);
        if (getCommand("sheepshop") != null) {
            getCommand("sheepshop").setExecutor(command);
            getCommand("sheepshop").setTabCompleter(command);
        }

        startRoomWatcher();
        getLogger().info("SheepShop 已启用：房间商店 " + (shopConfig.roomShopEnabled() ? "开启" : "关闭")
                + "，常驻商店 " + shopConfig.shopIds().size() + " 个。");
    }

    @Override
    public void onDisable() {
        if (watcher != null) {
            watcher.cancel();
            watcher = null;
        }
        pendingOpen.values().forEach(BukkitTask::cancel);
        pendingOpen.clear();
        inRoom.clear();
    }

    ShopConfig shopConfig() {
        return shopConfig;
    }

    ShopGui shopGui() {
        return shopGui;
    }

    void reloadShop() {
        reloadConfig();
        shopConfig = new ShopConfig(this);
    }

    /**
     * 每秒检查一次：谁刚走进商店房。
     *
     * <p>用轮询而不是事件，因为「进入房间」不是 Bukkit 事件，
     * 而 SheepDungeon 的房间是纯坐标包围盒（见它的 {@code RoomContentManager}）。</p>
     */
    private void startRoomWatcher() {
        if (watcher != null) {
            watcher.cancel();
        }
        watcher = Bukkit.getScheduler().runTaskTimer(this, this::checkRooms, 20L, 20L);
    }

    private void checkRooms() {
        if (!shopConfig.roomShopEnabled()) {
            return;
        }
        if (!SheepDungeonAPI.available()) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            boolean inside = SheepDungeonAPI.inShopRoom(player);
            boolean was = inRoom.getOrDefault(id, false);

            if (inside && !was) {
                inRoom.put(id, true);
                scheduleOpen(player);
            } else if (!inside && was) {
                inRoom.put(id, false);
                BukkitTask task = pendingOpen.remove(id);
                if (task != null) {
                    task.cancel();
                }
            }
        }
    }

    private void scheduleOpen(Player player) {
        long delay = shopConfig.roomShopOpenDelay();
        if (delay <= 0L) {
            shopGui.openRoomShop(player);
            return;
        }
        BukkitTask old = pendingOpen.remove(player.getUniqueId());
        if (old != null) {
            old.cancel();
        }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
            pendingOpen.remove(player.getUniqueId());
            if (!player.isOnline()) {
                return;
            }
            // 延迟期间可能已经走开了，再确认一次
            if (!SheepDungeonAPI.inShopRoom(player)) {
                return;
            }
            shopGui.openRoomShop(player);
        }, delay);
        pendingOpen.put(player.getUniqueId(), task);
    }
}
