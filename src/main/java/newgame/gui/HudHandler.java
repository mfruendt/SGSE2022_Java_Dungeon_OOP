package newgame.gui;

import de.fhbielefeld.pmdungeon.vorgaben.graphic.HUD;
import newgame.inventory.Inventory;
import newgame.inventory.InventoryTypes;

/** Handler for the game HUD
 * @author Maxim Fründt
 */
public class HudHandler
{
    private IPlayerHudEntity playerEntity;

    private final HUD hud;

    private PlayerStatsHud statsHudElement;

    private InventoryHud invHudElement;

    private InventoryHud chestInvHudElement;

    /** Create new HUD handler
     *
     */
    public HudHandler()
    {
        hud = new HUD();
    }

    /** Update the HUD
     *
     */
    public void update()
    {
        hud.draw();

        if (statsHudElement != null && playerEntity != null)
        {
            statsHudElement.draw(playerEntity.getHealth(), playerEntity.getSkillLevel());

            if (invHudElement != null)
            {
                invHudElement.draw();
            }

            if (chestInvHudElement != null)
            {
                chestInvHudElement.draw();
            }
        }
    }

    /** Display the inventory content of an inventory object
     *
     * @param inventory Inventory of the hero
     * @param inventoryType Type of the inventory
     */
    public <T extends Inventory> void displayInventory(T inventory, InventoryTypes inventoryType)
    {
        if (inventoryType == InventoryTypes.PLAYER_INVENTORY && invHudElement != null)
        {
            invHudElement.setInventoryContent(inventory);
        }
    }

    /** Add new trackable player entity to the HUD
     *
     * @param entity Player entity to be tracked
     * @param inventorySize Size of the tracked player entity
     * @return True if add was successful, else false
     */
    public boolean addPlayerEntity(IPlayerHudEntity entity, int inventorySize)
    {
        if (entity == null && inventorySize <= 0)
        {
            return false;
        }

        playerEntity = entity;

        statsHudElement = new PlayerStatsHud(hud.getHudBatch());
        invHudElement = new InventoryHud(hud.getHudBatch(), inventorySize);

        return true;
    }

    /** Remove a trackable player entity from the HUD
     *
     */
    public void removePlayerEntity()
    {
        playerEntity = null;
        statsHudElement = null;
        invHudElement = null;
    }
}
