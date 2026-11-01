package com.sheepshop;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * 界面事件。
 *
 * <p>买货界面：一律取消，只对左键点商品格子结算。
 * 出售界面：暂存区允许自由放入取出，其它格子取消。</p>
 */
final class ShopListener implements Listener {

    private final SheepShop plugin;

    ShopListener(SheepShop plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof SellGui.Holder sellHolder) {
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (plugin.sellGui().handleClick(player, sellHolder, event)) {
                event.setCancelled(true);
            }
            return;
        }

        if (!(holder instanceof ShopGui.Holder shopHolder)) {
            return;
        }
        // 商店界面里不允许拿走任何东西，也不允许把自己的物品塞进去
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // 点的是玩家自己的背包格子时，只取消、不结算
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }
        if (event.getClick().isShiftClick() || event.isRightClick()) {
            return;
        }
        plugin.shopGui().handleClick(player, shopHolder, event.getRawSlot());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof ShopGui.Holder) {
            event.setCancelled(true);
            return;
        }
        if (holder instanceof SellGui.Holder sellHolder) {
            // 拖拽允许，但要保证整次拖拽都落在暂存区里，
            // 不能拖到功能区（按钮格）上，否则会把玻璃板/按钮搞乱
            for (int slot : event.getRawSlots()) {
                if (slot >= SellGui.STAGING_SIZE) {
                    event.setCancelled(true);
                    return;
                }
            }
            // 拖完刷新一次金额
            if (event.getWhoClicked() instanceof Player player) {
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> plugin.sellGui().refreshIfOpen(player, sellHolder));
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof SellGui.Holder sellHolder
                && event.getPlayer() instanceof Player player) {
            plugin.sellGui().onClose(player, sellHolder);
        }
    }
}
