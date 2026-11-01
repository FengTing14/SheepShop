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

/** /shop open <id> | reload | coins [玩家] | list */
final class ShopCommand implements CommandExecutor, TabCompleter {

    private final SheepShop plugin;

    ShopCommand(SheepShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
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
            case "sell" -> {
                return sell(sender);
            }
            case "coins" -> {
                return coins(sender, args);
            }
            case "list" -> {
                sender.sendMessage("§6[SheepShop] §7常驻商店：§f" + String.join("§7, §f", plugin.shopConfig().shopIds()));
                return true;
            }
            default -> {
                help(sender, label);
                return true;
            }
        }
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
        plugin.shopGui().open(player, shop);
        return true;
    }

    private String firstShopId() {
        List<String> ids = plugin.shopConfig().shopIds();
        return ids.isEmpty() ? null : ids.get(0);
    }

    /**
     * /shop sell —— 打开出售界面。
     *
     * <p>站在商店房里打开会享受价格加成；在地牢里但不是商店房，
     * 只能开普通界面（和外面一样的价），这是「商店房卖得更多」的实现方式。</p>
     */
    private boolean sell(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.shopConfig().message("players-only"));
            return true;
        }
        if (!player.hasPermission("sheepshop.use")) {
            player.sendMessage("§c没有权限。");
            return true;
        }
        boolean shopRoom = io.github.sheepdungeon.api.SheepDungeonAPI.inShopRoom(player);
        plugin.sellGui().open(player, shopRoom);
        return true;
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
        sender.sendMessage("§7/" + label + " open [商店id] §f- 打开常驻商店");
        sender.sendMessage("§6[SheepShop] §7/" + label + " open room §f- 打开当前房间商店");
        sender.sendMessage("§6[SheepShop] §7/" + label + " sell §f- 打出售卖界面（商店房内价格更高）");
        sender.sendMessage("§7/" + label + " coins [玩家] §f- 查看地牢币");
        sender.sendMessage("§7/" + label + " list §f- 列出常驻商店");
        sender.sendMessage("§7/" + label + " reload §f- 重载配置");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            for (String option : List.of("open", "sell", "reload", "coins", "list")) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) {
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
