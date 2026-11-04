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

    String roomShopTitle() {
        return plugin.getConfig().getString("room-shop-title", "§8商店 §7· 地牢币 §e%coins% §7· §e%rotate%§7后换货");
    }

    /** 在商店房卖东西的价格倍率；1.0 表示无加成。 */
    double sellShopRoomMultiplier() {
        return Math.max(1.0, plugin.getConfig().getDouble("sell-shop-room-multiplier", 1.5));
    }

    String sellBonusSuffix() {
        return plugin.getConfig().getString("sell-bonus-suffix", " §a↑加成");
    }

    /** 界面标题（买卖共用）。%coins% 余额，%total% 合计卖价，%bonus% 加成标记。 */
    String shopTitle() {
        return plugin.getConfig().getString("title",
                "§8商店 §7· 余额 §e%coins% §7· 合计 §e%total%%bonus%");
    }

    /**
     * 提示文本。
     *
     * <p>老配置缺少新加的 key 时，直接返回「缺少提示文本：xxx」对玩家毫无意义。
     * 这里内置一份默认值兜底，保证任何情况下都能给出正常的中文提示。</p>
     */
    String message(String key) {
        String configured = plugin.getConfig().getString("messages." + key);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String fallback = DEFAULTS.get(key);
        return fallback != null ? fallback : "§c" + key;
    }

    /** 内置默认提示，与 config.yml 的 messages 段保持一致。 */
    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("no-coins", "§c地牢币不足，还差 §e%need%§c。"),
            Map.entry("bought", "§a已购买 §f%item% §7×%amount%§a，花费 §e%price%§a 地牢币。"),
            Map.entry("sold-out", "§c这件商品已经卖完了。"),
            Map.entry("unknown-item", "§c商品配置有误：找不到物品 §f%id%§c。"),
            Map.entry("inventory-full", "§e背包满了，物品掉在你脚下。"),
            Map.entry("reloaded", "§a配置已重新加载。"),
            Map.entry("players-only", "§c此命令只能由玩家执行。"),
            Map.entry("sell-nothing", "§c暂存区里没有能卖的东西。"),
            Map.entry("sold", "§a卖出 §f%count% §a件，获得 §e%total% §a地牢币。"),
            Map.entry("sell-returned", "§7已把暂存区的东西放回背包。")
    );

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
        return new ShopDefinition(key, section.getString("title", shopTitle()), readProducts(section));
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
