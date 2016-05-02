/*
 * Copyright (C) 2013 Moribus
 * Copyright (C) 2015 ProkopyL <prokopylmc@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.moribus.imageonmap.ui;

import fr.moribus.imageonmap.map.ImageMap;
import fr.moribus.imageonmap.map.MapManager;
import fr.moribus.imageonmap.map.PosterMap;
import fr.moribus.imageonmap.map.SingleMap;
import fr.zcraft.zlib.core.ZLib;
import fr.zcraft.zlib.tools.items.ItemStackBuilder;
import fr.zcraft.zlib.tools.items.ItemUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Queue;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class MapItemManager implements Listener
{
    static private HashMap<UUID, Queue<ItemStack>> mapItemCache;
    
    static public void init()
    {
        mapItemCache = new HashMap();
        ZLib.registerEvents(new MapItemManager());
    }
    
    static public void exit()
    {
        if(mapItemCache != null) mapItemCache.clear();
        mapItemCache = null;
    }
    
    static public boolean give(Player player, ImageMap map)
    {
        if(map instanceof PosterMap) return give(player, (PosterMap) map);
        else if(map instanceof SingleMap) return give(player, (SingleMap) map);
        return false;
    }
    
    static public boolean give(Player player, SingleMap map)
    {
        return give(player, createMapItem(map));
    }
    
    static public boolean give(Player player, PosterMap map)
    {
        return give(player, SplatterMapManager.makeSplatterMap(map));
    }
    
    static public int giveCache(Player player)
    {
        Queue<ItemStack> cache = getCache(player);
        Inventory inventory = player.getInventory();
        int givenItemsCount = 0;
        
        while(inventory.firstEmpty() >= 0 && !cache.isEmpty())
        {
            inventory.addItem(cache.poll());
            givenItemsCount++;
        }
        
        return givenItemsCount;
    }
    
    static private boolean give(Player player, ItemStack item)
    {
        if(player.getInventory().firstEmpty() <= -1)
        {
            getCache(player).add(item);
            return true;
        }
        else
        {
            player.getInventory().addItem(item);
            return false;
        }
    }
    
    static public ItemStack createMapItem(SingleMap map)
    {
        return createMapItem(map.getMapsIDs()[0], map.getName());
    }
    
    static public ItemStack createMapItem(PosterMap map, int x, int y)
    {
        String mapName;
        if(map.hasColumnData())
        {
            mapName = map.getName() +
                " (row " + x + 
                ", column " + y + ")";
        }
        else
        {
            mapName = map.getName();
        }
        return createMapItem(map.getMapIdAt(x, y), mapName);
    }
    
    static public ItemStack createMapItem(short mapID, String text)
    {
        return new ItemStackBuilder(Material.MAP)
                .data(mapID)
                .title(text)
                .hideAttributes()
                .item();
    }

    /**
     * Returns the item to place to display the (col;row) part of the given poster.
     *
     * @param map The map to take the part from.
     * @param x The x coordinate of the part to display. Starts at 0.
     * @param y The y coordinate of the part to display. Starts at 0.
     *
     * @return The map.
     *
     * @throws ArrayIndexOutOfBoundsException If x;y is not inside the map.
     */
    static public ItemStack createSubMapItem(ImageMap map, int x, int y)
    {
        if(map instanceof PosterMap && ((PosterMap) map).hasColumnData())
        {
            return MapItemManager.createMapItem(
                    ((PosterMap) map).getMapIdAt(x, y),
                    map.getName() +
                            " (row " + (y + 1) +
                            ", column " + (x + 1) + ")"
            );
        }
        else
        {
            if(x != 0 || y != 0)
            {
                throw new ArrayIndexOutOfBoundsException(); // Coherence
            }

            return MapItemManager.createMapItem(map.getMapsIDs()[0], map.getName());
        }
    }
    
    static public int getCacheSize(Player player)
    {
        return getCache(player).size();
    }
    
    static private Queue<ItemStack> getCache(Player player)
    {
        Queue<ItemStack> cache = mapItemCache.get(player.getUniqueId());
        if(cache == null)
        {
            cache = new ArrayDeque<>();
            mapItemCache.put(player.getUniqueId(), cache);
        }
        return cache;
    }
    
    static private String getMapTitle(ItemStack item)
    {
        ImageMap map = MapManager.getMap(item);
        if(map instanceof SingleMap)
        {
            return map.getName();
        }
        else
        {
            PosterMap poster = (PosterMap) map;
            int index = poster.getIndex(item.getDurability());
            return map.getName() + " (row " + (poster.getRowAt(index)) +
                            ", column " + (poster.getColumnAt(index)) + ")";
        }
    }
    
    static private void onItemFramePlace(ItemFrame frame, Player player, PlayerInteractEntityEvent event)
    {
        if(frame.getItem().getType() != Material.AIR) return;
        if(!MapManager.managesMap(player.getItemInHand())) return;
        
        if(SplatterMapManager.hasSplatterAttributes(player.getItemInHand()))
        {
            SplatterMapManager.placeSplatterMap(frame, player);
        }
        else
        {
            ItemStack is = new ItemStack(Material.MAP, 1, player.getItemInHand().getDurability());
            frame.setItem(is);
        }
        
        event.setCancelled(true);
        ItemUtils.consumeItem(player);
    }
    
    static private void onItemFrameRemove(ItemFrame frame, Player player, EntityDamageByEntityEvent event)
    {
        ItemStack item = frame.getItem();
        if(frame.getItem().getType() != Material.MAP) return;
        
        if(player.isSneaking())
        {
            PosterMap poster = SplatterMapManager.removeSplatterMap(frame);
            if(poster != null)
            {
                event.setCancelled(true);
                if(player.getGameMode() == GameMode.CREATIVE)
                {
                    if(!SplatterMapManager.hasSplatterMap(player, poster))
                        poster.give(player);
                }
                return;
            }
        }
        
        if(!MapManager.managesMap(frame.getItem())) return;
        frame.setItem(ItemUtils.setDisplayName(item, getMapTitle(item)));
        
    }
    
    @EventHandler
    static public void onEntityDamage(EntityDamageByEntityEvent event)
    {
        if(!(event.getEntity() instanceof ItemFrame)) return;
        if(!(event.getDamager() instanceof Player)) return;
        
        onItemFrameRemove((ItemFrame)event.getEntity(), (Player)event.getDamager(), event);
    }
    
    @EventHandler
    static public void onEntityInteract(PlayerInteractEntityEvent event)
    {
        if(!(event.getRightClicked() instanceof ItemFrame)) return;
        
        onItemFramePlace((ItemFrame)event.getRightClicked(), event.getPlayer(), event);
    }
}