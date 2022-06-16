package newgame.items;

import java.util.concurrent.ThreadLocalRandom;

import newgame.items.weapons.Weapon;
import newgame.items.weapons.meleeweapons.RegularSword;
import newgame.items.weapons.rangedweapons.Staff;

/**
 * Generates a random item
 * @author Dominik Haacke
 *
 */
public class ItemGenerator 
{
	public static Item generateItem()
	{
		int typeOfItem = ThreadLocalRandom.current().nextInt(0, 2);
		switch (typeOfItem) 
		{
			case 0: 
			{
				int weaponType = ThreadLocalRandom.current().nextInt(0, 1);
				switch (weaponType)
				{
					case 0:
						return new RegularSword();
					case 1:
						return new Staff(true, 0.4f);
				}
				
			}
			case 1: 
			{
				float defense = ThreadLocalRandom.current().nextFloat() * 5;
				float durability = ThreadLocalRandom.current().nextFloat() * 5 + 0.1f;
				return new Shield(defense, durability);
			}
			case 2: 
			{
				return new Potion();
			}
		}
		return null;
	}
}
