package newgame.gui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import de.fhbielefeld.pmdungeon.vorgaben.graphic.TextStage;
import newgame.inventory.Inventory;
import newgame.inventory.InventoryTypes;

import java.util.ArrayList;
import java.util.List;

/** Object that manages the player inventory HUD
 * @author Maxim Fründt
 */
public class InventoryHud
{
    /* Text stage that will contain all text labels */
    private final TextStage statsTexts;

    /* Font size of the inventory text */
    private final static int INV_SLOT_FONT_SIZE = 25;
    /* Start coordinate X of the player inventory text */
    private final static int INV_SLOT_X0_PLAYER = 10;
    /* Start coordinate X of the chest inventory text */
    private final static int INV_SLOT_X0_CHEST = Gdx.graphics.getWidth() - 300;
    /* Start coordinate Y of the inventory text */
    private final static int INV_SLOT_Y0 = Gdx.graphics.getHeight() - 30;
    /* Y offset between inventory elements */
    private final static int INV_SLOT_Y_OFFSET = INV_SLOT_FONT_SIZE + 10;
    /* Path to the used font */
    private final static String INV_SLOT_FONT_PATH = "assets/fonts/OpenSans-Regular.ttf";
    /* Color of the texts */
    private final static Color INV_SLOT_COLOR = Color.WHITE;

    /* Labels for the inventory slots */
    private final List<Label> inventorySlots;

    /* Prefix for the inventory slots */
    private final static String INV_SLOT_PREFIX = " --> ";
    /* Text for an empty inventory slot */
    private final static String INV_SLOT_EMPTY = "----";

    /* Title text of the player inventory */
    private final static String INV_TITLE_PLAYER = "Player inventory";
    /* Title text of the chest inventory */
    private final static String INV_TITLE_CHEST = "Chest inventory";

    /** Create a new inventory HUD element to display inventory contents
     *
     * @param batch Batch that will be used to render the text fields
     * @param inventorySize Size of the inventory
     */
    public InventoryHud(SpriteBatch batch, int inventorySize)
    {
        statsTexts = new TextStage(batch);
        inventorySlots = new ArrayList<>();

        inventorySlots.add(statsTexts.drawText(INV_TITLE_PLAYER, INV_SLOT_FONT_PATH, INV_SLOT_COLOR, INV_SLOT_FONT_SIZE, INV_SLOT_FONT_SIZE, INV_SLOT_FONT_SIZE, INV_SLOT_X0_PLAYER, INV_SLOT_Y0));

        for (int i = 0; i < inventorySize; i++)
        {
            inventorySlots.add(statsTexts.drawText(i + INV_SLOT_PREFIX + INV_SLOT_EMPTY, INV_SLOT_FONT_PATH, INV_SLOT_COLOR, INV_SLOT_FONT_SIZE, INV_SLOT_FONT_SIZE, INV_SLOT_FONT_SIZE, INV_SLOT_X0_PLAYER, INV_SLOT_Y0 - INV_SLOT_Y_OFFSET * (i + 1)));
        }
    }

    /** Draw the inventory HUD
     *
     */
    public void draw()
    {
        statsTexts.draw();
    }

    /** Set new inventory content to be displayed on the HUD
     *
     * @param inventory Inventory content
     */
    public <T extends Inventory> void setInventoryContent(T inventory)
    {
        // If the inventory is valid draw all inventory contents, else set text to empty
        if (inventory != null)
        {
            for (int i = 1, j = 0; i < inventorySlots.size(); i++, j++)
            {
                if (inventory.getItemAt(j) != null)
                {
                    inventorySlots.get(i).setText(j + INV_SLOT_PREFIX + inventory.getItemAt(j).getClass().getSimpleName());
                }
                else
                {
                    inventorySlots.get(i).setText(j + INV_SLOT_PREFIX + INV_SLOT_EMPTY);
                }
            }
        }
        else
        {
            for (Label label : inventorySlots)
            {
                label.setText("");
            }
        }
    }
}
