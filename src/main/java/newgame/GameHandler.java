package newgame;

import de.fhbielefeld.pmdungeon.vorgaben.game.Controller.MainController;
import de.fhbielefeld.pmdungeon.vorgaben.tools.Point;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import newgame.DamageDetails.DamageDirections;
import newgame.characters.*;
import newgame.gui.HudHandler;
import newgame.items.DungeonItem;
import newgame.items.Item;
import newgame.items.ItemType;
import newgame.items.Potion;
import newgame.items.Shield;
import newgame.items.weapons.Projectile;
import newgame.items.weapons.Weapon;
import newgame.items.weapons.meleeweapons.RegularSword;
import newgame.items.weapons.rangedweapons.Staff;
import newgame.logger.GameEventsLogger;
import newgame.logger.InventoryConsoleLogVisitor;
import newgame.logger.InventoryHudLogVisitor;
import newgame.logger.LogMessages;

/** Game handler class that will be used to execute the game logic
 *
 * @author Maxim Fründt
 */
public class GameHandler extends MainController implements HeroObserver
{
    /* Number of easy monsters that may spawn on a level */
    private final static int NUMBER_OF_EASY_MONSTERS = 90;

    /* Number of hard monsters that may spawn on a level */
    private final static int NUMBER_OF_HARD_MONSTERS = 9;

    /* Number of frames that an attack will be active (= deal damage) */
    private final static int NUMBER_OF_ATTACK_FRAMES = 30;

    /* Number of frames between two character contacts */
    private final static int CONTACT_FRAMES = 30;

    /* Playable hero character */
    private Hero hero;

    /* Handler of the game hud */
    private HudHandler hud;

    /* List of all monsters of the current dungeon level */
    private List<Monster> monsters;

    /* List of all items in the current dungeon level */
    private List<DungeonItem> items;
    
    /* List of all projectiles in the current dungeon level */
    private List<Projectile> projectiles;

    /* Handler for item collisions */
    private CollisionHandler<DungeonItem> collisionHandler;

    /* Counter for attack frames
     * After the counter reached the NUMBER_OF_ATTACK_FRAMES the attack of the hero will be ended
     */
    private int attackFrameCounter = 0;

    private int contactFrameCounter = -1;

    /** Setup method that will be called upon starting the game
     *
     */
    @Override
    protected void setup()
    {
        // Create new list of monsters, items and a hero
        monsters = new ArrayList<>();
        
        items = new ArrayList<>();
        
        projectiles = new ArrayList<>();
        
        hero = new Hero();
        this.hero.register(this);

        collisionHandler = new CollisionHandler<>();

        hud = new HudHandler();

        // Add hero to the entity controller and set the camera to follow the hero
        entityController.addEntity(hero);
        if (!hud.addPlayerEntity(hero, hero.getInventorySize()))
        {
            GameEventsLogger.getLogger().severe(LogMessages.GUI_ENTITY_NULL.toString());
        }

        camera.follow(hero);
    }

    /** Method that will be executed on the beginning of every frame
     *
     */
    @Override
    protected void beginFrame()
    {
        startTime = System.nanoTime();
    }

    long startTime, elapsedTime = 0;
    double[] timeMeasurements = new double[11];
    int currentMeasurement = 0;

    /** Method that will be executed on the end of every frame
     *
     */
    @Override
    protected void endFrame()
    {


        hud.update();

        if (contactFrameCounter == -1)
        {
            for (Monster monster : monsters)
            {
                // Check if damage has been dealt to the hero by a monster
                if (monster.handleAttacks(hero))
                {
                    contactFrameCounter++;
                }

                // Check if damage has been dealt to a monster by the hero
                DamageDetails damageDetails = hero.checkMonsterContact(monster.getPosition());

                if (damageDetails.hasPlayerContact())
                {
                    monster.receiveDamage(damageDetails);
                    hero.resetAttack();
                    contactFrameCounter++;
                }
            }
        }
        else
        {
            if (contactFrameCounter < CONTACT_FRAMES)
            {
                contactFrameCounter++;
            }
            else
            {
                contactFrameCounter = -1;
            }
        }

        for(Monster monster : monsters)
        {
        	if(monster.getProjectile() != null)
        	{
        		projectiles.add(monster.getProjectile());
        		entityController.addEntity(monster.getProjectile());
        		monster.removeProjectile();
        	}
        }
        
        if(hero.getProjectile() != null)
        {
        	projectiles.add(hero.getProjectile());
        	entityController.addEntity(hero.getProjectile());
        	hero.removeProjectile();
        }
        
        for(Projectile projectile : projectiles)
        {
	        boolean projectileContact = hero.checkForAnyContact(projectile.getSize(), projectile.getPosition());
	        if(projectileContact)
	        {
	        	hero.receiveFlatDamage(projectile.getDamage());
	        	
	        	projectile.destroy();
	        }
	        	
	        for(Monster monster : new ArrayList<>(monsters))
	        {
	        	boolean monsterProjectileContact = monster.checkForAnyContact(projectile.getSize(), projectile.getPosition());
	            if(monsterProjectileContact)
	            {
	            	monster.receiveFlatDamage(projectile.getDamage());
	                projectile.destroy();
	            }
	        }
        }

        this.hero.handleItemPickUp(this.items);

        if (levelController.checkForTrigger(hero.getPosition()))
        {
            // Remove all current monsters from the entity controller
            for (Monster monster : monsters)
            {
                entityController.removeEntity(monster);
            }
            
            for(DungeonItem item : items)
            {
            	entityController.removeEntity(item);
            }
            
            for(Projectile projectile : projectiles)
            {
            	entityController.removeEntity(projectile);
            }

            // Remove all monsters, chests and items and trigger the next stage
            collisionHandler.clear();
            monsters.clear();
            items.clear();
            projectiles.clear();

            levelController.triggerNextStage();
        }
        
        // Check if any monster is dead, if so remove it
        for (Monster monster : new ArrayList<>(monsters))
        {
            if (monster.isCharacterDead())
            {
                hero.addExperience(monster.getExperiencePoints());
                monsters.remove(monster);
                entityController.removeEntity(monster);
            }
        }

        List<DungeonItem> collectableItems = collisionHandler.getCollisions(hero.getPosition());

        for (DungeonItem item : collectableItems)
        {
            if (item.isPickedUp())
            {
                hero.addItemToInventory(item.getItem());
                collisionHandler.removeEntity(item.getPosition(), item);
                entityController.removeEntity(item);
                items.remove(item);
            }
        }

        for(DungeonItem item : new ArrayList<>(items))
        {
            // if new items added (when hero dropped some), add them to controller
            if (!entityController.getList().contains(item))
            {
                entityController.addEntity(item);
                item.setLocation(levelController.getDungeon(), new Point(this.hero.getPosition()));
                collisionHandler.addEntity(item.getPosition(), item);
            }
        }
        
        for(Projectile projectile : new ArrayList<>(projectiles))
        {
        	if(projectile.isDestroyed())
        	{
        		projectiles.remove(projectile);
        		entityController.removeEntity(projectile);
        	}
        }

        if (hero.getHasInventoryContentChanged())
        {
            hero.logInventoryItems(new InventoryConsoleLogVisitor());
            hero.logInventoryItems(new InventoryHudLogVisitor(hud));
        }

        // Increase the attack frame counter if an attack is ongoing, else reset it to 0
        if (hero.getAttackDirection() == DamageDirections.NONE)
        {
            attackFrameCounter = 0;
        }
        else
        {
            attackFrameCounter++;
        }

        // If the frame for an attack has expired reset the attack state of the hero
        if (attackFrameCounter >= NUMBER_OF_ATTACK_FRAMES)
        {
            attackFrameCounter = 0;
            hero.resetAttack();
        }

        if (hero.isCharacterDead())
        {
            restartGame();
        }

        elapsedTime = System.nanoTime() - startTime;

        //timeMeasurements[currentMeasurement++] = elapsedTime;

        //if (currentMeasurement == 11)
        //{
        //    currentMeasurement = 0;
        //    Arrays.sort(timeMeasurements);
        //    GameEventsLogger.getLogger().info("Time: " + timeMeasurements[5]);
        //}

        System.out.printf("%d\n", elapsedTime);
    }

    /** Method that will be called upon loading the level
     *
     */
    @Override
    public void onLevelLoad()
    {
        // Create monsters
        for (int i = 0; i < NUMBER_OF_EASY_MONSTERS; i++)
        {
            monsters.add(new EasyMonster(hero));
        }

        for (int i = 0; i < NUMBER_OF_HARD_MONSTERS; i++)
        {
            monsters.add(new HardMonster(hero));
        }

        monsters.add(new MiniBoss(hero));
        
        items.add(new DungeonItem(new RegularSword()));
        items.add(new DungeonItem(new Shield(5, 3)));
        items.add(new DungeonItem(new Potion()));
        
        //Staff
        items.add(new DungeonItem(new Staff(true, 0.4f))); 

        //Add items to the entity controller and set their level to the current dungeon
        for(DungeonItem item : items)
        {
        	entityController.addEntity(item);
        	item.setLocation(levelController.getDungeon());
        	collisionHandler.addEntity(item.getPosition(), item);
        }
        
        // Set the level of the hero to the current dungeon
        hero.setLevel(levelController.getDungeon());

        // Add monsters to the entity controller and set their level to the current dungeon
        for (Monster monster : monsters)
        {
            entityController.addEntity(monster);
            monster.setLevel(levelController.getDungeon());
        }

        GameEventsLogger.getLogger().info(LogMessages.LEVEL_LOADED.toString());
    }

    private void restartGame()
    {
        entityController.removeEntity(hero);
        hud.removePlayerEntity();

        for (Monster monster : monsters)
        {
            entityController.removeEntity(monster);
        }
        for (DungeonItem item : items)
        {
            entityController.removeEntity(item);
            collisionHandler.removeEntity(item.getPosition(), item);
        }
        for (Projectile projectile : projectiles)
        {
        	entityController.removeEntity(projectile);
        }
        setup();
        onLevelLoad();
    }

    /**
     * Event handler for when hero dropped an item
     * Item will be added to dungeon item list and will be drawn in dungeon
     * @param droppedItem Item that is dropped by the hero
     */
    @Override
    public void onItemDropped(Item droppedItem)
    {
        if (droppedItem == null)
        {
            GameEventsLogger.getLogger().severe(LogMessages.ILLEGAL_ARGUMENT.toString());
            return;
        }
        this.items.add(new DungeonItem(droppedItem));
    }
}