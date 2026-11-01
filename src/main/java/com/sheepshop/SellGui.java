package com.sheepshop;

import io.github.sheepdungeon.api.SheepDungeonAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 出售界面 —— 仿星露谷的箱子式卖货。
 *
 * <p>布局（6 行）：</p>
 * <pre>
 *   0-26  暂存区：玩家把要卖的东西丢进来
 *   27-44 玩家背包的映射显示（点这里的物品 = 挪进暂存区）
 *   45    确认出售按钮
 *   49    合计金额
 *   53    全部取回
 * </pre>
 *
 * <p><b>关键设计</b>：暂存区里的物品是「真的」放在这个容器里的，
 * 不是镜像玩家背包。玩家关界面时暂存区的东西必须还回去，否则就是吞物品。</p>
 */
final class SellGui {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /** 暂存区格子数（前 3 行） */
    static final int STAGING_SIZE = 27;
    /** 确认出售按钮 */
    static final int CONFIRM_SLOT = 45;
    /** 合计金额展示位 */
    static final int TOTAL_SLOT = 49;
    /** 全部取回按钮 */
    static final int RETURN_SLOT = 53;
    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;

    /** 界面归属标记。 */
    static final class Holder implements InventoryHolder {
        private final boolean shopRoom;
        private Inventory inventory;

        Holder(boolean shopRoom) {
            this.shopRoom = shopRoom;
        }

        /** 是否在商店房打开（价格加成）。 */
        boolean shopRoom() {
            return shopRoom;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    private final SheepShop plugin;

    SellGui(SheepShop plugin) {
        this.plugin = plugin;
    }

    /** 打开出售界面。{@code shopRoom} 决定是否享受商店房的价格加成。 */
    void open(Player player, boolean shopRoom) {
        Holder holder = new Holder(shopRoom);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, LEGACY.deserialize(title(player, 0L, shopRoom)));
        holder.inventory(inventory);

        // 底部按钮先占位，避免玩家把东西放进功能区
        paintControls(inventory, player, 0L, shopRoom);

        player.openInventory(inventory);
    }

    /** 标题实时显示「卖出去能拿多少」。 */
    private String title(Player player, long total, boolean shopRoom) {
        String base = plugin.shopConfig().sellTitle();
        String suffix = shopRoom
                ? plugin.shopConfig().sellBonusSuffix()
                : "";
        return base.replace("%total%", String.valueOf(total))
                .replace("%coins%", String.valueOf(SheepDungeonAPI.coins(player)))
                .replace("%bonus%", suffix);
    }

    /**
     * 刷新金额显示。
     *
     * <p><b>不重建界面</b>：早期版本用「关掉再开一个新界面」来改标题，
     * 结果鼠标拖拽时每移动一格都会触发一次点击 → 界面被反复重建 → 拖拽立刻中断，
     * 表现就是「拖不动」。现在只更新标题（Paper 支持运行时改标题）和底部按钮。</p>
     */
    private void refresh(Player player, Inventory inventory, Holder holder) {
        long total = totalOf(inventory, holder);

        // Paper 支持直接改已打开界面的标题，不用重开
        org.bukkit.inventory.InventoryView view = player.getOpenInventory();
        if (view.getTopInventory() == inventory) {
            runCatchingSetTitle(view, title(player, total, holder.shopRoom()));
        }

        // 底部按钮的金额也要跟着变（只动这两个格子，不影响暂存区）
        paintControls(inventory, player, total, holder.shopRoom());

        player.sendActionBar(LEGACY.deserialize(
                "§7合计：§e" + total + " §7地牢币" + (holder.shopRoom() ? " §a（商店房加成）" : "")));
    }

    private void runCatchingSetTitle(org.bukkit.inventory.InventoryView view, String rawTitle) {
        try {
            view.setTitle(LEGACY.serialize(LEGACY.deserialize(rawTitle)));
        } catch (Throwable ignored) {
            // 服务端不支持运行时改标题时忽略，金额还有 actionbar 和底部按钮兜底
        }
    }

    /** 合计金额：暂存区所有物品按 SheepDungeon 的售价算。 */
    private long totalOf(Inventory inventory, Holder holder) {
        long total = 0L;
        double multiplier = holder.shopRoom() ? plugin.shopConfig().sellShopRoomMultiplier() : 1.0;
        for (int i = 0; i < STAGING_SIZE; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            total += Math.round(SheepDungeonAPI.stackPriceOf(item) * multiplier);
        }
        return total;
    }

    private void paintControls(Inventory inventory, Player player, long total, boolean shopRoom) {
        // 玻璃板分隔暂存区和功能区
        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = STAGING_SIZE; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }

        ItemStack confirm = icon(Material.EMERALD_BLOCK, "§a§l确认出售",
                List.of(
                        LEGACY.deserialize("§7合计：§e" + total + " §7地牢币"),
                        shopRoom ? LEGACY.deserialize("§a商店房加成已生效") : Component.empty(),
                        LEGACY.deserialize("§7点击卖出暂存区的全部物品")
                ));
        inventory.setItem(CONFIRM_SLOT, confirm);

        ItemStack totalIcon = icon(Material.GOLD_INGOT, "§e合计 §6" + total + " §e地牢币",
                List.of(
                        LEGACY.deserialize("§7当前余额：§e" + SheepDungeonAPI.coins(player)),
                        LEGACY.deserialize("§7把要卖的东西放进上面的格子")
                ));
        inventory.setItem(TOTAL_SLOT, totalIcon);

        ItemStack back = icon(Material.CHEST, "§e全部取回",
                List.of(LEGACY.deserialize("§7把暂存区的东西放回背包")));
        inventory.setItem(RETURN_SLOT, back);
    }

    private ItemStack icon(Material material, String name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LEGACY.deserialize(name));
            List<Component> clean = new ArrayList<>();
            for (Component line : lore) {
                if (!LEGACY.serialize(line).isEmpty()) {
                    clean.add(line);
                }
            }
            meta.lore(clean);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 处理点击。
     *
     * @return true 表示取消这次点击（不改变容器内容）
     */
    boolean handleClick(Player player, Holder holder, InventoryClickEvent event) {
        int raw = event.getRawSlot();
        Inventory top = event.getInventory();

        // 功能区：只认两个按钮，其它一律吃掉
        if (raw >= STAGING_SIZE) {
            if (raw == CONFIRM_SLOT) {
                confirm(player, top, holder);
            } else if (raw == RETURN_SLOT) {
                returnAll(player, top);
            }
            return true;
        }

        // 暂存区：放行，让 Bukkit 自己完成移动。
        // shift 点击也放行——它会把整叠移到玩家背包/或从背包自动灌入，
        // 这正是「快速卖一堆」需要的，禁掉反而难用。
        scheduleRefresh(player, holder);
        return false;
    }

    /** 如果玩家正开着这个出售界面，刷新一次金额。 */
    void refreshIfOpen(Player player, Holder holder) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder current
                && current == holder) {
            refresh(player, current.getInventory(), current);
        }
    }

    /** 延迟一 tick 刷新（等 Bukkit 把物品真正放进去了再算钱）。 */
    private void scheduleRefresh(Player player, Holder holder) {
        Bukkit.getScheduler().runTask(plugin, () -> refreshIfOpen(player, holder));
    }

    private void confirm(Player player, Inventory inventory, Holder holder) {
        long total = 0L;
        int count = 0;
        double multiplier = holder.shopRoom() ? plugin.shopConfig().sellShopRoomMultiplier() : 1.0;

        for (int i = 0; i < STAGING_SIZE; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            long each = Math.round(SheepDungeonAPI.stackPriceOf(item) * multiplier);
            if (each <= 0L) {
                // 卖不掉的物品原样退回，不能悄悄吞掉
                player.getInventory().addItem(item).values()
                        .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
                inventory.setItem(i, null);
                continue;
            }
            total += each;
            count += item.getAmount();
            inventory.setItem(i, null);
        }

        if (total <= 0L) {
            player.sendMessage(plugin.shopConfig().message("sell-nothing"));
            return;
        }

        SheepDungeonAPI.addCoins(player, total);
        SheepDungeonAPI.saveCoins();
        player.sendMessage(plugin.shopConfig().message("sold")
                .replace("%count%", String.valueOf(count))
                .replace("%total%", String.valueOf(total)));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);

        refresh(player, inventory, holder);
    }

    /** 把暂存区的东西全部退回背包。 */
    private void returnAll(Player player, Inventory inventory) {
        returnAll(player, inventory, true);
    }

    /**
     * 把暂存区的东西全部退回背包。
     *
     * @param notify 是否发提示；关界面时静默退货，免得每次关都刷一行
     */
    private void returnAll(Player player, Inventory inventory, boolean notify) {
        boolean any = false;
        for (int i = 0; i < STAGING_SIZE; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            player.getInventory().addItem(item).values()
                    .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            inventory.setItem(i, null);
            any = true;
        }
        if (notify && any) {
            player.sendMessage(plugin.shopConfig().message("sell-returned"));
        }
    }

    /** 界面被关掉时，把暂存区的东西还回去（防吞物品）。 */
    void onClose(Player player, Holder holder) {
        returnAll(player, holder.getInventory(), false);
    }

    static String lower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}
