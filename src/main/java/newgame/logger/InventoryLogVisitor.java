package newgame.logger;

import newgame.inventory.HeroInventory;
import newgame.items.Item;

/**
 * Interface pattern for logging inventory items
 * 
 * @author Benjamin Krüger
 */
public interface InventoryLogVisitor 
{
    void log(HeroInventory heroInventory);
}
