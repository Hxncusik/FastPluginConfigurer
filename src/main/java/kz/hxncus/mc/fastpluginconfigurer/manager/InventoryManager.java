package kz.hxncus.mc.fastpluginconfigurer.manager;

import kz.hxncus.mc.fastpluginconfigurer.FastPluginConfigurer;
import kz.hxncus.mc.fastpluginconfigurer.inventory.FastInventory;
import kz.hxncus.mc.fastpluginconfigurer.inventory.marker.ItemMarker;
import kz.hxncus.mc.fastpluginconfigurer.inventory.marker.PDCItemMarker;
import kz.hxncus.mc.fastpluginconfigurer.inventory.marker.UnavailableItemMarker;
import kz.hxncus.mc.fastpluginconfigurer.util.VersionUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@EqualsAndHashCode
public class InventoryManager implements Listener {
    private final Map<Inventory, FastInventory> inventories = new ConcurrentHashMap<>();
    private final ItemMarker itemMarker;

    public InventoryManager(FastPluginConfigurer plugin) {
        if (VersionUtil.IS_PDC_VERSION) {
            this.itemMarker = new PDCItemMarker(plugin);
        } else {
            this.itemMarker = new UnavailableItemMarker();
        }
    }

    public void register(Inventory inventory, FastInventory handler) {
        inventories.put(inventory, handler);
    }

    @EventHandler
    public void handleDrag(InventoryDragEvent event) {
        FastInventory inventory = inventories.get(event.getInventory());
        if (inventory != null) {
            inventory.handleDrag(event);
        }
    }

    @EventHandler
    public void handleClick(InventoryClickEvent event) {
        FastInventory inventory = inventories.get(event.getInventory());
        if (inventory != null) {
            inventory.handleClick(event);
        }
    }

    @EventHandler
    public void handleClose(InventoryCloseEvent event) {
        FastInventory inventory = inventories.get(event.getInventory());
        if (inventory != null && inventory.handleClose(event)) {
            event.getPlayer().openInventory(inventory.getInventory());
        }
    }

    @EventHandler
    public void handleOpen(InventoryOpenEvent event) {
        FastInventory inventory = inventories.get(event.getInventory());
        if (inventory != null) {
            inventory.handleOpen(event);
        }
    }

    public void closeAll() {
        for (Inventory inventory : inventories.keySet()) {
            for (HumanEntity viewer : inventory.getViewers()) {
                viewer.closeInventory();
            }
        }
    }
}
