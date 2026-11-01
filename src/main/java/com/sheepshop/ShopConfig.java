package com.sheepshop;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 一件商品。
 *
 * <p>价格用 {@code long} 的地牢币，因为地牢币本身是整数账本；
 * 配置里允许写小数，读入时四舍五入到整数，避免出现 0.5 币这种买不了也找不开的情况。</p>
 */
final class Product {

    private final String itemId;
    private final int amount;
    private final long price;
    private final String display;

    Product(String itemId, int amount, long price, String display) {
        this.itemId = itemId;
        this.amount = Math.max(1, amount);
        this.price = Math.max(1L, price);
        this.display = display;
    }

    String itemId() {
        return itemId;
    }

    int amount() {
        return amount;
    }

    long price() {
        return price;
    }

    /** 界面里的显示名；没配就用物品 ID。 */
    String display() {
        return display == null || display.isEmpty() ? itemId : display;
    }
}

/** 一个商店：一组商品 + 标题。 */
final class ShopDefinition {

    private final String id;
    private final String title;
    private final List<Product> products;

    ShopDefinition(String id, String title, List<Product> products) {
        this.id = id;
        this.title = title;
        this.products = List.copyOf(products);
    }

    String id() {
        return id;
    }

    String title() {
        return title;
    }

    List<Product> products() {
        return products;
    }
}

/** 读 config.yml。 */
final class ShopConfig {

    private final SheepShop plugin;

    ShopConfig(SheepShop plugin) {
        this.plugin = plugin;
    }

    boolean roomShopEnabled() {
        return plugin.getConfig().getBoolean("room-shop-enabled", true);
    }

    long roomShopOpenDelay() {
        return Math.max(0L, plugin.getConfig().getLong("room-shop-open-delay", 10L));
    }

    String roomShopTitle() {
        return plugin.getConfig().getString("room-shop-title", "§8商店 §7· 地牢币 §e%coins%");
    }

    String title() {
        return plugin.getConfig().getString("title", "§8商店");
    }

    int rows() {
        int rows = plugin.getConfig().getInt("rows", 3);
        return Math.min(6, Math.max(1, rows));
    }

    String message(String key) {
        return plugin.getConfig().getString("messages." + key, "§c缺少提示文本: " + key);
    }

    /** 常驻商店；不存在返回 null。 */
    ShopDefinition shop(String id) {
        if (id == null) {
            return null;
        }
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("shops");
        if (root == null) {
            return null;
        }
        String key = resolveKey(root, id);
        if (key == null) {
            return null;
        }
        ConfigurationSection section = root.getConfigurationSection(key);
        if (section == null) {
            return null;
        }
        return new ShopDefinition(key, section.getString("title", title()), readProducts(section));
    }

    /** 所有常驻商店的 id。 */
    List<String> shopIds() {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("shops");
        if (root == null) {
            return List.of();
        }
        return new ArrayList<>(root.getKeys(false));
    }

    /** 读 {@code products} 列表；坏条目跳过并告警，不让一条错误配置拖垮整个商店。 */
    List<Product> readProducts(ConfigurationSection section) {
        List<Product> result = new ArrayList<>();
        List<Map<?, ?>> raw = section.getMapList("products");
        int index = 0;
        for (Map<?, ?> map : raw) {
            index++;
            Object itemId = map.get("item-id");
            if (itemId == null || itemId.toString().isBlank()) {
                plugin.getLogger().warning("第 " + index + " 件商品缺少 item-id，已跳过。");
                continue;
            }
            int amount = asInt(map.get("amount"), 1);
            long price = Math.round(asDouble(map.get("price"), 0.0));
            if (price <= 0L) {
                plugin.getLogger().warning("商品 " + itemId + " 的 price 必须大于 0，已跳过。");
                continue;
            }
            Object display = map.get("display");
            result.add(new Product(itemId.toString(), amount, price, display == null ? null : display.toString()));
        }
        return result;
    }

    private String resolveKey(ConfigurationSection root, String id) {
        String wanted = id.toLowerCase(Locale.ROOT);
        for (String key : root.getKeys(false)) {
            if (key.toLowerCase(Locale.ROOT).equals(wanted)) {
                return key;
            }
        }
        return null;
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double asDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.toString().trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /** 商品为空时的兜底列表，避免开出一个空界面。 */
    Map<String, Product> placeholder() {
        Map<String, Product> map = new LinkedHashMap<>();
        map.put("placeholder", new Product("STONE", 1, 1L, "§7配置为空"));
        return map;
    }

    FileConfiguration raw() {
        return plugin.getConfig();
    }
}
