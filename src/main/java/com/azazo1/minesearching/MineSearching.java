package com.azazo1.minesearching;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

public final class MineSearching extends JavaPlugin implements Listener {
    private final HashMap<Player, Inventory> inventories = new HashMap<>(); // 附近矿物选择箱
    private final HashMap<Player, HashMap<Material, ArrayList<Location>>> mineBlocks = new HashMap<>(); // 用于暂时存储矿物搜索结果
    private final int searchSize = 5; // 搜索尺寸，搜索正方体的边长的一半
    private ItemStack searchPickaxe;
    private String pickaxeName = "Hawkeye pickaxe";
    private final ArrayList<Material> mineTypes = new ArrayList<>() {{ // 矿物顺序为重要到不重要
        add(Material.ANCIENT_DEBRIS);//远古残骸
        add(Material.DIAMOND_ORE);// 钻石
        add(Material.IRON_ORE);//铁
        add(Material.COAL_ORE);// 煤
        add(Material.LAPIS_ORE); // 青金石
        add(Material.REDSTONE_ORE);//红石
        add(Material.NETHER_GOLD_ORE);//下界金
        add(Material.GOLD_ORE);//金
        add(Material.NETHER_QUARTZ_ORE);//下界石英
        add(Material.COPPER_ORE);//铜
        add(Material.EMERALD_ORE);//绿宝石
        // todo 添加深板岩矿物

    }};

    @Override
    public void onEnable() {
        // Plugin startup logic
        initPickaxe();
        Objects.requireNonNull(Bukkit.getPluginCommand("getpickaxe")).setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this); // 这是有效的
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player) {
            // 如果是玩家，那就给他一把镐子
            player.getInventory().addItem(searchPickaxe);
        }
        return true;
    }

    private void initPickaxe() {
        searchPickaxe = new ItemStack(Material.IRON_PICKAXE);
        searchPickaxe.addEnchantment(Enchantment.DIG_SPEED, 5);
        ItemMeta im = searchPickaxe.getItemMeta();
        im.displayName(Component.text(pickaxeName).color(TextColor.color(252, 48, 255)));
        im.setUnbreakable(true);
        searchPickaxe.setItemMeta(im);
    }

    @EventHandler
    public void onPlayerRightClick(@NotNull PlayerInteractEvent event) {
        if (event.getAction().isRightClick() && event.getItem() != null && event.getItem().equals(searchPickaxe)) {
            var mineBlocks = searchMineBlocks(event.getPlayer().getLocation()); // 搜索矿物
            this.mineBlocks.put(event.getPlayer(), mineBlocks);

            Component log = Component.text("");
            if (mineBlocks.size() > 0) {
                log = log.append(Component.text("找到矿物:\n"));
                for (Material mineType : mineBlocks.keySet()) {
                    ArrayList<Location> locations = mineBlocks.get(mineType);
                    log = log.append(Component.text(mineType.toString()));
                    log = log.append(Component.text(" 数量: " + locations.size() + "\n"));
                }
            } else {
                log = log.append(Component.text("暂无矿物"));
            }
            event.getPlayer().sendMessage(log);
            selectMineToFace(mineBlocks, event.getPlayer());
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInventoryClick(@NotNull InventoryClickEvent event) {
        HumanEntity whoClicked = event.getWhoClicked();
        if (!(whoClicked instanceof Player player)) {
            return;
        }
        Inventory inventory = inventories.get(player);
        if (Objects.equals(event.getClickedInventory(), inventory)) {
            // 改变玩家朝向（朝向至选择矿物）
            if (event.getCurrentItem() != null && inventory.contains(event.getCurrentItem())) {
                Material material = event.getCurrentItem().getType();
                Location targetLocation = mineBlocks.get(player).get(material).get(0);
                int tx = targetLocation.getBlockX();
                int ty = targetLocation.getBlockY();
                int tz = targetLocation.getBlockZ();
                double x = whoClicked.getLocation().getX();
                double y = whoClicked.getLocation().getY();
                double z = whoClicked.getLocation().getZ();
//                使用游戏内命令进行传送时用此角度
//                double angleH = Math.atan2(tz - z, tx - x) - Math.PI / 2;
//                double angleV = -Math.atan2(ty - y, Math.sqrt(Math.pow(tx - x, 2) + Math.pow(tz - z, 2)));
                player.teleport(player.getLocation().setDirection(new Vector(tx - x, ty - y, tz - z)));
            }
            player.closeInventory();
            inventory.clear();
            event.setCancelled(true);
        }
    }

    private void selectMineToFace(HashMap<Material, ArrayList<Location>> mineBlocks, Player player) {
        // 开启一个容器，让玩家选择一个矿物来朝向
        Inventory inventory = Bukkit.createInventory(null, 18);
        inventories.put(player, inventory);
        for (Material material : mineBlocks.keySet()) {
            ItemStack item = new ItemStack(material, mineBlocks.get(material).size());
            inventory.addItem(item);
        }
        player.openInventory(inventory);
    }

    private @NotNull HashMap<Material, ArrayList<Location>> searchMineBlocks(Location location) {
        HashMap<Material, ArrayList<Location>> oreBlocks = new HashMap<>();
        // 开始搜索附近矿物
        // 检查区块内所有方块是否为矿物方
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        for (int i = x - searchSize; i <= x + searchSize; i++) {
            for (int j = y - searchSize; j <= y + searchSize; j++) {
                for (int k = z - searchSize; k <= z + searchSize; k++) {
                    Location loc = new Location(location.getWorld(), i, j, k);
                    Material mineType = isInMineTypes(loc.getBlock().getType());
                    if (mineTypes.contains(mineType)) {
                        ArrayList<Location> locations;
                        if (!oreBlocks.containsKey(mineType)) {
                            locations = new ArrayList<>();
                            oreBlocks.put(mineType, locations);
                        } else {
                            locations = oreBlocks.get(mineType);
                        }
                        locations.add(loc);
                    }
                }
            }
        }
        return oreBlocks;
    }

    public Material isInMineTypes(Material targetMaterial) {
        for (Material mineType : mineTypes) {
            if (mineType.equals(targetMaterial)) {
                return mineType;
            }
        }
        return null;
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
