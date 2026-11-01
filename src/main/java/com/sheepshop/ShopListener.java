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
 * 统一商店界面的事件。
 *
 * <p>一个界面里同时有买和卖两块区域，规则不同：</p>
 * <ul>
 *   <li>左边商品区：点击 = 买东西，格子内容不能被拿走</li>
 *   <li>右边暂存区：自由放物品，用来卖</li>
 *   <li>底部按钮：确认出售 / 全部取回</li>
 *   <li>其它格子（分隔板）：一律挡住</li>
 * </ul>
 */
final class ShopListener implements Listener {

    private final SheepShop plugin;

    ShopListener(SheepShop plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof ShopGui.Holder shopHolder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }

        int raw = event.getRawSlot();
        boolean clickedTop = event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getInventory());

        // 点的是自己的背包
        if (!clickedTop) {
            // shift 点击可以从背包快速灌进暂存区，其余忽略
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }

        // 底部按钮
        if (raw == ShopGui.CONFIRM_SLOT) {
            event.setCancelled(true);
            plugin.shopGui().confirm(player, shopHolder);
            return;
        }
        if (raw == ShopGui.RETURN_SLOT) {
            event.setCancelled(true);
            plugin.shopGui().returnAll(player, shopHolder);
            return;
        }
        if (raw == ShopGui.TOTAL_SLOT) {
            event.setCancelled(true);
            return;
        }

        // 商品区：买
        if (ShopGui.isProductSlot(raw)) {
            event.setCancelled(true);
            plugin.shopGui().buy(player, shopHolder, raw);
            return;
        }

        // 暂存区：放行，让 Bukkit 自己完成移动
        if (ShopGui.isStagingSlot(raw)) {
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> plugin.shopGui().refresh(player, shopHolder));
            return;
        }

        // 其它（分隔板）：挡住
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof ShopGui.Holder shopHolder)) {
            return;
        }
        // 拖拽只能落在暂存区，商品区和按钮区一概拒绝
        for (int slot : event.getRawSlots()) {
            if (!ShopGui.isStagingSlot(slot)) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getWhoClicked() instanceof Player player) {
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> plugin.shopGui().refresh(player, shopHolder));
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ShopGui.Holder shopHolder
                && event.getPlayer() instanceof Player player) {
            // 关界面把暂存区东西退回去，防吞物品（静默，不刷提示）
            plugin.shopGui().returnAll(player, shopHolder, false);
        }
    }
}
