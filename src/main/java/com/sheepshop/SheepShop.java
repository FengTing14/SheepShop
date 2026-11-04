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
 * <p>一个命令 {@code /shop} 开一个界面，买和卖都在同一个窗口里完成。</p>
 *
 * <p>两种商品来源：</p>
 * <ul>
 *   <li><b>房间商店</b>：站在 SheepDungeon 的 SHOP 房间里，商品由那个房间的配置决定，
 *       卖东西还有价格加成；</li>
 *   <li><b>常驻商店</b>：商品写在 config.yml 的 {@code shops} 段。</li>
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

    /** 玩家上一次是否在商店房里，用于检测「刚走进去」这个时刻。 */
    private final Map<UUID, Boolean> inRoom = new HashMap<>();

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
     *
     * <p><b>不再自动打开界面</b>：房间里的悬浮字会告诉玩家输入 {@code /shop}，
     * 让玩家自己决定什么时候开。自动弹窗在黑店里逼玩家点东西，体验很差。</p>
     */
    private void startRoomWatcher() {
        if (watcher != null) {
            watcher.cancel();
        }
        watcher = Bukkit.getScheduler().runTaskTimer(this, this::checkRooms, 20L, 20L);
    }

    private void checkRooms() {
        if (!SheepDungeonAPI.available()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            boolean inside = SheepDungeonAPI.inShopRoom(player);
            boolean was = inRoom.getOrDefault(id, false);
            inRoom.put(id, inside);
            if (inside && !was) {
                onEnterShopRoom(player);
            }
            // 商店界面开着时，每秒刷新标题里的「换货倒计时」
            shopGui.tickRotation(player);
        }
    }

    /** 进入商店房：只在界面上提示怎么开，不替玩家打开。 */
    private void onEnterShopRoom(Player player) {
        long coins = SheepDungeonAPI.coins(player);
        player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection()
                .deserialize("§e按 §f/shop §e打开商店 §7· 地牢币 §e" + coins));
    }
}
