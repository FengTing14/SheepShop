package com.sheepshop;

import io.github.sheepdungeon.api.SheepDungeonAPI;
import io.github.sheepdungeon.api.ShopOffer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一商店界面 —— 买和卖在同一个窗口里。
 *
 * <p>早期版本把「买东西」和「卖东西」拆成两个界面两个命令（{@code /shop open room} 和
 * {@code /shop sell}），玩家得记住开哪个，太啰嗦。现在合成一个：</p>
 *
 * <pre>
 *   6 行界面，左边卖货、右边收货
 *
 *   列:  0 1 2 | 3 | 4 5 6 | 7 | 8
 *   行0-3: 商品区（左 3 列）      | 暂存区（右 3 列）
 *   行4:   商品区继续              | 暂存区继续
 *   行5:   [ ] [确认出售] [ ] | [ ] [合计] [全部取回] [ ]
 * </pre>
 *
 * <p>点左边商品 = 买，把东西丢进右边暂存区 = 准备卖，点「确认出售」结算。</p>
 */
final class ShopGui {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /** 界面总行数 */
    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;

    /** 商品区列（0,1,2）与暂存区列（4,5,6） */
    private static final int[] PRODUCT_COLUMNS = {0, 1, 2};
    private static final int[] STAGING_COLUMNS = {4, 5, 6};
    /** 商品区可用行数（留最后一行放按钮） */
    private static final int CONTENT_ROWS = 5;

    static final int CONFIRM_SLOT = 45;
    static final int TOTAL_SLOT = 49;
    static final int RETURN_SLOT = 53;

    /** 界面归属标记。 */
    static final class Holder implements InventoryHolder {
        private final String shopId;
        private final boolean shopRoom;
        /** 标题模板（已确定用商店自己的还是全局的），刷新时复用。 */
        private String titleTemplate;
        /** 商品格子 → 商品 */
        private final Map<Integer, Product> products = new HashMap<>();
        private Inventory inventory;

        Holder(String shopId, boolean shopRoom) {
            this.shopId = shopId;
            this.shopRoom = shopRoom;
        }

        String shopId() {
            return shopId;
        }

        boolean shopRoom() {
            return shopRoom;
        }

        String titleTemplate() {
            return titleTemplate;
        }

        void titleTemplate(String template) {
            this.titleTemplate = template;
        }

        Product productAt(int slot) {
            return products.get(slot);
        }

        void bind(int slot, Product product) {
            products.put(slot, product);
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

    ShopGui(SheepShop plugin) {
        this.plugin = plugin;
    }

    // =================================================================
    // 打开
    // =================================================================

    /** 打开一个常驻商店。 */
    void open(Player player, ShopDefinition shop) {
        if (shop == null) {
            return;
        }
        List<Product> products = shop.products().isEmpty()
                ? List.of(new Product("STONE", 1, 1L, "§7配置为空"))
                : shop.products();
        render(player, shop.id(), shop.title(), products, false);
    }

    /** 打开玩家所在商店房的商店；不在商店房返回 false。 */
    boolean openRoomShop(Player player) {
        List<ShopOffer> offers = SheepDungeonAPI.shopProducts(player);
        if (offers.isEmpty()) {
            return false;
        }
        List<Product> products = new ArrayList<>(offers.size());
        for (ShopOffer offer : offers) {
            long price = Math.round(offer.getPrice());
            products.add(new Product(offer.getItemId(), 1, Math.max(1L, price), null));
        }
        render(player, SheepShop.ROOM_SHOP_ID, plugin.shopConfig().roomShopTitle(), products, true);
        return true;
    }

    /**
     * 不分买卖：自动判断开哪个商店。
     *
     * <p>在商店房就开房间商店（有卖价加成），否则开常驻商店。
     * 这是 {@code /shop} 不带参数时的行为。</p>
     */
    boolean openAuto(Player player) {
        if (SheepDungeonAPI.inShopRoom(player) && openRoomShop(player)) {
            return true;
        }
        String firstId = plugin.shopConfig().shopIds().isEmpty()
                ? null : plugin.shopConfig().shopIds().get(0);
        ShopDefinition shop = plugin.shopConfig().shop(firstId);
        if (shop == null) {
            return false;
        }
        open(player, shop);
        return true;
    }

    // =================================================================
    // 渲染
    // =================================================================

    /**
     * 渲染界面。
     *
     * @param rawTitle 商店自己的标题模板；为空时用 config.yml 的 title。
     *                 占位符 %coins% / %total% / %bonus% 在这里统一替换。
     */
    private void render(Player player, String shopId, String rawTitle, List<Product> products, boolean shopRoom) {
        Holder holder = new Holder(shopId, shopRoom);
        long coins = SheepDungeonAPI.coins(player);
        long total = 0L;
        String template = rawTitle == null || rawTitle.isBlank()
                ? plugin.shopConfig().shopTitle() : rawTitle;
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
                LEGACY.deserialize(fill(template, coins, total, shopRoom, shopRoom ? rotateText(player) : "")));
        holder.inventory(inventory);
        holder.titleTemplate(template);

        fillDivider(inventory);
        paintProducts(inventory, holder, products, coins);
        paintControls(inventory, player, total, shopRoom);

        // 暂存区清空（新界面天然为空）
        player.openInventory(inventory);
    }

    /** 商品区与暂存区之间的分割列，以及非功能区格子。 */
    private void fillDivider(Inventory inventory) {
        ItemStack divider = icon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        // 第 3 列和第 7 列做分隔
        for (int row = 0; row < CONTENT_ROWS; row++) {
            inventory.setItem(row * 9 + 3, divider);
            inventory.setItem(row * 9 + 7, divider);
        }
        // 最后一行除按钮外的空位
        for (int col = 0; col < 9; col++) {
            int slot = (ROWS - 1) * 9 + col;
            if (slot != CONFIRM_SLOT && slot != TOTAL_SLOT && slot != RETURN_SLOT) {
                inventory.setItem(slot, divider);
            }
        }
        // 商品区/暂存区为空时的底色也铺一层，避免玩家往空槽乱放
        ItemStack empty = icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int row = 0; row < CONTENT_ROWS; row++) {
            for (int col : PRODUCT_COLUMNS) {
                inventory.setItem(row * 9 + col, empty);
            }
            for (int col : STAGING_COLUMNS) {
                inventory.setItem(row * 9 + col, null);
            }
        }
    }

    private void paintProducts(Inventory inventory, Holder holder, List<Product> products, long coins) {
        int index = 0;
        for (int row = 0; row < CONTENT_ROWS; row++) {
            for (int col : PRODUCT_COLUMNS) {
                if (index >= products.size()) {
                    return;
                }
                Product product = products.get(index++);
                int slot = row * 9 + col;
                inventory.setItem(slot, buildIcon(product, coins));
                holder.bind(slot, product);
            }
        }
        int shown = CONTENT_ROWS * PRODUCT_COLUMNS.length;
        if (products.size() > shown) {
            plugin.getLogger().warning("商店商品 " + products.size() + " 件，界面只能放 " + shown + " 件，其余未显示。");
        }
    }

    private void paintControls(Inventory inventory, Player player, long total, boolean shopRoom) {
        long coins = SheepDungeonAPI.coins(player);

        inventory.setItem(CONFIRM_SLOT, icon(Material.EMERALD_BLOCK, "§a§l确认出售", List.of(
                LEGACY.deserialize("§7合计：§e" + total + " §7地牢币"),
                shopRoom ? LEGACY.deserialize("§a商店房加成已生效") : LEGACY.deserialize("§7把要卖的东西放进右边格子"),
                LEGACY.deserialize("§7点击卖出")
        )));

        inventory.setItem(TOTAL_SLOT, icon(Material.GOLD_INGOT, "§e合计 §6" + total + " §e地牢币", List.of(
                LEGACY.deserialize("§7当前余额：§e" + coins),
                LEGACY.deserialize("§7左边点商品购买，右边放物品出售")
        )));

        inventory.setItem(RETURN_SLOT, icon(Material.CHEST, "§e全部取回", List.of(
                LEGACY.deserialize("§7把右边的东西放回背包")
        )));
    }

    private ItemStack buildIcon(Product product, long coins) {
        ItemStack item = SheepDungeonAPI.buildItem(product.itemId(), product.amount());
        if (item == null) {
            item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(LEGACY.deserialize("§c" + product.display()));
                meta.lore(List.of(LEGACY.deserialize(
                        plugin.shopConfig().message("unknown-item").replace("%id%", product.itemId()))));
                item.setItemMeta(meta);
            }
            return item;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (product.display() != null && !product.display().equals(product.itemId())) {
                meta.displayName(LEGACY.deserialize(product.display()));
            }
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            boolean affordable = coins >= product.price();
            lore.add(LEGACY.deserialize("§7价格：§e" + product.price() + " §7地牢币"));
            if (product.amount() > 1) {
                lore.add(LEGACY.deserialize("§7数量：§f" + product.amount()));
            }
            lore.add(LEGACY.deserialize(affordable ? "§a点击购买" : "§c地牢币不足"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** 标题占位符替换。买卖共用，所以同时有余额和合计。 */
    private String fill(String template, long coins, long total, boolean shopRoom, String rotate) {
        String suffix = shopRoom ? plugin.shopConfig().sellBonusSuffix() : "";
        return template
                .replace("%coins%", String.valueOf(coins))
                .replace("%total%", String.valueOf(total))
                .replace("%bonus%", suffix)
                .replace("%rotate%", rotate == null ? "" : rotate);
    }

    /** 「X分Y秒 / Y秒」形式的换货倒计时；不在商店房返回空串。 */
    String rotateText(Player player) {
        long sec = SheepDungeonAPI.shopRotateSeconds(player);
        if (sec < 0L) {
            return "";
        }
        long m = sec / 60L;
        long s = sec % 60L;
        return m > 0L ? (m + "分" + s + "秒") : (s + "秒");
    }

    /** 每秒刷新一次：商店房界面开着时，更新标题里的换货倒计时。 */
    void tickRotation(Player player) {
        org.bukkit.inventory.InventoryView view = player.getOpenInventory();
        if (!(view.getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        if (!holder.shopRoom()) {
            return;
        }
        refresh(player, holder);
    }

    private ItemStack icon(Material material, String name, List<net.kyori.adventure.text.Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LEGACY.deserialize(name));
            List<net.kyori.adventure.text.Component> clean = new ArrayList<>();
            for (net.kyori.adventure.text.Component line : lore) {
                if (!LEGACY.serialize(line).isEmpty()) {
                    clean.add(line);
                }
            }
            meta.lore(clean);
            item.setItemMeta(meta);
        }
        return item;
    }

    // =================================================================
    // 交互
    // =================================================================

    /** 是不是暂存区格子（右三列）。 */
    static boolean isStagingSlot(int slot) {
        if (slot >= SIZE) {
            return false;
        }
        int row = slot / 9;
        int col = slot % 9;
        if (row >= CONTENT_ROWS) {
            return false;
        }
        for (int c : STAGING_COLUMNS) {
            if (c == col) {
                return true;
            }
        }
        return false;
    }

    /** 是不是商品格子（左三列）。 */
    static boolean isProductSlot(int slot) {
        if (slot >= SIZE) {
            return false;
        }
        int row = slot / 9;
        int col = slot % 9;
        if (row >= CONTENT_ROWS) {
            return false;
        }
        for (int c : PRODUCT_COLUMNS) {
            if (c == col) {
                return true;
            }
        }
        return false;
    }

    /** 算暂存区合计。 */
    long totalOf(Inventory inventory, Holder holder) {
        long total = 0L;
        double multiplier = holder.shopRoom() ? plugin.shopConfig().sellShopRoomMultiplier() : 1.0;
        for (int slot = 0; slot < SIZE; slot++) {
            if (!isStagingSlot(slot)) {
                continue;
            }
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            total += Math.round(SheepDungeonAPI.stackPriceOf(item) * multiplier);
        }
        return total;
    }

    /** 刷新标题与底部金额（不重建界面）。 */
    void refresh(Player player, Holder holder) {
        Inventory inventory = holder.getInventory();
        long total = totalOf(inventory, holder);
        long coins = SheepDungeonAPI.coins(player);

        org.bukkit.inventory.InventoryView view = player.getOpenInventory();
        if (view.getTopInventory() == inventory) {
            try {
                String template = holder.titleTemplate() == null
                        ? plugin.shopConfig().shopTitle() : holder.titleTemplate();
                view.setTitle(LEGACY.serialize(LEGACY.deserialize(
                        fill(template, coins, total, holder.shopRoom(),
                                holder.shopRoom() ? rotateText(player) : ""))));
            } catch (Throwable ignored) {
                // 服务端不支持运行时改标题时忽略，金额还有底部按钮兜底
            }
        }

        // 只更新两个金额格子，不动商品和暂存区
        inventory.setItem(CONFIRM_SLOT, icon(Material.EMERALD_BLOCK, "§a§l确认出售", List.of(
                LEGACY.deserialize("§7合计：§e" + total + " §7地牢币"),
                holder.shopRoom() ? LEGACY.deserialize("§a商店房加成已生效") : LEGACY.deserialize("§7把要卖的东西放进右边格子"),
                LEGACY.deserialize("§7点击卖出")
        )));
        inventory.setItem(TOTAL_SLOT, icon(Material.GOLD_INGOT, "§e合计 §6" + total + " §e地牢币", List.of(
                LEGACY.deserialize("§7当前余额：§e" + coins),
                LEGACY.deserialize("§7左边买，右边卖")
        )));
    }

    /** 买东西。 */
    boolean buy(Player player, Holder holder, int slot) {
        Product product = holder.productAt(slot);
        if (product == null) {
            return false;
        }

        ItemStack probe = SheepDungeonAPI.buildItem(product.itemId(), product.amount());
        if (probe == null) {
            player.sendMessage(plugin.shopConfig().message("unknown-item").replace("%id%", product.itemId()));
            return true;
        }

        long coins = SheepDungeonAPI.coins(player);
        if (coins < product.price()) {
            player.sendMessage(plugin.shopConfig().message("no-coins")
                    .replace("%need%", String.valueOf(product.price() - coins)));
            return true;
        }

        if (!SheepDungeonAPI.takeCoins(player, product.price())) {
            player.sendMessage(plugin.shopConfig().message("no-coins")
                    .replace("%need%", String.valueOf(product.price() - coins)));
            return true;
        }
        SheepDungeonAPI.saveCoins();

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(probe);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            player.sendMessage(plugin.shopConfig().message("inventory-full"));
        }

        player.sendMessage(plugin.shopConfig().message("bought")
                .replace("%item%", product.display())
                .replace("%amount%", String.valueOf(product.amount()))
                .replace("%price%", String.valueOf(product.price())));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);

        // 余额变了，商品图标的「买得起/买不起」要跟着变
        long nowCoins = SheepDungeonAPI.coins(player);
        Inventory inventory = holder.getInventory();
        for (Map.Entry<Integer, Product> entry : holder.products.entrySet()) {
            inventory.setItem(entry.getKey(), buildIcon(entry.getValue(), nowCoins));
        }
        refresh(player, holder);
        return true;
    }

    /** 结算暂存区。 */
    void confirm(Player player, Holder holder) {
        Inventory inventory = holder.getInventory();
        long total = 0L;
        int count = 0;
        double multiplier = holder.shopRoom() ? plugin.shopConfig().sellShopRoomMultiplier() : 1.0;

        for (int slot = 0; slot < SIZE; slot++) {
            if (!isStagingSlot(slot)) {
                continue;
            }
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            long each = Math.round(SheepDungeonAPI.stackPriceOf(item) * multiplier);
            if (each <= 0L) {
                // 卖不掉的（石头、武器等）原样退回，不吞
                player.getInventory().addItem(item).values()
                        .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
                inventory.setItem(slot, null);
                continue;
            }
            total += each;
            count += item.getAmount();
            inventory.setItem(slot, null);
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

        refresh(player, holder);
    }

    /** 把暂存区退回背包。 */
    void returnAll(Player player, Holder holder) {
        returnAll(player, holder, true);
    }

    void returnAll(Player player, Holder holder, boolean notify) {
        Inventory inventory = holder.getInventory();
        boolean any = false;
        for (int slot = 0; slot < SIZE; slot++) {
            if (!isStagingSlot(slot)) {
                continue;
            }
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            player.getInventory().addItem(item).values()
                    .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            inventory.setItem(slot, null);
            any = true;
        }
        if (notify && any) {
            player.sendMessage(plugin.shopConfig().message("sell-returned"));
        }
    }
}
