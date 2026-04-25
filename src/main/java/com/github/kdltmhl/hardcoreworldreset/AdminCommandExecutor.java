package com.github.kdltmhl.hardcoreworldreset;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

/**
 * Handles the {@code /hwr} admin command for HardcoreWorldReset.
 *
 * <p>Sub-commands:
 * <ul>
 *   <li>{@code /hwr reload} — reload config.yml at runtime</li>
 *   <li>{@code /hwr status} — display current plugin state</li>
 *   <li>{@code /hwr reset}  — manually trigger a world reset (with confirmation)</li>
 * </ul>
 *
 * <p>Requires the {@code hardcoreworldreset.admin} permission.
 */
public class AdminCommandExecutor implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS = List.of("reload", "status", "reset");

    private final HardcoreWorldReset plugin;

    public AdminCommandExecutor(HardcoreWorldReset plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!sender.hasPermission("hardcoreworldreset.admin")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            case "reset"  -> handleReset(sender);
            default       -> sendHelp(sender);
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        plugin.getConfigManager().reload();
        sender.sendMessage(Component.text("[HWR] ", NamedTextColor.GOLD)
                .append(Component.text("Configuration reloaded.", NamedTextColor.GREEN)));
    }

    private void handleStatus(CommandSender sender) {
        boolean swapping = plugin.isSwapping();
        String activeWorld = plugin.getActiveWorldName();

        sender.sendMessage(Component.text("──── HardcoreWorldReset Status ────", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text(" Active World : ", NamedTextColor.GRAY)
                .append(Component.text(activeWorld, NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text(" Swapping     : ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(swapping),
                        swapping ? NamedTextColor.RED : NamedTextColor.GREEN)));
        sender.sendMessage(Component.text(" Swap Method  : ", NamedTextColor.GRAY)
                .append(Component.text(
                        plugin.getConfigManager().getSwapMethod().name(), NamedTextColor.AQUA)));
        sender.sendMessage(Component.text(" End Goal     : ", NamedTextColor.GRAY)
                .append(Component.text(
                        plugin.getConfigManager().getEndGoal().name(), NamedTextColor.AQUA)));
        sender.sendMessage(Component.text("───────────────────────────────────", NamedTextColor.GOLD));
    }

    private void handleReset(CommandSender sender) {
        if (plugin.isSwapping()) {
            sender.sendMessage(Component.text("[HWR] ", NamedTextColor.GOLD)
                    .append(Component.text("A world reset is already in progress.", NamedTextColor.RED)));
            return;
        }
        sender.sendMessage(Component.text("[HWR] ", NamedTextColor.GOLD)
                .append(Component.text("Manually triggering world reset...", NamedTextColor.YELLOW)));
        plugin.triggerWorldSwap(null, null);
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("──── HardcoreWorldReset Commands ────", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text(" /hwr reload", NamedTextColor.YELLOW)
                .append(Component.text(" — Reload config.yml", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text(" /hwr status", NamedTextColor.YELLOW)
                .append(Component.text(" — Show plugin status", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text(" /hwr reset", NamedTextColor.YELLOW)
                .append(Component.text(" — Manually trigger a world reset", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("─────────────────────────────────────", NamedTextColor.GOLD));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (!sender.hasPermission("hardcoreworldreset.admin")) return List.of();
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            return SUB_COMMANDS.stream()
                    .filter(s -> s.startsWith(partial))
                    .toList();
        }
        return List.of();
    }
}
