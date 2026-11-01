package com.sheepshop;

import io.github.sheepdungeon.api.SheepDungeonAPI;
import io.github.sheepdungeon.api.ShopOffer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
 * 商店界面。
 *
 * <p>用自定义 {@link Holder} 标记界面归属，比按标题字符串比对可靠：
 * 标题里带余额，玩家买东西后标题会变，靠标题认界面会失效。</p>
 */
final class ShopGui {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /** 界面归属标记，同时保存 slot → 商品 的映射。 */
    static final class Holder implements InventoryHolder {
        private final String shopId;
        private final Map<Integer, Product> slotProducts = new HashMap<>();
        private Inventory inventory;

        Holder(String shopId) {
            this.shopId = shopId;
        }

        String shopId() {
            return shopId;
        }

        Product productAt(int slot) {
            return slotProducts.get(slot);
        }

        void bind(int slot, Product product) {
            slotProducts.put(slot, product);
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

    /** 打开一个常驻商店。 */
    void open(Player player, ShopDefinition shop) {
        if (shop == null) {
            return;
        }
        List<Product> products = shop.products().isEmpty()
                ? List.of(new Product("STONE", 1, 1L, "§7配置为空"))
                : shop.products();
        render(player, shop.id(), shop.title(), products);
    }

    /** 打开玩家当前所在商店房的商品。商品来自 SheepDungeon 的房间配置。 */
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
        render(player, SheepShop.ROOM_SHOP_ID, plugin.shopConfig().roomShopTitle(), products);
        return true;
    }

    private void render(Player player, String shopId, String rawTitle, List<Product> products) {
        int rows = plugin.shopConfig().rows();
        int capacity = rows * 9;
        if (products.size() > capacity) {
            plugin.getLogger().warning(
                    "商店 " + shopId + " 有 " + products.size() + " 件商品，超过 " + rows + " 行能放的 "
                            + capacity + " 件，只显示前面这些。调大 config.yml 的 rows 可显示更多。");
        }

        Holder holder = new Holder(shopId);
        String title = rawTitle.replace("%coins%", String.valueOf(SheepDungeonAPI.coins(player)));
        Inventory inventory = Bukkit.createInventory(holder, capacity, LEGACY.deserialize(title));
        holder.inventory(inventory);

        long coins = SheepDungeonAPI.coins(player);
        int slot = 0;
        for (Product product : products) {
            if (slot >= capacity) {
                break;
            }
            ItemStack icon = buildIcon(product, coins);
            inventory.setItem(slot, icon);
            holder.bind(slot, product);
            slot++;
        }

        player.openInventory(inventory);
    }

    private ItemStack buildIcon(Product product, long coins) {
        ItemStack item = SheepDungeonAPI.buildItem(product.itemId(), product.amount());
        if (item == null) {
            item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(LEGACY.deserialize("§c" + product.display()));
            meta.lore(List.of(LEGACY.deserialize(
                    plugin.shopConfig().message("unknown-item").replace("%id%", product.itemId()))));
            item.setItemMeta(meta);
            return item;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (product.display() != null && !product.display().equals(product.itemId())) {
                meta.displayName(LEGACY.deserialize(product.display()));
            }
            List<Component> lore = new ArrayList<>();
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

    /** 处理一次点击；返回 true 表示已消费该事件。 */
    boolean handleClick(Player player, Holder holder, int slot) {
        Product product = holder.productAt(slot);
        if (product == null) {
            return false;
        }

        ItemStack probe = SheepDungeonAPI.buildItem(product.itemId(), product.amount());
        if (probe == null) {
            player.sendMessage(plugin.shopConfig().message("unknown-item")
                    .replace("%id%", product.itemId()));
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

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(probe);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            player.sendMessage(plugin.shopConfig().message("inventory-full"));
        }

        player.sendMessage(plugin.shopConfig().message("bought")
                .replace("%item%", product.display())
                .replace("%amount%", String.valueOf(product.amount()))
                .replace("%price%", String.valueOf(product.price())));

        // 余额变了，重画一遍让标题和「买得起/买不起」同步
        rerender(player, holder);
        return true;
    }

    private void rerender(Player player, Holder holder) {
        if (SheepShop.ROOM_SHOP_ID.equals(holder.shopId())) {
            openRoomShop(player);
            return;
        }
        ShopDefinition shop = plugin.shopConfig().shop(holder.shopId());
        if (shop != null) {
            open(player, shop);
        }
    }
}
