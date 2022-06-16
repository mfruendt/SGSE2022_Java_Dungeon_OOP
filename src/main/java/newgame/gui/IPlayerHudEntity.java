package newgame.gui;

/** Interface for entities that will be tracked on the HUD
 * @author Maxim Fründt
 */
public interface IPlayerHudEntity
{
    /** Get the health of the tracked entity to display it
     *
     * @return Entity health
     */
    float getHealth();

    /** Get the skill level of the tracked entity
     *
     * @return Entity skill level (With progress to next level as decimal place)
     */
    float getSkillLevel();
}
