package com.sheepshop;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/** 界面点击：只允许左键点自己商店里的格子，其它一律取消。 */
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
        if (event.getInventory().getHolder() instanceof ShopGui.Holder) {
            event.setCancelled(true);
        }
    }
}
