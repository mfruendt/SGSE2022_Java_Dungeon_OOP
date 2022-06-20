package newgame.characters;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

import de.fhbielefeld.pmdungeon.vorgaben.tools.Point;
import newgame.DamageDetails;
import newgame.HeroObserver;
import newgame.inventory.HeroEquipment;
import newgame.inventory.HeroInventory;
import newgame.DamageDetails.DamageDirections;
import newgame.animations.CharacterAnimations;
import newgame.gui.IPlayerHudEntity;
import newgame.items.*;
import newgame.items.weapons.Projectile;
import newgame.items.weapons.Weapon;
import newgame.items.weapons.WeaponType;
import newgame.items.weapons.rangedweapons.RangedWeapon;
import newgame.logger.GameEventsLogger;
import newgame.logger.InventoryLogVisitor;
import newgame.logger.LogMessages;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Class that will be used to manage the game hero
 *
 * @author Maxim Fründt
 */
public class Hero extends Character implements IPlayerHudEntity
{
    private HeroObserver heroesItemObserver;

    /* Fixed start health of the hero */
    private static final float HERO_HEALTH = 1000.0f;

    /* Flag if damage is currently taken
     * (This will lock the movement and knockback the player)
     */
    private boolean isDamageTaken = false;
    
    private DamageDirections attackDirection = DamageDirections.NONE;

    private static final float ATTACK_RANGE = 0.2f;
    
    private static final float PICKUP_RANGE = 0.5f;

    private static final float ATTACK_DAMAGE_UNARMED = 3.0f;

    private static final float DEFENSE_UNARMED = 1.0f;

    private float damage;

    private float defense;

    private DamageDirections knockbackDirection;

    /* Starting point of the knockback */
    private Point knockbackStartingPoint;

    /* Distance of the knockback if damage has bean taken */
    private static final int KNOCKBACK_DISTANCE = 3;

    /* The movement speed while receiving knockback */
    private static final float KNOCKBACK_SPEED = 0.5f;

    private final HeroInventory inventory;

    private final HeroEquipment equipment;

    private boolean hasInventoryContentChanged = false;

    private float skillLevel;

    private Projectile projectile;

    /** Create a new hero character
     *
     */
    public Hero()
    {
        animations = new ArrayList<>();

        animations.add(AnimationStates.IDLE_LEFT.getIndex(), CharacterAnimations.getAnimation(CharacterAnimations.Animations.HERO_M_IDLE_L));
        animations.add(AnimationStates.IDLE_RIGHT.getIndex(), CharacterAnimations.getAnimation(CharacterAnimations.Animations.HERO_M_IDLE_R));
        animations.add(AnimationStates.RUNNING_LEFT.getIndex(), CharacterAnimations.getAnimation(CharacterAnimations.Animations.HERO_M_RUN_L));
        animations.add(AnimationStates.RUNNING_RIGHT.getIndex(), CharacterAnimations.getAnimation(CharacterAnimations.Animations.HERO_M_RUN_R));

        isDead = false;
        health = HERO_HEALTH;
        activeAnimation = AnimationStates.IDLE_RIGHT;
        inventory = new HeroInventory();
        equipment = new HeroEquipment();
        damage = ATTACK_DAMAGE_UNARMED;
        defense = DEFENSE_UNARMED;
        GameEventsLogger.getLogger().info(LogMessages.CHARACTER_SPAWN + ": " + Hero.class.getName());
    }

    /** Health getter to display in the HUD
     *
     * @return Health of the hero
     */
    @Override
    public float getHealth()
    {
        return health;
    }

    /** Skill level getter to display in the HUD
     *
     * @return Skill level of the hero
     */
    @Override
    public float getSkillLevel()
    {
        return skillLevel;
    }

    /** Handle the death of the hero
     *
     */
    @Override
    public void onDeath()
    {
        isDead = true;
        GameEventsLogger.getLogger().info(LogMessages.GAME_OVER.toString());
        GameEventsLogger.getLogger().info(LogMessages.RESTART_GAME.toString());
    }

    /** Function to receive damage.
     *  For the hero this will knock the hero back and reduce health.
     */
    @Override
    public boolean receiveDamage(final DamageDetails damageDetails)
    {
        if (damageDetails == null ||
            damageDetails.getDirection() == null ||
            damageDetails.getDamageDealt() <= 0)
        {
            GameEventsLogger.getLogger().severe(LogMessages.ILLEGAL_ARGUMENT.toString());
            return false;
        }
        if (this.currentPosition == null)
        {
            GameEventsLogger.getLogger().severe(LogMessages.ACCESS_WITH_NULL_ATTRIBUTE.toString());
            return false;
        }
        isDamageTaken = true;
        knockbackStartingPoint = new Point(this.currentPosition);
        // take 30 % of damage always and additional 70 % of the damage - percentage defense
        float reducedDamageByDefense = (damageDetails.getDamageDealt() * 0.7f - this.defense);
        if (reducedDamageByDefense > 0)
        {
            health -= (damageDetails.getDamageDealt() * 0.3f + reducedDamageByDefense);
        }
        else
        {
            health -= damageDetails.getDamageDealt() * 0.3f;
        }

        // Reduce durability of shield, if some is equipped
        Shield equippedShield = this.equipment.getShield();
        if (equippedShield != null)
        {
            equippedShield.reduceDurability(0.1f);
            if (equippedShield.getDurability() < 0)
            {
                if (equipment.removeShield() == null)
                {
                    GameEventsLogger.getLogger().severe(LogMessages.ACCESS_WITH_NULL_ATTRIBUTE.toString());
                    throw new RuntimeException(this.getClass().getSimpleName());
                }
            }
        }

        if (health <= 0)
        {
            onDeath();
        }
        else
        {
            switch (damageDetails.getDirection())
            {
                case TOP:
                {
                    knockbackDirection = DamageDirections.BOTTOM;
                    activeAnimation = AnimationStates.IDLE_RIGHT;
                    break;
                }
                case BOTTOM:
                {
                    knockbackDirection = DamageDirections.TOP;
                    activeAnimation = AnimationStates.IDLE_RIGHT;
                    break;
                }
                case LEFT:
                {
                    knockbackDirection = DamageDirections.RIGHT;
                    activeAnimation = AnimationStates.IDLE_LEFT;
                    break;
                }
                case RIGHT:
                {
                    knockbackDirection = DamageDirections.LEFT;
                    activeAnimation = AnimationStates.IDLE_RIGHT;
                    break;
                }
                default:
                    GameEventsLogger.getLogger().severe(LogMessages.UNKNOWN_VARIABLE_VALUE.toString());
            }
        }
        return true;
    }

    /** Cyclical redraw and handling of heroes movement, pickups/actions, damage taking and attacks
     *
     */
    @Override
    public void update()
    {
        // Redraw the hero
        this.draw();
        updateWeaponTimer();
        if (isDamageTaken)
        {
            handleDamageTaken();
        }
        else
        {
            handlePlayerMovement();
            handleAttacks();
            handleInputsForInventoryHandling();
        }
    }

    /** Resets the attack of the hero after either dealing damage or a time frame
     *
     */
    public void resetAttack()
    {
        attackDirection = DamageDirections.NONE;
    }

    /**
     * Add item to heroes inventory
     * 
     * @param toBeAddedItem To Be added Item
     * @return Success about adding item
     */
    public boolean addItemToInventory(final Item toBeAddedItem)
    {
        if (this.inventory.addItem(toBeAddedItem))
        {
            hasInventoryContentChanged = true;
            return true;
        }

        return false;
    }

    /** Get the current attack direction
     *
     * @return Attack direction
     */
    public DamageDirections getAttackDirection()
    {
        return attackDirection;
    }

    /** Add experience to the skill level of the hero
     *
     * @param experience Experience to be added
     * @throws IllegalArgumentException if experience is negative
     */
    public boolean addExperience(final float experience) throws IllegalArgumentException
    {
        if (experience < 0)
        {
            GameEventsLogger.getLogger().severe(LogMessages.ILLEGAL_ARGUMENT.toString());
            return false;
        }
        skillLevel += experience;
        return false;
    }

    /** Check if the inventory content has changed
     *
     * @return True if inventory content has changed, else false
     */
    public boolean getHasInventoryContentChanged()
    {
        if (hasInventoryContentChanged)
        {
            hasInventoryContentChanged = false;
            return true;
        }
        else
        {
            return false;
        }
    }

    /** Check if a monster came in contact with the player attack
     *
     * @param monsterPosition Position of the monster
     * @return Details about the dealt damage
     */
    public DamageDetails checkMonsterContact(final Point monsterPosition)
    {
        if (monsterPosition == null)
        {
            GameEventsLogger.getLogger().severe(LogMessages.ILLEGAL_ARGUMENT.toString());
            return null;
        }
        // if the player is currently not attack no damage can be dealt
        if (attackDirection == DamageDirections.NONE)
        {
            return new DamageDetails();
        }
        else
        {
            DamageDirections direction = checkCharacterContact(ATTACK_RANGE, monsterPosition, attackDirection);

            // If the monster is within range of the attack
            if (direction != DamageDirections.NONE)
            {
                // Generate random number to calculate the damage probability
                int randomNum = ThreadLocalRandom.current().nextInt(0, 10);

                // If the random number is between 0 and 6 (= 60% probability) the attack is successful
                if (randomNum <= 6)
                {
                    Weapon equippedWeapon = this.equipment.getWeapon();
                    if (equippedWeapon != null)
                    {
                        equippedWeapon.reduceDurability();
                        if (equippedWeapon.getDurability() <= 0)
                        {
                            GameEventsLogger.getLogger().info(LogMessages.WEAPON_SPENT.toString());
                            this.equipment.removeWeapon();
                        }
                    }
                    return  new DamageDetails(attackDirection, this.damage);
                }
                else
                {
                    return new DamageDetails();
                }
            }
            else
            {
                return new DamageDetails();
            }
        }
    }

    /**
     * Register new observer instance.
     *
     * @param heroesItemObserver Observer interface to be registered
     */
    public void register(final HeroObserver heroesItemObserver)
    {
        this.heroesItemObserver = heroesItemObserver;
    }

    /**
     * Log all items of inventory
     * 
     * @param inventoryLogVisitor Object to log items
     */
    public void logInventoryItems(final InventoryLogVisitor inventoryLogVisitor)
    {
        this.inventory.log(inventoryLogVisitor);
    }

    /**
     * Check for pickup inputs
     */
    public void handleItemPickUp(List<DungeonItem> dungeonItems)
    {
    	if(Gdx.input.isKeyPressed(Input.Keys.F))
    	{
            for (DungeonItem dungeonItem : dungeonItems)
            {
                if (this.checkForAnyContact(this.getPickupRange(), dungeonItem.getPosition()))
                {
                    if (inventory.getRemainingCapacity() > 0)
                    {
                        dungeonItem.setPickUpStatus(true);
                    }
                }
            }
    	}
    }
    
    /**
     * Returns the pickup range for the Hero
     * @return Pickup Range
     */
    public float getPickupRange()
    {
    	return PICKUP_RANGE;
    }

    /** Get the inventory size of the player inventory
     *
     * @return Size of the inventory
     */
    public int getInventorySize()
    {
        return inventory.getCapacity();
    }

    /**
    * Returns the projectile of the hero
    * @return Projectile projectile
    */
    public Projectile getProjectile()
    {
    	return projectile;
    }
    
    /**
     * Removes the projectile of the hero
     */
    public void removeProjectile()
    {
    	projectile = null;
    }

    /*
     * Use some item from inventory
     * Use can mean equip, drink, read the item - depending on its type
     * toBeUsedItem may also be a satchel => then first satchel item will be used (if exists)
     */
    private <T extends Item> boolean useItem(final T toBeUsedItem)
    {
        if (toBeUsedItem == null)
        {
            return false;
        }
        // first try to drop item (to be differ between inventory and satchel)
        Item droppedItem = null;
        boolean isToBeUsedItemDropped = false;
        ItemType itemType = toBeUsedItem.getType();
        switch (itemType)
        {
            case WEAPON:
            case PROTECTION:
            case SPELL:
            case POTION:
            {
                droppedItem = toBeUsedItem;
                isToBeUsedItemDropped = this.inventory.removeItem(toBeUsedItem);
                break;
            }
            default:
                GameEventsLogger.getLogger().severe(LogMessages.TRIED_TO_USE_INVALID_ITEM_TYPE.toString());
        }

        // don't change order!!!
        if (!isToBeUsedItemDropped)
        {
            if (!itemType.equals(ItemType.SATCHEL))
            {
                GameEventsLogger.getLogger().severe(LogMessages.DROPPING_ITEM_FAILED.toString());
            }
            else
            {
                GameEventsLogger.getLogger().info(LogMessages.SATCHEL_EMPTY.toString());
            }
            return false;
        }
        boolean isItemUsedSuccessfully = false;
        itemType = droppedItem.getType();
        switch (itemType)
        {
            case WEAPON:
                isItemUsedSuccessfully = this.equipItem((Weapon) droppedItem);
                break;
            case PROTECTION: 
                isItemUsedSuccessfully = this.equipItem((Shield) droppedItem);
                break;
            case POTION:
                isItemUsedSuccessfully = this.drinkPotion((Potion) droppedItem);
                break;
            default:
                GameEventsLogger.getLogger().severe(LogMessages.TRIED_TO_USE_INVALID_ITEM_TYPE.toString());
        }

        // if item is not used, we have to undo previous drop from inventory/satchel
        if (!isItemUsedSuccessfully)
        {
            this.inventory.addItem(toBeUsedItem);
        }
        else 
        {
            hasInventoryContentChanged = true;
        }

        return isItemUsedSuccessfully;
    }
    
    private boolean drinkPotion(final Potion potion)
    {
        if (potion == null)
        {
            GameEventsLogger.getLogger().severe(LogMessages.ILLEGAL_ARGUMENT.toString());
            return false;
        }
        if ((this.health + potion.drink()) > HERO_HEALTH)
        {
            this.health = HERO_HEALTH;
        }
        else
        {
            this.health += potion.drink();
        }
        GameEventsLogger.getLogger().info(LogMessages.DRANK_POTION.toString());
        return true;
    }

    private <T extends Item> boolean equipItem(final T toBeEquippedItem)
    {
        if (toBeEquippedItem == null)
        {
            GameEventsLogger.getLogger().severe(LogMessages.ILLEGAL_ARGUMENT.toString());
            return false;
        }
        boolean isToBeEquippedItemEquipped = false;
        boolean isPreviousEquippedItemMovedToInventory = true;
        Item previousEquippedItem = null;
        ItemType itemType = toBeEquippedItem.getType();
        switch (itemType)
        {
            case WEAPON:
            {
                previousEquippedItem = this.equipment.removeWeapon();
                if (previousEquippedItem != null)
                {
                    isPreviousEquippedItemMovedToInventory = this.inventory.addItem(previousEquippedItem);
                    if (!isPreviousEquippedItemMovedToInventory)
                    {
                        break;
                    }
                }
                isToBeEquippedItemEquipped = this.equipment.equipWeapon((Weapon) toBeEquippedItem);
                break;
            }
            case PROTECTION:
            {
                previousEquippedItem = this.equipment.removeShield();
                if (previousEquippedItem != null)
                {
                    isPreviousEquippedItemMovedToInventory = this.inventory.addItem(previousEquippedItem);
                    if (!isPreviousEquippedItemMovedToInventory)
                    {
                        break;
                    }
                }
                isToBeEquippedItemEquipped = this.equipment.equipShield((Shield) toBeEquippedItem);
                break;
            }
            default: 
                GameEventsLogger.getLogger().info(LogMessages.CANT_EQUIP_INVALID_ITEM_TYPE.toString());
        }
        
        //==============================================================================
        // undo previous actions if some action not successes / create previous equipment and inventory state
        if (!isPreviousEquippedItemMovedToInventory)
        {
            return false;
        }
        else if (!isToBeEquippedItemEquipped)
        {
            if (previousEquippedItem != null)
            {
                this.inventory.removeItem(previousEquippedItem);
                switch (itemType) {
                    case WEAPON:
                        this.equipment.equipWeapon((Weapon) previousEquippedItem);
                        break;
                    case PROTECTION:
                        this.equipment.equipShield((Shield) previousEquippedItem);
                        break;
                    default:
                        GameEventsLogger.getLogger().info(LogMessages.CANT_EQUIP_INVALID_ITEM_TYPE.toString());
                }
            }
            return false;
        }
        //==============================================================================

        this.onEquipmentChanged();
        return true;
    }

    /* Handle the taken damage by knockbacking the hero */
    private void handleDamageTaken()
    {
        // Check if the travelled distance is enough to end the knockback or we would hit a wall (calculation is done depending on the knockback direction)
        // If it is not enough, move the hero further
        switch (knockbackDirection)
        {
            case BOTTOM:
                if ((currentPosition.y - KNOCKBACK_DISTANCE) >= knockbackStartingPoint.y || 
                    !currentDungeonWorld.isTileAccessible(new Point(currentPosition.x, currentPosition.y + KNOCKBACK_SPEED)))
                {
                    isDamageTaken = false;
                    activeAnimation = AnimationStates.IDLE_RIGHT;
                }
                else
                {
                    currentPosition.y += KNOCKBACK_SPEED;
                }
                break;

            case TOP:
                if ((currentPosition.y + KNOCKBACK_DISTANCE) <= knockbackStartingPoint.y || 
                    !currentDungeonWorld.isTileAccessible(new Point(currentPosition.x, currentPosition.y - KNOCKBACK_SPEED)))
                {
                    isDamageTaken = false;
                    activeAnimation = AnimationStates.IDLE_RIGHT;
                }
                else
                {
                    currentPosition.y -= KNOCKBACK_SPEED;
                }
                break;

            case RIGHT:
                if ((currentPosition.x + KNOCKBACK_DISTANCE) <= knockbackStartingPoint.x || 
                    !currentDungeonWorld.isTileAccessible(new Point(currentPosition.x - KNOCKBACK_SPEED, currentPosition.y)))
                {
                    isDamageTaken = false;
                    activeAnimation = AnimationStates.IDLE_RIGHT;
                }
                else
                {
                    currentPosition.x -= KNOCKBACK_SPEED;
                }
                break;

            case LEFT:
                if ((currentPosition.x - KNOCKBACK_DISTANCE) >= knockbackStartingPoint.x || 
                    !currentDungeonWorld.isTileAccessible(new Point(currentPosition.x + KNOCKBACK_SPEED, currentPosition.y)))
                {
                    isDamageTaken = false;
                    activeAnimation = AnimationStates.IDLE_LEFT;
                }
                else
                {
                    currentPosition.x += KNOCKBACK_SPEED;
                }
                break;

            default:
                GameEventsLogger.getLogger().severe(LogMessages.ILLEGAL_ARGUMENT.toString());
                throw new IllegalArgumentException();
        }

        GameEventsLogger.getLogger().fine(LogMessages.HERO_GOT_DAMAGE.toString());
        GameEventsLogger.getLogger().fine(LogMessages.HERO_KNOCKBACK + ": " + knockbackDirection.toString());
    }

    private void updateWeaponTimer()
    {
    	if(equipment.getWeapon() != null)
    	{
        	if(equipment.getWeapon().getWeaponType() == WeaponType.RANGED)
        	{
        		RangedWeapon weapon = (RangedWeapon)equipment.getWeapon();
        		weapon.updateCooldownTimer();
        	}
    	}
    }

    /* Check for player inputs and initiate an attack accordingly. */
    private void handleAttacks()
    {
    	if(equipment.getWeapon() != null)
    	{
        	if(equipment.getWeapon().getWeaponType() == WeaponType.MELEE)
        	{
        		if (Gdx.input.isKeyPressed(Input.Keys.UP))
                {
                    attackDirection = DamageDirections.BOTTOM;
                    GameEventsLogger.getLogger().finest(LogMessages.KEYBOARD_INPUT + "UP");
                    GameEventsLogger.getLogger().fine(LogMessages.HERO_ATTACKS + ": " + DamageDirections.BOTTOM);
                }

                if (Gdx.input.isKeyPressed(Input.Keys.DOWN))
                {
                    attackDirection = DamageDirections.TOP;
                    GameEventsLogger.getLogger().finest(LogMessages.KEYBOARD_INPUT + "DOWN");
                    GameEventsLogger.getLogger().fine(LogMessages.HERO_ATTACKS + ": " + DamageDirections.TOP);
                }

                if (Gdx.input.isKeyPressed(Input.Keys.RIGHT))
                {
                    attackDirection = DamageDirections.LEFT;
                    GameEventsLogger.getLogger().finest(LogMessages.KEYBOARD_INPUT + "RIGHT");
                    GameEventsLogger.getLogger().fine(LogMessages.HERO_ATTACKS + ": " + DamageDirections.LEFT);
                }

                if (Gdx.input.isKeyPressed(Input.Keys.LEFT))
                {
                    attackDirection = DamageDirections.RIGHT;
                    GameEventsLogger.getLogger().finest(LogMessages.KEYBOARD_INPUT + "LEFT");
                    GameEventsLogger.getLogger().fine(LogMessages.HERO_ATTACKS + ": " + DamageDirections.RIGHT);
                }
        	}
        	else if(equipment.getWeapon().getWeaponType() == WeaponType.RANGED)
        	{
        		RangedWeapon weapon = (RangedWeapon)equipment.getWeapon();
        		if (Gdx.input.isKeyPressed(Input.Keys.UP) && Gdx.input.isKeyPressed(Input.Keys.RIGHT))
                {
        			//45�
        			if(weapon.canShoot())
        			{
        				projectile = new Projectile(
        						currentPosition.x, 
        						currentPosition.y, 
        						45, 
        						weapon.getSpeed(), 
        						weapon.getDamage(), 
        						weapon.getDamageFalloff(), 
        						weapon.getAccuracyFalloff(), 
        						weapon.getDistance(), 
        						weapon.isUnlimitedDistance(), 
        						weapon.getProjectileSize(), 
        						PICKUP_RANGE * 2, 
        						weapon.getProjectileTexture(),
        						currentDungeonWorld);
        				GameEventsLogger.getLogger().finest(LogMessages.KEYBOARD_INPUT + "UP & RIGHT");
                        GameEventsLogger.getLogger().fine(LogMessages.HERO_SHOOTS + ": " + "45�");
        			}
                }
        		
        		if (Gdx.input.isKeyPressed(Input.Keys.UP) && Gdx.input.isKeyPressed(Input.Keys.LEFT))
                {
        			//135�
        			if(weapon.canShoot())
        			{
        				projectile = new Projectile(
        						currentPosition.x, 
        						currentPosition.y, 
        						135, 
        						weapon.getSpeed(), 
        						weapon.getDamage(), 
        						weapon.getDamageFalloff(), 
        						weapon.getAccuracyFalloff(), 
        						weapon.getDistance(), 
        						weapon.isUnlimitedDistance(), 
        						weapon.getProjectileSize(), 
        						PICKUP_RANGE * 2, 
        						weapon.getProjectileTexture(),
        						currentDungeonWorld);
        				GameEventsLogger.getLogger().finest(LogMessages.KEYBOARD_INPUT + "UP & LEFT");
                        GameEventsLogger.getLogger().fine(LogMessages.HERO_SHOOTS + ": " + "135�");
        			}
                }
        		if (Gdx.input.isKeyPressed(Input.Keys.DOWN) && Gdx.input.isKeyPressed(Input.Keys.RIGHT))
                {
        			//315�
        			if(weapon.canShoot())
        			{
        				projectile = new Projectile(
        						currentPosition.x, 
        						currentPosition.y, 
        						315, 
        						weapon.getSpeed(), 
        						weapon.getDamage(), 
        						weapon.getDamageFalloff(), 
        						weapon.getAccuracyFalloff(), 
        						weapon.getDistance(), 
        						weapon.isUnlimitedDistance(), 
        						weapon.getProjectileSize(), 
        						PICKUP_RANGE * 2, 
        						weapon.getProjectileTexture(),
        						currentDungeonWorld);
        				GameEventsLogger.getLogger().finest(LogMessages.KEYBOARD_INPUT + "DOWN & RIGHT");
                        GameEventsLogger.getLogger().fine(LogMessages.HERO_SHOOTS + ": " + "315�");
        			}
                }
        		if (Gdx.input.isKeyPressed(Input.Keys.DOWN) && Gdx.input.isKeyPressed(Input.Keys.LEFT))
                {
        			//225�
        			if(weapon.canShoot())
        			{
        				projectile = new Projectile(
        						currentPosition.x, 
        						currentPosition.y, 
        						225, 
        						weapon.getSpeed(), 
        						weapon.getDamage(), 
        						weapon.getDamageFalloff(), 
        						weapon.getAccuracyFalloff(), 
        						weapon.getDistance(), 
        						weapon.isUnlimitedDistance(), 
        						weapon.getProjectileSize(), 
        						PICKUP_RANGE * 2, 
        						weapon.getProjectileTexture(),
        						currentDungeonWorld);
        				GameEventsLogger.getLogger().finest(LogMessages.KEYBOARD_INPUT + "DOWN & LEFT");
                        GameEventsLogger.getLogger().fine(LogMessages.HERO_SHOOTS + ": " + "225�");
        			}
                }
        		
        		if (Gdx.input.isKeyPressed(Input.Keys.UP))
                {
        			//90�
        			if(weapon.canShoot())
        			{
        				projectile = new Projectile(
        						currentPosition.x, 
        						currentPosition.y, 
        						90, 
        						weapon.getSpeed(), 
        						weapon.getDamage(), 
        						weapon.getDamageFalloff(), 
        						weapon.getAccuracyFalloff(), 
        						weapon.getDistance(), 
        						weapon.isUnlimitedDistance(), 
        						weapon.getProjectileSize(), 
        						PICKUP_RANGE * 2, 
        						weapon.getProjectileTexture(),
        						currentDungeonWorld);
        				GameEventsLogger.getLogger().finest(LogMessages.KEYBOARD_INPUT + "UP");
                        GameEventsLogger.getLogger().fine(LogMessages.HERO_SHOOTS + ": " + "90�");
        			}
                }
        		
        		if (Gdx.input.isKeyPressed(Input.Keys.RIGHT))
                {
        			//0�
        			if(weapon.canShoot())
        			{
        				projectile = new Projectile(
        						currentPosition.x, 
        						currentPosition.y, 
        						0, 
        						weapon.getSpeed(), 
        						weapon.getDamage(), 
        						weapon.getDamageFalloff(), 
        						weapon.getAccuracyFalloff(), 
        						weapon.getDistance(), 
        						weapon.isUnlimitedDistance(), 
        						weapon.getProjectileSize(), 
        						PICKUP_RANGE * 2, 
        						weapon.getProjectileTexture(),
        						currentDungeonWorld);
        				GameEventsLogger.getLogger().finest(LogMessages.KEYBOARD_INPUT + "RIGHT");
                        GameEventsLogger.getLogger().fine(LogMessages.HERO_SHOOTS + ": " + "0�");
        			}
                }
        		
        		if (Gdx.input.isKeyPressed(Input.Keys.DOWN))
                {
        			//270�
        			if(weapon.canShoot())
        			{
        				projectile = new Projectile(
        						currentPosition.x, 
        						currentPosition.y, 
        						270, 
        						weapon.getSpeed(), 
        						weapon.getDamage(), 
        						weapon.getDamageFalloff(), 
        						weapon.getAccuracyFalloff(), 
        						weapon.getDistance(), 
        						weapon.isUnlimitedDistance(), 
        						weapon.getProjectileSize(), 
        						PICKUP_RANGE * 2, 
        						weapon.getProjectileTexture(),
        						currentDungeonWorld);
        				GameEventsLogger.getLogger().finest(LogMessages.KEYBOARD_INPUT + "DOWN");
                        GameEventsLogger.getLogger().fine(LogMessages.HERO_SHOOTS + ": " + "270�");
        			}
                }
        		
        		if (Gdx.input.isKeyPressed(Input.Keys.LEFT))
                {
        			//180�
        			if(weapon.canShoot())
        			{
        				projectile = new Projectile(
        						currentPosition.x, 
        						currentPosition.y, 
        						180, 
        						weapon.getSpeed(), 
        						weapon.getDamage(), 
        						weapon.getDamageFalloff(), 
        						weapon.getAccuracyFalloff(), 
        						weapon.getDistance(), 
        						weapon.isUnlimitedDistance(), 
        						weapon.getProjectileSize(), 
        						PICKUP_RANGE * 2, 
        						weapon.getProjectileTexture(),
        						currentDungeonWorld);
        				GameEventsLogger.getLogger().finest(LogMessages.KEYBOARD_INPUT + "LEFT");
                        GameEventsLogger.getLogger().fine(LogMessages.HERO_SHOOTS + ": " + "180�");
        			}
                }
        	}
    	}
    	else
    	{
    		if (Gdx.input.isKeyPressed(Input.Keys.UP))
            {
                attackDirection = DamageDirections.BOTTOM;
                GameEventsLogger.getLogger().finest(LogMessages.KEYBOARD_INPUT + "UP");
                GameEventsLogger.getLogger().fine(LogMessages.HERO_ATTACKS + ": " + DamageDirections.BOTTOM);
            }

            if (Gdx.input.isKeyPressed(Input.Keys.DOWN))
            {
                attackDirection = DamageDirections.TOP;
                GameEventsLogger.getLogger().finest(LogMessages.KEYBOARD_INPUT + "DOWN");
                GameEventsLogger.getLogger().fine(LogMessages.HERO_ATTACKS + ": " + DamageDirections.TOP);
            }

            if (Gdx.input.isKeyPressed(Input.Keys.RIGHT))
            {
                attackDirection = DamageDirections.LEFT;
                GameEventsLogger.getLogger().finest(LogMessages.KEYBOARD_INPUT + "RIGHT");
                GameEventsLogger.getLogger().fine(LogMessages.HERO_ATTACKS + ": " + DamageDirections.LEFT);
            }

            if (Gdx.input.isKeyPressed(Input.Keys.LEFT))
            {
                attackDirection = DamageDirections.RIGHT;
                GameEventsLogger.getLogger().finest(LogMessages.KEYBOARD_INPUT + "LEFT");
                GameEventsLogger.getLogger().fine(LogMessages.HERO_ATTACKS + ": " + DamageDirections.RIGHT);
            }
    	}
    }

    /* Check for player inputs and move the character accordingly. */
    private void handlePlayerMovement()
    {
        // Flag if the hero has moved will be used to determine if the idle animation should be set again
        boolean isHeroMoved = false;
        // Current animation will be used to determine the moving animation when moving up/down
        AnimationStates currentAnimation = activeAnimation;

        // Save current position to edit it when walking the character
        Point newPosition = new Point(this.currentPosition);
        Point oldPosition = new Point(this.currentPosition);

        // Register walking inputs for y axis and set walking animation
        if (Gdx.input.isKeyPressed(Input.Keys.W))
        {
            GameEventsLogger.getLogger().finest(LogMessages.KEYBOARD_INPUT + "W");
            newPosition.y += MOVEMENT_SPEED;
            // We keep the direction (left or right) of the previous animation
            if (currentAnimation == AnimationStates.RUNNING_LEFT || currentAnimation == AnimationStates.IDLE_LEFT)
            {
                activeAnimation = AnimationStates.RUNNING_LEFT;
            }
            else
            {
                activeAnimation = AnimationStates.RUNNING_RIGHT;
            }
            isHeroMoved = true;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S))
        {
            GameEventsLogger.getLogger().finest(LogMessages.KEYBOARD_INPUT + "S");
            newPosition.y -= MOVEMENT_SPEED;
            // We keep the direction (left or right) of the previous animation
            if (currentAnimation == AnimationStates.RUNNING_LEFT || currentAnimation == AnimationStates.IDLE_LEFT)
            {
                activeAnimation = AnimationStates.RUNNING_LEFT;
            }
            else
            {
                activeAnimation = AnimationStates.RUNNING_RIGHT;
            }
            isHeroMoved = true;
        }
        // We only set the y component here to make it possible to run into a wall (collide)
        // and also walk to the side at the same time
        if (currentDungeonWorld.isTileAccessible(newPosition))
        {
            this.currentPosition.y = newPosition.y;
        }
        // If we would be out of bounds we reset the y component
        else
        {
            newPosition.y = oldPosition.y;
        }

        // Register walking inputs for x axis and set walking animation
        if (Gdx.input.isKeyPressed(Input.Keys.D))
        {
            GameEventsLogger.getLogger().finest(LogMessages.KEYBOARD_INPUT + "D");
            newPosition.x += MOVEMENT_SPEED;
            activeAnimation = AnimationStates.RUNNING_RIGHT;
            isHeroMoved = true;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A))
        {
            GameEventsLogger.getLogger().finest(LogMessages.KEYBOARD_INPUT + "A");
            newPosition.x -= MOVEMENT_SPEED;
            activeAnimation = AnimationStates.RUNNING_LEFT;
            isHeroMoved = true;
        }

        // We do the same for the x component to allow walking along a wall
        if (currentDungeonWorld.isTileAccessible(newPosition))
        {
            this.currentPosition.x = newPosition.x;
        }

        // Set idle animation when the character has not moved
        if (!isHeroMoved)
        {
            // If the previous animation was turned to the left stay left
            if (activeAnimation == AnimationStates.IDLE_LEFT || activeAnimation == AnimationStates.RUNNING_LEFT)
            {
                activeAnimation = AnimationStates.IDLE_LEFT;
            }
            // If the previous animation was turned to the right stay right
            else if(activeAnimation == AnimationStates.IDLE_RIGHT || activeAnimation == AnimationStates.RUNNING_RIGHT)
            {
                activeAnimation = AnimationStates.IDLE_RIGHT;
            }
        }
        else
        {
            String oldPositionString = oldPosition.x + "/" + oldPosition.y;
            String newPositionString = newPosition.x + "/" + newPosition.y;
            GameEventsLogger.getLogger().fine(LogMessages.HERO_MOVED + ": " + oldPositionString + " -> " + newPositionString);
        }
    }
    
    /* Should be called, when new weapon/shield equipped OR weapon/shield removed to update heroes damage */
    private void onEquipmentChanged()
    {
        Weapon equippedWeapon = this.equipment.getWeapon();
        if (equippedWeapon != null)
        {
            this.damage = equippedWeapon.getDamage();
        }
        else 
        {
            this.damage = ATTACK_DAMAGE_UNARMED;
        }

        Shield equippedShield = this.equipment.getShield();
        if (equippedShield != null)
        {
            this.defense = equippedShield.getDefense();
        }
        else 
        {
            this.defense = DEFENSE_UNARMED;
        }
    }

    /* Checks, if user wants to use or drop items from inventory. */
    private void handleInputsForInventoryHandling()
    {
        if (Gdx.input.isKeyPressed(Input.Keys.L))
        {
            hasInventoryContentChanged = true;
        }

        // number 0 in Input.Keys starts at enum value 7
        int numbersOffsetInInputKeys = 7;
        int numbersNumPadOffsetInInputKeys = 144;
        int indexOfToBeUsedItem = -1;
        int indexOfToBeDroppedItem = -1;
        for (int i = 1; i <= this.inventory.getCapacity(); i++)
        {
            // USE (equip weapon/shield, drink potion, read spell, ...) INVENTORY ITEM
            // Key U for "use" + Number for index in inventory + 1
            if (Gdx.input.isKeyPressed(Input.Keys.U) &&
                (Gdx.input.isKeyPressed(numbersOffsetInInputKeys + i - 1) || Gdx.input.isKeyPressed(numbersNumPadOffsetInInputKeys + i - 1)))
            {
                indexOfToBeUsedItem = i - 1;
                break;
            }

            // DROP/REMOVE INVENTORY ITEM
            // Key R for "remove" + Number for index in inventory + 1
            if (Gdx.input.isKeyPressed(Input.Keys.R) &&
                (Gdx.input.isKeyPressed(numbersOffsetInInputKeys + i - 1) || Gdx.input.isKeyPressed(numbersNumPadOffsetInInputKeys + i - 1)))
            {
                indexOfToBeDroppedItem = i - 1;
                break;
            }
        }

        boolean itemShouldBeUsed = (indexOfToBeUsedItem != -1);
        boolean itemShouldBeDropped = (indexOfToBeDroppedItem != -1);
        if (itemShouldBeUsed)
        {
            Item toBeUsedItem = this.inventory.getItemAt(indexOfToBeUsedItem);
            if (toBeUsedItem != null)
            {
                if(!this.useItem(toBeUsedItem))
                {
                    GameEventsLogger.getLogger().severe(LogMessages.FAILED_TO_USE_ITEM.toString());
                }
            }
            else
            {
                GameEventsLogger.getLogger().info(LogMessages.CAN_NOT_USE_EMPTY_PLACE.toString());
            }
        }
        else if (itemShouldBeDropped)
        {
            Item droppedItem = this.inventory.getItemAt(indexOfToBeDroppedItem);
            if (droppedItem != null)
            {
                this.inventory.emptyInventorySlot(indexOfToBeDroppedItem);
                hasInventoryContentChanged = true;
                this.heroesItemObserver.onItemDropped(droppedItem);
            }
        }
    }
}