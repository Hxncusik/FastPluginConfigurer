package kz.hxncus.mc.fastpluginconfigurer.command;

import kz.hxncus.mc.fastpluginconfigurer.FastPluginConfigurer;
import kz.hxncus.mc.fastpluginconfigurer.cache.ConfigCache;
import kz.hxncus.mc.fastpluginconfigurer.config.ConfigMaterial;
import kz.hxncus.mc.fastpluginconfigurer.config.Messages;
import kz.hxncus.mc.fastpluginconfigurer.hook.Convertible;
import kz.hxncus.mc.fastpluginconfigurer.inventory.AbstractInventory;
import kz.hxncus.mc.fastpluginconfigurer.inventory.EmptyInventory;
import kz.hxncus.mc.fastpluginconfigurer.util.*;
import kz.hxncus.mc.fastpluginconfigurer.util.builder.ItemBuilder;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class FPCCommand extends AbstractCommand {
    public FPCCommand(FastPluginConfigurer plugin) {
        super(plugin, "fastpluginconfigurer");
    }

    @Override
    public void execute(CommandSender sender, Command command, String label, String... args) {
        if (!(sender instanceof Player)) {
            Messages.MUST_BE_PLAYER.send(sender);
            return;
        }
        if (args.length < 1) {
            sendHelpMessage(sender, label);
        } else if (Constants.INVENTORY_TO_FILE.equalsIgnoreCase(args[0]) || Constants.FILE_TO_INVENTORY.equalsIgnoreCase(args[0])) {
            inventorySubCommand(sender, label, args);
        } else if (Constants.CONFIG.equalsIgnoreCase(args[0])) {
            configSubCommand((HumanEntity) sender, label, args);
        } else if ("reload".equalsIgnoreCase(args[0])) {
            plugin.reloadConfig();
            plugin.onEnable();
            Messages.CONFIG_SUCCESSFULLY_RELOADED.send(sender);
        } else {
            sendHelpMessage(sender, label);
        }
    }

    private void inventorySubCommand(CommandSender sender, String label, String... args) {
        if (args.length < 3) {
            sendHelpMessage(sender, label);
            return;
        }
        Convertible.Converters converter = Convertible.Converters.valueOfIgnoreCase(args[1]);
        if (converter == null || converter.getConverter() == null) {
            Messages.CONVERTER_TYPE_DOES_NOT_EXIST.send(sender);
            return;
        }
        if (args[0].equalsIgnoreCase(Constants.INVENTORY_TO_FILE)) {
            converter.getConverter().convertInventoryToFile((Player) sender, args[2]);
        } else if (args[0].equalsIgnoreCase(Constants.FILE_TO_INVENTORY)) {
            converter.getConverter().convertFileToInventory((Player) sender, args[2]);
        }
    }

    public void configSubCommand(HumanEntity humanEntity, String label, String... args) {
        if (args.length < 2) {
            sendHelpMessage(humanEntity, label);
            return;
        }
        Plugin targetPlugin = Bukkit.getPluginManager().getPlugin(args[1]);
        if (targetPlugin == null || !targetPlugin.isEnabled()) {
            Messages.PLUGIN_DOES_NOT_EXIST.send(humanEntity);
            return;
        }
        File file;
        if (args.length < 3) {
            file = new File(targetPlugin.getDataFolder(), "settings.yml");
        } else {
            file = new File(targetPlugin.getDataFolder(), args[2]);
        }
        if (!file.exists()) {
            Messages.FILE_DOES_NOT_EXIST.send(humanEntity, file.getName());
            return;
        }
        ConfigCache configCache = plugin.getCacheManager().getConfigCache(humanEntity.getUniqueId());
        configCache.setFile(file);
        configCache.setPluginName(args[1]);

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        FileUtil.reload(config, file);
        AbstractInventory fastInventory = createFastInventory(args[1]);

        setupConfigInventories(config.getKeys(false).iterator(), fastInventory, config, args[1]);

        humanEntity.openInventory(fastInventory.getInventory());
    }

    private void setupConfigInventories(Iterator<String> iterator, AbstractInventory fastInventory, ConfigurationSection section, String lastPluginName) {
        while (iterator.hasNext()) {
            if (fastInventory.getInventory().firstEmpty() > 44) {
                AbstractInventory abstractInventory = createFastInventory(lastPluginName);
                fastInventory.setItem(53, Constants.ARROW_ITEM.setDisplayName(Messages.NEXT_PAGE.toString()).build(), event -> event.getWhoClicked().openInventory(abstractInventory.getInventory()));
                abstractInventory.setItem(45, Constants.ARROW_ITEM.setDisplayName(Messages.PREVIOUS_PAGE.toString()).build(), event -> event.getWhoClicked().openInventory(fastInventory.getInventory()));
                setupConfigInventories(iterator, abstractInventory, section, lastPluginName);
                return;
            }
            setupKey(lastPluginName, fastInventory, section, iterator.next());
        }
        if (fastInventory.getInventory().firstEmpty() < 45) {
            addNewKeyItem(fastInventory, section.getCurrentPath());
        }
    }

    private AbstractInventory createFastInventory(String title) {
        AbstractInventory fastInventory = new EmptyInventory(54, title);
        fastInventory.addClickHandler(event -> event.setCancelled(true));
        return fastInventory;
    }
    
    private void addNewKeyItem(AbstractInventory inventory, String currentPath) {
        inventory.addItem(Constants.NETHER_STAR.setDisplayName(Messages.CLICK_TO_ADD_NEW_KEY.toString()).build(), event -> {
            HumanEntity humanEntity = event.getWhoClicked();
            humanEntity.closeInventory();
            if (currentPath == null) {
                Messages.INVALID_PATH.send(humanEntity);
                return;
            }
            ConfigCache player = plugin.getCacheManager().getConfigCache(humanEntity.getUniqueId());
            player.setChatState(ConfigCache.ChatState.ADDING_NEW_KEY);
            player.setKeyPath(currentPath);
            Messages.WRITE_NEW_KEY_IN_CHAT.send(humanEntity, StringUtil.isEmpty(currentPath) ? "" : Messages.PATH.toString(currentPath));
        });
    }

    private void setupKey(String lastPluginName, AbstractInventory inventory, ConfigurationSection section, String path) {
        ConfigurationSection sections = section.getConfigurationSection(path);
        String currentPath = section.getCurrentPath();
        String fullPath = StringUtil.isEmpty(currentPath) ? path : String.format("%s.%s", currentPath, path);
        if (sections != null) {
            AbstractInventory fastInventory = createFastInventory(lastPluginName);
            inventory.addItem(new ItemBuilder(VersionUtil.SIGN).setDisplayName(Messages.SECTION.toString(path))
                    .addLore("", Messages.CLICK_TO_OPEN_SECTION.toString(), Messages.SHIFT_CLICK_TO_EDIT_SECTION.toString())
                    .build(), event -> onSectionClick(event, fastInventory, fullPath));
            fastInventory.setItem(45, Constants.ARROW_ITEM.setDisplayName(Messages.PREVIOUS_PAGE.toString()).build(),
                    event -> event.getWhoClicked().openInventory(inventory.getInventory()));
            setupConfigInventories(sections.getKeys(false).iterator(), fastInventory, sections, lastPluginName);
            return;
        }
        addKeyItemToInventory(inventory, getHashedPassword(path, section.get(path, "")), fullPath);
    }

    private static Object getHashedPassword(String path, Object value) {
        String pathLowerCase = path.toLowerCase(java.util.Locale.ROOT);
        if (value instanceof String && (pathLowerCase.endsWith("password") || pathLowerCase.endsWith("pass"))) {
            return HashUtil.toSHA256((String) value);
        } else {
            return value;
        }
    }

    private void addKeyItemToInventory(AbstractInventory inventory, Object value, String fullPath) {
        inventory.addItem(new ItemBuilder(getMaterialFromValue(value))
            .setDisplayName(Messages.KEY.toString(fullPath))
            .addLore(Messages.CURRENT_VALUE.toString(), Messages.VALUE.toString(StringUtil.isEmpty(value.toString()) ? Messages.EMPTY_VALUE : value))
            .addLore("", Messages.CLICK_TO_CHANGE_CURRENT_VALUE.toString(), Messages.SHIFT_CLICK_TO_COPY_CURRENT_VALUE.toString())
            .setAmount(value instanceof Integer ? (Integer) value : 1)
            .build(), event -> onItemClick(event, fullPath, value.toString()));
    }

    private void onSectionClick(InventoryClickEvent event, AbstractInventory fastInventory, String fullPath) {
        HumanEntity humanEntity = event.getWhoClicked();
        if (!event.getClick().isShiftClick()) {
            humanEntity.openInventory(fastInventory.getInventory());
            return;
        }
        handlePlayerPathSetting(fullPath, humanEntity);
    }

    private void handlePlayerPathSetting(String fullPath, HumanEntity humanEntity) {
        humanEntity.closeInventory();
        ConfigCache player = plugin.getCacheManager().getConfigCache(humanEntity.getUniqueId());
        player.setChatState(ConfigCache.ChatState.SETTING_KEY_VALUE);
        player.setKeyPath(fullPath);
        Messages.WRITE_VALUE_IN_CHAT.send(humanEntity, fullPath);
    }

    private void onItemClick(InventoryClickEvent event, String fullPath, String valueString) {
        Player player = (Player) event.getWhoClicked();
        if (event.getClick().isShiftClick()) {
            if (valueString.length() > 256) {
                Messages.VALUE_TOO_LONG.send(player);
                return;
            }
            player.closeInventory();
            TextComponent textComponent = new TextComponent(Messages.CLICK_MESSAGE_TO_COPY_VALUE.toString(valueString));
            textComponent.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, valueString));
            player.spigot().sendMessage(textComponent);
            return;
        }
        handlePlayerPathSetting(fullPath, player);
    }

    private static Material getMaterialFromValue(Object value) {
        for (ConfigMaterial values : ConfigMaterial.values()) {
            if (values.getClazz().isInstance(value)) {
                if (value instanceof Boolean) {
                    return Boolean.TRUE.equals(value) ? Material.SLIME_BALL : Material.MAGMA_CREAM;
                }
                return values.getMaterial();
            }
        }
        return Material.BEDROCK;
    }

    private void sendHelpMessage(CommandSender sender, String label) {
        Messages.HELP.send(sender);
        Messages.HELP_CONFIG.send(sender, label);
        Messages.HELP_INVENTORYTOFILE.send(sender, label);
        Messages.HELP_FILETOINVENTORY.send(sender, label);
    }

    @Override
    public List<String> complete(CommandSender sender, Command command, String... args) {
        int length = args.length;
        if (length == 1) {
            return Arrays.asList("help", "reload", Constants.CONFIG, Constants.INVENTORY_TO_FILE, Constants.FILE_TO_INVENTORY);
        }
        String args0 = args[0];
        if (length == 2) {
            return getListAtLength2(args0);
        }
        String args1 = args[1];
        if (length == 3) {
            return getListAtLength3(args0, args1);
        }
        return Collections.emptyList();
    }

    private List<String> getListAtLength2(String args0) {
        if (args0.equalsIgnoreCase(Constants.INVENTORY_TO_FILE) || args0.equalsIgnoreCase(Constants.FILE_TO_INVENTORY)) {
            return Arrays.stream(Convertible.Converters.values())
                         .filter(Convertible.Converters::isEnabled)
                         .map(Convertible.Converters::getName)
                         .collect(Collectors.toList());
        } else if (args0.equalsIgnoreCase(Constants.CONFIG)) {
            return Arrays.stream(Bukkit.getPluginManager().getPlugins())
                .filter(Plugin::isEnabled)
                .map(Plugin::getName)
                .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    private List<String> getListAtLength3(String args0, String args1) {
        if (args0.equalsIgnoreCase(Constants.FILE_TO_INVENTORY)) {
            for (Convertible.Converters converters : Convertible.Converters.values()) {
                if (converters.isEnabled() && converters.getName().equalsIgnoreCase(args1)) {
                    return converters.getConverter().getAllFileNames();
                }
            }
        } else if (args0.equalsIgnoreCase(Constants.CONFIG)) {
            return getPluginConfigFiles(args1);
        }
        return Collections.emptyList();
    }

    private static List<String> getPluginConfigFiles(String args1) {
        Plugin targetPlugin = Bukkit.getPluginManager().getPlugin(args1);
        if (targetPlugin == null || !targetPlugin.isEnabled()) {
            return Collections.emptyList();
        }
        return fileList(targetPlugin.getDataFolder(), "");
    }

    private static List<String> fileList(File folder, String path) {
        List<String> result = new ArrayList<>();
        File[] files = folder.listFiles();
        if (files == null) {
            return Collections.emptyList();
        } else {
            for (File file : files) {
                String fileName = file.getName();
                if (file.isDirectory()) {
                    result.addAll(fileList(file, fileName + File.separator));
                } else if (fileName.endsWith(Constants.YML_EXPANSION)) {
                    result.add(path + fileName);
                }
            }
        }
        return result;
    }
}
