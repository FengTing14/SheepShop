package com.sheepshop;

import io.github.sheepdungeon.api.SheepDungeonAPI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /shop —— 一个命令开一个界面，买卖都在里面。
 *
 * <p>早期版本是 {@code /shop open <id>} 买东西、{@code /shop sell} 卖东西，
 * 玩家要记住开哪个，太繁琐。现在不带参数直接开界面，买卖在同一窗口完成。</p>
 */
final class ShopCommand implements CommandExecutor, TabCompleter {

    private final SheepShop plugin;

    ShopCommand(SheepShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return openAuto(sender);
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "open" -> {
                return open(sender, args);
            }
            case "reload" -> {
                if (!sender.hasPermission("sheepshop.admin")) {
                    sender.sendMessage("§c没有权限。");
                    return true;
                }
                plugin.reloadShop();
                sender.sendMessage(plugin.shopConfig().message("reloaded"));
                return true;
            }
            case "coins" -> {
                return coins(sender, args);
            }
            case "list" -> {
                sender.sendMessage("§6[SheepShop] §7常驻商店：§f"
                        + String.join("§7, §f", plugin.shopConfig().shopIds()));
                return true;
            }
            case "help" -> {
                help(sender, label);
                return true;
            }
            default -> {
                // 直接写商店 id 也认：/shop supply
                ShopDefinition shop = plugin.shopConfig().shop(args[0]);
                if (shop != null) {
                    return openShop(sender, shop);
                }
                help(sender, label);
                return true;
            }
        }
    }

    /** 不带参数：在商店房就开房间商店，否则开第一个常驻商店。 */
    private boolean openAuto(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.shopConfig().message("players-only"));
            return true;
        }
        if (!player.hasPermission("sheepshop.use")) {
            player.sendMessage("§c没有权限。");
            return true;
        }
        if (!plugin.shopGui().openAuto(player)) {
            player.sendMessage("§c这里没有可用的商店，试试 §f/shop list §c看看有哪些。");
        }
        return true;
    }

    private boolean open(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.shopConfig().message("players-only"));
            return true;
        }
        if (!player.hasPermission("sheepshop.use")) {
            player.sendMessage("§c没有权限。");
            return true;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("room")) {
            if (!plugin.shopGui().openRoomShop(player)) {
                player.sendMessage("§c你当前不在商店房里，或这个房间没配商品。");
            }
            return true;
        }

        String id = args.length >= 2 ? args[1] : firstShopId();
        ShopDefinition shop = plugin.shopConfig().shop(id);
        if (shop == null) {
            player.sendMessage("§c商店不存在：§f" + id);
            return true;
        }
        return openShop(sender, shop);
    }

    private boolean openShop(CommandSender sender, ShopDefinition shop) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.shopConfig().message("players-only"));
            return true;
        }
        if (!player.hasPermission("sheepshop.use")) {
            player.sendMessage("§c没有权限。");
            return true;
        }
        plugin.shopGui().open(player, shop);
        return true;
    }

    private String firstShopId() {
        List<String> ids = plugin.shopConfig().shopIds();
        return ids.isEmpty() ? null : ids.get(0);
    }

    private boolean coins(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§c玩家不在线：§f" + args[1]);
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage("§c控制台请指定玩家名：/shop coins <玩家>");
            return true;
        }
        sender.sendMessage("§e" + target.getName() + " §7的地牢币：§e" + SheepDungeonAPI.coins(target));
        return true;
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage("§6[SheepShop] §7命令：");
        sender.sendMessage("§7/" + label + " §f- 打开商店（买卖同一个界面）");
        sender.sendMessage("§7/" + label + " open [商店id] §f- 打开指定商店");
        sender.sendMessage("§7/" + label + " open room §f- 打开当前商店房的商店");
        sender.sendMessage("§7/" + label + " coins [玩家] §f- 查看地牢币");
        sender.sendMessage("§7/" + label + " list §f- 列出常驻商店");
        sender.sendMessage("§7/" + label + " reload §f- 重载配置");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("open", "reload", "coins", "list", "help"));
            options.addAll(plugin.shopConfig().shopIds());
            for (String option : options) {
                if (option.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(option);
                }
            }
            return result;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("open")) {
            result.add("room");
            result.addAll(plugin.shopConfig().shopIds());
            return result;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("coins")) {
            plugin.getServer().getOnlinePlayers().forEach(player -> result.add(player.getName()));
            return result;
        }
        return List.of();
    }
}
