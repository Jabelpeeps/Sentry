package net.aufdemrand.sentry;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.bukkit.Effect;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
/////////////////////////
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Horse;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Spider;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Witch;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import net.aufdemrand.denizen.npc.traits.HealthTrait;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.ai.GoalController;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.MobType;
import net.citizensnpcs.api.trait.trait.Owner;
import net.citizensnpcs.util.NMS;
import net.citizensnpcs.util.PlayerAnimation;
//Version Specifics
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EntityPotion;
import net.minecraft.server.v1_8_R3.PacketPlayOutAnimation;


public class SentryInstance {

	Sentry sentry;
	
	Location _projTargetLostLoc;
	Location spawnLocation = null;
	
	int strength = 1;
	int armorValue = 0;
	int epCount = 0;
	int nightVision = 16;
	int respawnDelay = 10;
	int sentryRange = 10;
	int followDistance  = 16;
	int mountID = -1;
	int warningRange = 0;

	float sentrySpeed =  1.0f;
	
	double attackRate = 2.0;
	double healRate = 0.0;
	double sentryWeight = 1.0;
	double sentryMaxHealth = 20.0;

	boolean killsDropInventory = true;
	boolean dropInventory = false;
	boolean targetable = true;
	boolean invincible = false;
	boolean loaded = false;
	boolean acceptsCriticals = true;
	boolean iWillRetaliate = true;
    boolean ignoreLOS;
    boolean mountCreated = false;
	
	private GiveUpStuckAction giveup = new GiveUpStuckAction( this );

	public String greetingMsg = "&a<NPC> says: Welcome, <PLAYER>!";
	public String warningMsg = "&a<NPC> says: Halt! Come no further!";
	
	private Map<Player, Long> warningsGiven = new HashMap<Player, Long>();
	private Set<Player> _myDamamgers = new HashSet<Player>();

	public LivingEntity guardEntity = null;
	public LivingEntity meleeTarget = null;
	public String guardTarget = null;

	PacketPlayOutAnimation healAnimation = null;
	
	public List<String> ignoreTargets = new ArrayList<String>();
	public List<String> validTargets = new ArrayList<String>();

	public Set<String> _ignoreTargets = new HashSet<String>();
	public Set<String> _validTargets = new HashSet<String>();

	// TODO why are we saving four instances of the system time?
	long isRespawnable = System.currentTimeMillis();	
	long oktoFire = System.currentTimeMillis();
	long oktoheal = System.currentTimeMillis();
	long oktoreasses= System.currentTimeMillis();
	long okToTakedamage = 0;
	
	public List<PotionEffect> weaponSpecialEffects = null;
	ItemStack potiontype = null;
	public LivingEntity projectileTarget;
	Random random = new Random();
	
	public SentryStatus myStatus = SentryStatus.isDYING;
	public AttackType myAttacks;
	public SentryTrait myTrait;
	public NPC myNPC = null;

	private int taskID = 0;
	
	public SentryInstance( Sentry plugin ) {
		sentry = plugin;
		isRespawnable = System.currentTimeMillis();
	}
	
	public void initialize() {
		
		LivingEntity myEntity = getMyEntity();

		// check for illegal values
		if ( sentryWeight <= 0 ) 		sentryWeight = 1.0;
		if ( attackRate > 30 )			attackRate = 30.0;
		if ( sentryMaxHealth < 0 )		sentryMaxHealth = 0;
		if ( sentryRange < 1 )			sentryRange = 1;
		if ( sentryRange > 200 )		sentryRange = 200;
		if ( sentryWeight <= 0 )		sentryWeight = 1.0;
		if ( respawnDelay < -1 )		respawnDelay = -1;
		if ( spawnLocation == null ) 	spawnLocation = myEntity.getLocation();
		
		// Allow Denizen to handle the sentry's health if it is active.
		if ( Sentry.denizenActive ) {
			if ( myNPC.hasTrait( HealthTrait.class ) ) myNPC.removeTrait( HealthTrait.class );
		}

		// disable citizens respawning, because Sentry doesn't always raise EntityDeath
		myNPC.data().set( "respawn-delay", -1 );

		setHealth( sentryMaxHealth );

		_myDamamgers.clear();
		myStatus = SentryStatus.isLOOKING;
		
		faceForward();

		healAnimation = new PacketPlayOutAnimation( ((CraftEntity) myEntity).getHandle(), 6);

		//	Packet derp = new net.minecraft.server.Packet15Place();
		
		if ( guardTarget == null ) 
			myNPC.teleport( spawnLocation, TeleportCause.PLUGIN ); //it should be there... but maybe not if the position was saved elsewhere.

		NavigatorParameters navigatorParams = myNPC.getNavigator().getDefaultParameters();
		float myRange = navigatorParams.range();

		if ( myRange < sentryRange + 5 ) {
			myRange = sentryRange + 5;
		}

		myNPC.data().set( NPC.DEFAULT_PROTECTED_METADATA, false );
		myNPC.data().set( NPC.TARGETABLE_METADATA, targetable );
		
		navigatorParams.range( myRange );
		navigatorParams.stationaryTicks( 5 * 20 );
		navigatorParams.useNewPathfinder( false );
		
		// TODO why is this disabled?
		//	myNPC.getNavigator().getDefaultParameters().stuckAction(new BodyguardTeleportStuckAction(this, this.plugin));

		// plugin.getServer().broadcastMessage("NPC GUARDING!");

		if ( myEntity instanceof Creeper )
			navigatorParams.attackStrategy( new CreeperAttackStrategy() );
		else if ( myEntity instanceof Spider )
			navigatorParams.attackStrategy( new SpiderAttackStrategy( sentry ) );
		
		processTargets();

		if ( taskID == 0 ) {
			taskID = sentry.getServer().getScheduler()
									   .scheduleSyncRepeatingTask( sentry, 
											   					   new SentryLogic(), 
											   					   40 + myNPC.getId(),  
											   					   sentry.logicTicks );
		}
	}

	public void cancelRunnable() {
		if ( taskID != 0 ) 
			sentry.getServer().getScheduler().cancelTask( taskID );
	}

	public boolean hasTargetType( int type ) {
		return ( targets & type ) == type;
	}
	public boolean hasIgnoreType( int type ) {
		return ( ignores & type ) == type;
	}

	public boolean isIgnored( LivingEntity aTarget ) {
		
		if ( aTarget == guardEntity ) return true;
		if ( ignores == 0 ) return false;
		if ( hasIgnoreType( all ) ) return true;

		if ( CitizensAPI.getNPCRegistry().isNPC( aTarget ) ) {

			if ( hasIgnoreType( npcs ) ) 
				return true;

			NPC targetNpc = CitizensAPI.getNPCRegistry().getNPC( aTarget );

			if ( targetNpc != null ) {

				if ( hasIgnoreType( namednpcs ) && ignoresContain( "NPC:" + targetNpc.getName() ) )
						return true;

				if ( hasIgnoreType( permGroups ) ) {
					
					OfflinePlayer player = sentry.getServer().getOfflinePlayer( targetNpc.getTrait( Owner.class ).getOwnerId() );
					
					if ( checkGroups4Ignores( aTarget.getWorld(), player ) ) 
							return true;
//					// check world permission groups
//					if ( checkGroups4Ignores( VaultBridge.perms.getPlayerGroups( aTarget.getWorld().getName(), player ) ) ) 
//							return true;
//					
//					// check global permission groups
//					if ( checkGroups4Ignores( VaultBridge.perms.getPlayerGroups( (String) null, player ) ) ) 
//							return true;
				}
			}
		} else if ( aTarget instanceof Player ) {

			if ( hasIgnoreType( allplayers ) ) return true;
			
			Player player = (Player) aTarget;
			String name = player.getName();

			if ( hasIgnoreType( namedplayers ) && ignoresContain( "PLAYER:" + name ) ) 
					return true;

			if ( hasIgnoreType( owner ) && name.equalsIgnoreCase( myNPC.getTrait( Owner.class ).getOwner() ) ) 
					return true;

			if ( hasIgnoreType( permGroups ) && checkGroups4Ignores( aTarget.getWorld(), player ) ) 
					return true;
				
//				// check world permission groups
//				if ( checkGroups4Ignores( VaultBridge.perms.getPlayerGroups( aTarget.getWorld().getName(), player ) ) ) 
//						return true;
//				
//				// check global permission groups
//				if ( checkGroups4Ignores( VaultBridge.perms.getPlayerGroups( (String) null, player ) ) ) 
//						return true;
//			}

			if ( hasIgnoreType( towny ) ) {
				
				String[] info = TownyBridge.getResidentTownyInfo( player );

				if ( info[1] != null 
					&& ignoresContain( "TOWN:" + info[1] ) )	
							return true;

				if ( info[0] != null 
					&& ignoresContain( "NATION:" + info[0] ) )	
							return true;
			}

			if ( hasIgnoreType( faction ) ) {
				
				String factionName = FactionsBridge.getFactionsTag( player );
				
				if ( factionName != null 
					&& ignoresContain( "FACTION:" + factionName ) )
							return true;
			}
			
			if ( hasIgnoreType( war ) ) {
				
				String team = WarBridge.getWarTeam( player );
				
				if ( team != null
					&& ignoresContain( "WARTEAM:" + team ) )
							return true;
			}
			
			// TODO add boolean in Sentry to record this is active (and add to config saving and loading)
			if ( hasIgnoreType( mcTeams ) ) {
				
				String team = ScoreboardTeamsBridge.getMCTeamName( player );

				if ( team != null && ignoresContain( "TEAM:" + team ) )	
							return true;
			}
			
			if ( hasIgnoreType( clans ) ) {
				
				String clan = SimpleClansBridge.getClan( player );

				if ( clan != null && ignoresContain( "CLAN:" + clan ) )
							return true;
			}
		}
		else if ( aTarget instanceof Monster && hasIgnoreType( monsters ) ) 
						return true;

		else if ( hasIgnoreType( namedentities ) && ignoresContain( "ENTITY:" + aTarget.getType() ) )	
						return true;

		return false;
	}

	private boolean checkGroups4Ignores( World world, OfflinePlayer player ) {
		// check world permission groups & then global permission groups if needed.
		return checkGroups4Ignores( VaultBridge.perms.getPlayerGroups( world.getName(), player ) ) 
				|| checkGroups4Ignores( VaultBridge.perms.getPlayerGroups( (String) null, player ) );
	}
	private boolean checkGroups4Ignores( String[] groups ) {
		
		if ( groups != null ) {
			for ( String each : groups )
				if ( ignoresContain( "GROUP:" + each ) )	
					return true;
		}
		return false;
	}
	
	public boolean isTarget( LivingEntity aTarget ) {

		if ( targets == 0 || targets == events ) return false;

		if ( hasTargetType( all ) ) return true;

		if ( aTarget instanceof Player && !CitizensAPI.getNPCRegistry().isNPC( aTarget ) ) {

			if ( hasTargetType( allplayers ) ) return true;
			
			Player player = (Player) aTarget;
			String name = player.getName();
			
			if ( hasTargetType( namedplayers ) && targetsContain( "PLAYER:" + name ) ) 
						return true;

			if ( targetsContain( "ENTITY:OWNER" ) && name.equalsIgnoreCase( myNPC.getTrait( Owner.class ).getOwner() ) ) 
						return true;

			if ( hasTargetType( permGroups ) && checkGroups4Targets( aTarget.getWorld(), player ) ) 
						return true;
//			{
//
//				// check world permission groups
//				if ( checkGroups4Targets( VaultBridge.perms.getPlayerGroups( aTarget.getWorld().getName(), player ) ) ) 
//						return true;
//				
//				// check global permission groups
//				if ( checkGroups4Targets( VaultBridge.perms.getPlayerGroups( (String) null, player ) ) ) 
//						return true;
//			}

			if ( hasTargetType( towny ) || ( hasTargetType( townyenemies ) ) ) {
				
				String[] info = TownyBridge.getResidentTownyInfo( (Player) aTarget );

				if ( hasTargetType( towny ) && info[1] != null 
					&& targetsContain( "TOWN:" + info[1] ) )
							return true;

				if ( info[0] != null ) {
					
					if ( hasTargetType( towny ) && targetsContain( "NATION:" + info[0] ) )
							return true;

					if ( hasTargetType( townyenemies ) )
						for ( String each : NationsEnemies ) 
							if ( TownyBridge.isNationEnemy( each, info[0] ) )	
								return true;
				}
			}

			if ( hasTargetType( faction ) || hasTargetType( factionEnemies ) ) {
				
				String factionName = FactionsBridge.getFactionsTag((Player)aTarget);

				if ( factionName != null ) {
					
					if ( targetsContain( "FACTION:" + factionName ) )
							return true;

					if ( hasTargetType( factionEnemies ) ) 
						for ( String each : FactionEnemies ) 
							if ( FactionsBridge.isFactionEnemy( getMyEntity().getWorld().getName(), 
																each, 
																factionName) ) 
							return true;						
				}	
			}

			if ( hasTargetType( war ) ) {
				
				String team = WarBridge.getWarTeam( (Player) aTarget );

				if ( team != null && targetsContain( "WARTEAM:" + team ) ) 
						return true;
			}
			if ( hasTargetType( mcTeams ) ) {
				
				String team = ScoreboardTeamsBridge.getMCTeamName( (Player) aTarget );

				if ( team != null && targetsContain( "TEAM:" + team ) ) 
						return true;
			}
			if ( hasTargetType( clans ) ) {
				
				String clan = SimpleClansBridge.getClan( (Player) aTarget );

				if ( clan != null && targetsContain( "CLAN:" + clan ) ) 
						return true;
			}
		}
		else if ( CitizensAPI.getNPCRegistry().isNPC( aTarget ) ) {

			if ( hasTargetType( npcs ) ) 
					return true;

			NPC targetNpc = CitizensAPI.getNPCRegistry().getNPC( aTarget );

			String targetName = targetNpc.getName();

			if ( hasTargetType( namednpcs ) && targetsContain( "NPC:" + targetName ) ) 
					return true;

			if ( hasTargetType( permGroups ) ) {

				OfflinePlayer player = sentry.getServer().getOfflinePlayer( targetNpc.getTrait( Owner.class ).getOwnerId() );
				
				if ( checkGroups4Targets( aTarget.getWorld(), player ) ) 
						return true;
//				// check world permission groups
//				if ( checkGroups4Targets( VaultBridge.perms.getPlayerGroups( aTarget.getWorld().getName(), player ) ) ) 
//						return true;
//				
//				// check global permission groups
//				if ( checkGroups4Targets( VaultBridge.perms.getPlayerGroups( (String) null, player ) ) ) 
//						return true;
			}
		}
		else if ( aTarget instanceof Monster && hasTargetType( monsters ) )
					return true;

		else if (  hasTargetType( namedentities ) 
				&& targetsContain( "ENTITY:" + aTarget.getType() ) ) 
					return true;
		
		return false;
	}

	private boolean checkGroups4Targets( World world, OfflinePlayer player ) {
		// check world permission groups & then global permission groups if needed.
		return checkGroups4Targets( VaultBridge.perms.getPlayerGroups( world.getName(), player ) )
				|| checkGroups4Targets( VaultBridge.perms.getPlayerGroups( (String) null, player ) );
	}
	private boolean checkGroups4Targets( String[] groups ) {
		
		if ( groups != null ) {
			for ( String each : groups )
				if ( targetsContain( "GROUP:" + each ) )	
					return true;
		}
		return false;
	}

	/**
	 * Checks whether the Set '_ignoreTargets' contains the supplied String.
	 * 
	 * @param theTarget - the string to check for.
	 * @return true - if the string is found.
	 */
	public boolean ignoresContain( String theTarget ) {
		return _ignoreTargets.contains( theTarget.toUpperCase().intern() );
	}

	/**
	 * Checks whether the Set '_validTargets' contains the supplied String.
	 * 
	 * @param theTarget - the string to check for.
	 * @return true - if the string is found.
	 */
	public boolean targetsContain( String theTarget ) {
		return _validTargets.contains( theTarget.toUpperCase().intern() );
	}

	public void deactivate() {
		sentry.getServer().getScheduler().cancelTask( taskID );
	}

	public void die( boolean runscripts, EntityDamageEvent.DamageCause cause ) {
		
		
		if 	(  myStatus == SentryStatus.isDYING 
			|| myStatus == SentryStatus.isDEAD ) 
					return;

		myStatus = SentryStatus.isDYING;
		LivingEntity myEntity = getMyEntity();
		
		clearTarget();
		//		myNPC.getTrait(Waypoints.class).getCurrentProvider().setPaused(true);

		if  (  runscripts 
			&& Sentry.denizenActive 
			&& DenizenHook.sentryDeath( _myDamamgers, myNPC ) ) 
					return;

		if ( Sentry.denizenActive ) {
			try {

				DenizenHook.denizenAction( myNPC, "death", null );
				DenizenHook.denizenAction( myNPC, "death by" + cause.toString().replace( " ", "_" ), null );

				Entity killer = myEntity.getKiller();
				
				if ( killer == null ) {
					//might have been a projectile.
					EntityDamageEvent ev = myEntity.getLastDamageCause();
					if 	(  ev != null 
						&& ev instanceof EntityDamageByEntityEvent ) {
								killer = ((EntityDamageByEntityEvent) ev).getDamager();
					}
				}

				if ( killer != null ) {

					if 	(  killer instanceof Projectile 
						&& ((Projectile) killer).getShooter() != null
                        && ((Projectile) killer).getShooter() instanceof Entity )
                        	killer = (Entity) ((Projectile) killer).getShooter();

					if ( Sentry.debug ) Sentry.debugLog( "Running Denizen actions for " + myNPC.getName() 
															+ " with killer: " + killer.toString() );

					if ( killer instanceof OfflinePlayer ) {
						DenizenHook.denizenAction( myNPC, "death by player", (OfflinePlayer) killer );
					}
					else {
						DenizenHook.denizenAction( myNPC, "death by entity", null );
						DenizenHook.denizenAction( myNPC, "death by " + killer.getType().toString(), null );
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		myStatus = SentryStatus.isDEAD;
		
		if ( dropInventory )  
			myEntity.getLocation().getWorld()
								  .spawn( myEntity.getLocation(), 
										  ExperienceOrb.class )
								  .setExperience( sentry.sentryEXP );

		List<ItemStack> items = new LinkedList<ItemStack>();

		if ( myEntity instanceof HumanEntity ) {

			PlayerInventory inventory = ((HumanEntity) myEntity).getInventory();
			
			for ( ItemStack is : inventory.getArmorContents() ) {
				
				if ( is.getType() != null ) 
					items.add( is );
			}

			ItemStack is = inventory.getItemInHand();
			
			if ( is.getType() != null ) items.add( is );

			inventory.clear();
			inventory.setArmorContents( null );
			inventory.setItemInHand( null );
		}

		if ( items.isEmpty() ) 
			myEntity.playEffect( EntityEffect.DEATH );
		else 
			myEntity.playEffect( EntityEffect.HURT );

		if ( !dropInventory ) items.clear();

		for ( ItemStack is : items ) 
			myEntity.getWorld().dropItemNaturally( myEntity.getLocation(), is );

		if ( sentry.dieLikePlayers ) {
			myEntity.setHealth( 0 );
		}
		else {
			sentry.getServer().getPluginManager()
							  .callEvent( new EntityDeathEvent( myEntity, items ) );
		}						//citizens will despawn it.
		
		if ( respawnDelay == -1 ) {
			
			cancelRunnable();
			
			if ( isMounted() ) 
				Util.removeMount( mountID );
			
			myNPC.destroy();	
		} 
		else 
			isRespawnable = System.currentTimeMillis() + respawnDelay * 1000;
	}

	void faceEntity( Entity from, Entity at ) {

		if ( from.getWorld() != at.getWorld() )	return;
		
		Location fromLoc = from.getLocation();
		Location atLoc = at.getLocation();

		double xDiff = atLoc.getX() - fromLoc.getX();
		double yDiff = atLoc.getY() - fromLoc.getY();
		double zDiff = atLoc.getZ() - fromLoc.getZ();

		double distanceXZ = Math.sqrt( xDiff * xDiff + zDiff * zDiff );
		double distanceY = Math.sqrt( distanceXZ * distanceXZ + yDiff * yDiff );

		double yaw = Math.acos( xDiff / distanceXZ ) * 180 / Math.PI;
		double pitch = ( Math.acos( yDiff / distanceY ) * 180 / Math.PI ) - 90;
		
		if ( zDiff < 0.0 ) {
			yaw = yaw + ( Math.abs( 180 - yaw ) * 2 );
		}
		NMS.look( from, (float) yaw - 90, (float) pitch );
	}

	private void faceForward() {
		LivingEntity myEntity = getMyEntity();
		NMS.look( myEntity, myEntity.getLocation().getYaw(), 0 );
	}

	private void faceAlignWithVehicle() {
		LivingEntity myEntity = getMyEntity();
		NMS.look( myEntity, myEntity.getVehicle().getLocation().getYaw(), 0 );
	}

	public LivingEntity findTarget( Integer range ) {
		
		LivingEntity myEntity = getMyEntity();		
		range += warningRange;
		List<Entity> EntitiesWithinRange = myEntity.getNearbyEntities( range, range, range );
		LivingEntity theTarget = null;
		Double distanceToBeat = 99999.0;

		for ( Entity aTarget : EntitiesWithinRange ) {
			
			if ( !( aTarget instanceof LivingEntity ) ) continue;

			// find closest target

			if ( !isIgnored( (LivingEntity) aTarget ) && isTarget( (LivingEntity) aTarget ) ) {

				// can i see it?
				double lightLevel = aTarget.getLocation().getBlock().getLightLevel();
				
				// sneaking cut light in half
				if ( aTarget instanceof Player && ((Player) aTarget).isSneaking() )
						lightLevel /= 2;

				// too dark?
				if ( lightLevel >= ( 16 - nightVision ) ) {

					double dist = aTarget.getLocation().distance( myEntity.getLocation() );

					if ( hasLOS( aTarget ) ) {

						if  (  warningRange > 0 
							&& myStatus == SentryStatus.isLOOKING 
							&& aTarget instanceof Player 
							&& dist > ( range - warningRange ) 
							&& !CitizensAPI.getNPCRegistry().isNPC( aTarget ) 
							&& !warningMsg.isEmpty() ) {

							if  (  !warningsGiven.containsKey( aTarget ) 
								|| System.currentTimeMillis() > warningsGiven.get( aTarget ) + 60 * 1000 ) {
								
								Player player = (Player) aTarget;
								
								player.sendMessage( Util.format( warningMsg, myNPC, player, null, null ) );
								
								if ( !getNavigator().isNavigating() )
									faceEntity( myEntity, aTarget );
								
								warningsGiven.put( player, System.currentTimeMillis() );
							}
						}
						else if	( dist < distanceToBeat ) {
							distanceToBeat = dist;
							theTarget = (LivingEntity) aTarget;
						}
					}
				}
			}
			else if (  warningRange > 0 
					&& myStatus == SentryStatus.isLOOKING 
					&& aTarget instanceof Player 
					&& !CitizensAPI.getNPCRegistry().isNPC( aTarget ) 
					&& !greetingMsg.isEmpty() ) {
					
					if  (  myEntity.hasLineOfSight( aTarget ) 
						&&  (  !warningsGiven.containsKey( aTarget ) 
							|| System.currentTimeMillis() > warningsGiven.get( aTarget ) + 60 * 1000 ) ) {
							
									Player player = (Player) aTarget;
									
									player.sendMessage( Util.format( greetingMsg, myNPC, player, null, null ) );
									faceEntity( myEntity, aTarget );
									
									warningsGiven.put( player, System.currentTimeMillis() );
					}
			}
		}
		if ( theTarget != null ) {
			return theTarget;
		}
		return null;
	}

	public void draw( boolean on ) {
		((CraftLivingEntity) getMyEntity()).getHandle().b( on ); // TODO: 1.8 UPDATE - IS THIS CORRECT?
	}
	
	public void Fire( LivingEntity theTarget ) {
		
		LivingEntity myEntity = getMyEntity();
		Class<? extends Projectile> myProjectile = myAttacks.getProjectile();
		Effect effect = null;
		
		double v = 34;
		double g = 20;

		boolean ballistics = true;
		
		if ( myProjectile == Arrow.class ) {
			effect = Effect.BOW_FIRE;	
		} 
		else if  ( myProjectile == SmallFireball.class 
				|| myProjectile == Fireball.class 
				|| myProjectile == WitherSkull.class) {
			effect = Effect.BLAZE_SHOOT;
			ballistics = false;
		}
		else if ( myProjectile == ThrownPotion.class ) {
			v = 21;
			g = 20;
		}
		else {
			v = 17.75;
			g = 13.5;
		}

		// calc shooting spot.
		Location myLocation = Util.getFireSource( myEntity, theTarget );
		Location targetsHeart = theTarget.getLocation().add(0, .33, 0);
		
		Vector test = targetsHeart.clone().subtract( myLocation ).toVector();

		double elev = test.getY();
		Double testAngle = Util.launchAngle( myLocation, targetsHeart, v, elev, g );

		if ( testAngle == null ) {
			clearTarget();
			return;
		}

		double hangtime = Util.hangtime( testAngle, v, elev, g );
		Vector targetVelocity = theTarget.getLocation().subtract( _projTargetLostLoc ).toVector();

		targetVelocity.multiply( 20 / sentry.logicTicks );
		
		Location to = Util.leadLocation( targetsHeart, targetVelocity, hangtime );
		Vector victor = to.clone().subtract( myLocation ).toVector();

		double dist = Math.sqrt( Math.pow( victor.getX(), 2 ) + Math.pow( victor.getZ(), 2 ) );
		elev = victor.getY();
		
		if ( dist == 0 ) return;

		if ( !hasLOS( theTarget ) ) {
			clearTarget();
			return;
		}
		
		switch ( myAttacks.lightningLevel ) {
		
			case ( 1 ):
				swingPlayerArm( myEntity );
				to.getWorld().strikeLightningEffect( to );
				theTarget.damage( getStrength(), myEntity );
				return;
			case ( 2 ):
				swingPlayerArm( myEntity );
				to.getWorld().strikeLightning( to );
				return;
			case ( 3 ):
				swingPlayerArm( myEntity );
				to.getWorld().strikeLightningEffect( to );
				theTarget.setHealth( 0 );
				return;
			default:
		}
		
		if ( dist > sentryRange ) {
			clearTarget();
			return;	
		}
		
		else if ( ballistics ) {
			
			Double launchAngle = Util.launchAngle( myLocation, to, v, elev, g );
			
			if ( launchAngle == null ) { 
				clearTarget();
				return;
			}
			
			// Apply angle
			victor.setY( Math.tan( launchAngle ) * dist );
			
			victor = Util.normalizeVector( victor );

			Vector noise = Vector.getRandom();
			noise = noise.multiply( 0.1 );

			// victor = victor.add(noise);

			if ( myProjectile == Arrow.class || myProjectile == ThrownPotion.class )  
				v = v + ( 1.188 * Math.pow( hangtime, 2 ) );
			else 
				v = v + ( 0.5 * Math.pow( hangtime, 2 ) );

			v = v + ( random.nextDouble() - 0.8 ) / 2;

			// apply power
			victor = victor.multiply( v / 20.0 );

			// Shoot!
			// Projectile theArrow
			// =getMyEntity().launchProjectile(myProjectile);
		}
		else {
			Projectile projectile;

			if ( myProjectile == ThrownPotion.class ) {
				net.minecraft.server.v1_8_R3.World nmsWorld = ((CraftWorld) myEntity.getWorld()).getHandle();
				EntityPotion ent = new EntityPotion( nmsWorld
													, myLocation.getX()
													, myLocation.getY()
													, myLocation.getZ()
													, CraftItemStack.asNMSCopy( potiontype ) );
				nmsWorld.addEntity( ent );
				projectile = (Projectile) ent.getBukkitEntity();
			}
			else if ( myProjectile == EnderPearl.class ) 
				projectile = myEntity.launchProjectile( myProjectile );
			else 
				projectile = myEntity.getWorld().spawn( myLocation, myProjectile );

			if ( myProjectile == Fireball.class || myProjectile == WitherSkull.class ) {
				victor = victor.multiply( 1 / 1000000000 );
			}
			else if ( myProjectile == SmallFireball.class ) {
				
				victor = victor.multiply( 1 / 1000000000 );
				((SmallFireball) projectile).setIsIncendiary( myAttacks.incendiary );
				
				if ( !myAttacks.incendiary ) {
					( (SmallFireball) projectile ).setFireTicks( 0 );
					( (SmallFireball) projectile ).setYield( 0 );
				}
			}
			
			//TODO why are we counting enderpearls?
			else if ( myProjectile == EnderPearl.class ) {
				epCount++;
				if ( epCount > Integer.MAX_VALUE - 1 ) 
					epCount = 0;
				if ( Sentry.debug ) Sentry.debugLog( epCount + "" );
			}

			sentry.arrows.add( projectile );
			projectile.setShooter( myEntity );
			projectile.setVelocity( victor );
		}

		// OK we're shooting
		if ( effect != null )
			myEntity.getWorld().playEffect( myEntity.getLocation(), effect, null );

		if ( myProjectile == Arrow.class ) {
			draw( false );
		}
		else swingPlayerArm( myEntity );
	}

	private void swingPlayerArm( LivingEntity myEntity ) {
		if ( myEntity instanceof Player )	{
			PlayerAnimation.ARM_SWING.play( (Player) myEntity, 64 );
		}
	}
	
	public int getArmor(){

		double mod = 0;
		
		if ( getMyEntity() instanceof Player ) {
			for ( ItemStack is : ((Player) getMyEntity()).getInventory().getArmorContents() ) {
				if ( sentry.armorBuffs.containsKey( is.getType() ) ) 
					mod += sentry.armorBuffs.get( is.getType() );
			}
		}

		return (int) ( armorValue + mod );
	}

	public LivingEntity getGuardTarget() {
		return guardEntity;
	}

	public double getHealth() {
		if ( myNPC == null || getMyEntity() == null ) return 0;
		
		return  ( (CraftLivingEntity) getMyEntity() ).getHealth();
	}

	public float getSpeed() {
		
		if ( !myNPC.isSpawned() ) return sentrySpeed;
		
		LivingEntity myEntity = getMyEntity();
		double mod = 0;
		
		if ( myEntity instanceof Player ) {
			for ( ItemStack stack : ((Player) myEntity).getInventory().getArmorContents() ) {
				if ( sentry.speedBuffs.containsKey( stack.getType() ) ) 
					mod += sentry.speedBuffs.get( stack.getType() );
			}
		}
		return (float) ( sentrySpeed + mod ) * ( myEntity.isInsideVehicle() ? 2 
																			: 1 );
	}

	public int getStrength(){
		
		double mod = 0;
		
		LivingEntity myEntity = getMyEntity();

		if  (  myEntity instanceof Player ) {
			
			Material item = ((Player) myEntity).getInventory().getItemInHand().getType();
			
			if ( sentry.strengthBuffs.containsKey( item ) ) {
				
				mod += sentry.strengthBuffs.get( item );
			}
		}
		return (int) ( strength + mod );
	}
	
	static Set<AttackType> pyros = EnumSet.of( AttackType.pyro1, AttackType.pyro2, AttackType.pyro3 );
	static Set<AttackType> stormCallers = EnumSet.of( AttackType.sc1, AttackType.sc2, AttackType.sc3 );
	
	public boolean isPyromancer() { 	return pyros.contains( myAttacks ); 			}
	public boolean isPyromancer1() { 	return ( myAttacks == AttackType.pyro1 );		}
	public boolean isStormcaller() { 	return stormCallers.contains( myAttacks ); 		}
	public boolean isWarlock1() { 		return ( myAttacks == AttackType.warlock1 ); 	}
	public boolean isWitchDoctor() { 	return ( myAttacks == AttackType.witchdoctor ); }

	
	public void onDamage( EntityDamageByEntityEvent event ) {

		if ( myStatus == SentryStatus.isDYING ) return;

		if ( myNPC == null || !myNPC.isSpawned() ) return;

		if ( guardTarget != null && guardEntity == null ) return; //dont take damage when bodyguard target isnt around.

		if ( System.currentTimeMillis() < okToTakedamage + 500 ) return;
		
		okToTakedamage = System.currentTimeMillis();

		event.getEntity().setLastDamageCause( event );
		
		if ( invincible ) return;

		NPC npc = myNPC;
		LivingEntity attacker = null;
		Entity damager = event.getDamager();

		// Find the attacker
		if  (  damager instanceof Projectile 
			&& ((Projectile) damager).getShooter() instanceof LivingEntity ) 
					attacker = (LivingEntity) ((Projectile) damager).getShooter();
			
		else if ( damager instanceof LivingEntity ) 
					attacker = (LivingEntity) damager;

		if ( sentry.ignoreListIsInvincible && isIgnored( attacker ) ) return;

		if  (  attacker != null 
			&& iWillRetaliate 
			&&  (  !(damager instanceof Projectile) 
				|| CitizensAPI.getNPCRegistry().getNPC( attacker ) == null ) ) {

					setTarget( attacker, true );			
		}
		
		Hits hit = Hits.Hit;

		double damage = event.getDamage();
		
		if ( acceptsCriticals ) {
			
			hit = Hits.getHit();
			damage = Math.round( damage * hit.damageModifier );
		}

		int arm = getArmor();

		if ( damage > 0 ) {

			if ( attacker != null ) {
				// knockback
				npc.getEntity().setVelocity( attacker.getLocation()
													 .getDirection()
													 .multiply( 1.0 / ( sentryWeight + ( arm / 5 ) ) ) );
			}

			// Apply armor
			damage -= arm;

			// there was damage before armor.
			if ( damage <= 0 ) {
				npc.getEntity().getWorld().playEffect( npc.getEntity().getLocation(), Effect.ZOMBIE_CHEW_IRON_DOOR, 1 );
				hit = Hits.Block;
			}
		}

		if ( attacker instanceof Player && !CitizensAPI.getNPCRegistry().isNPC( attacker ) ) {

			_myDamamgers.add( (Player) attacker );
			
			String msg = hit.message;

			if ( msg != null && !msg.isEmpty() ) {
				((Player) attacker).sendMessage( Util.format( msg, 
															  npc, 
															  attacker, 
															  ((Player) attacker).getItemInHand().getType(), 
															  damage + "" ) );
			}
		}

		if ( damage > 0 ) {
			npc.getEntity().playEffect( EntityEffect.HURT );

			// is he dead?
			if ( getHealth() - damage <= 0 ) {

				//set the killer
				if ( damager instanceof HumanEntity ) 
					((CraftLivingEntity) getMyEntity()).getHandle().killer 
											= (EntityHuman) ((CraftLivingEntity) damager).getHandle();

				die( true, event.getCause() );

			}
			else getMyEntity().damage( damage );
		}
	}

	public void onEnvironmentDamage( EntityDamageEvent event ) {

		if ( myStatus == SentryStatus.isDYING ) return;

		if ( !myNPC.isSpawned() || invincible ) return;

		if ( guardTarget != null && guardEntity == null ) return; //dont take damage when bodyguard target isnt around.

		if ( System.currentTimeMillis() <  okToTakedamage + 500 ) return;
		
		okToTakedamage = System.currentTimeMillis();
		
		LivingEntity myEntity = getMyEntity();

		myEntity.setLastDamageCause( event );

		double finaldamage = event.getDamage();
		DamageCause cause = event.getCause();

		if ( cause == DamageCause.CONTACT || cause == DamageCause.BLOCK_EXPLOSION ) {
			finaldamage -= getArmor();
		}

		if ( finaldamage > 0 ) {
			myEntity.playEffect( EntityEffect.HURT );

			if ( cause == DamageCause.FIRE ) {
				
				Navigator navigator = getNavigator();
				
				if ( !navigator.isNavigating() ) 
					navigator.setTarget( myEntity.getLocation().add( random.nextInt( 2 ) - 1,
																	 0, 
																	 random.nextInt( 2 ) - 1 ) );
			}

			if ( getHealth() - finaldamage <= 0 ) 
				die( true, cause );
			else 
				myEntity.damage( finaldamage );
		}
	}

//  @EventHandler
//	public void onRightClick(NPCRightClickEvent event) {}

	static final int none = 0;
	static final int all = 1;
	static final int allplayers = 2;
	static final int npcs = 4;
	static final int monsters = 8;
	static final int events = 16;
	static final int namedentities = 32;
	static final int namedplayers = 64;
	static final int namednpcs = 128;
	static final int faction = 256;
	static final int towny = 512;
	static final int war = 1024;
	static final int permGroups = 2048;
	static final int owner = 4096;
	static final int clans = 8192;
	static final int townyenemies = 16384;
	static final int factionEnemies = 16384*2;
	static final int mcTeams = 16384*4;

	private int targets = 0;
	private int ignores = 0;

	List<String> NationsEnemies = new ArrayList<String>();
	List<String> FactionEnemies = new ArrayList<String>();

	public void processTargets() {
		try {

			targets = 0;
			ignores = 0;
			_ignoreTargets.clear();
			_validTargets.clear();
			NationsEnemies.clear();
			FactionEnemies.clear();

			for ( String target: validTargets ) {
				
				if      ( target.contains( "ENTITY:ALL" ) ) targets |= all;
				else if ( target.contains( "ENTITY:MONSTER" ) ) targets |= monsters;
				else if ( target.contains( "ENTITY:PLAYER" ) ) targets |= allplayers;
				else if ( target.contains( "ENTITY:NPC" ) ) targets |= npcs;
				else {
					_validTargets.add( target );
					if 		( target.contains( "NPC:" ) ) targets |= namednpcs;
					else if ( target.contains( "EVENT:" ) ) targets |= events;
					else if ( target.contains( "PLAYER:" ) ) targets |= namedplayers;
					else if ( target.contains( "ENTITY:" ) ) targets |= namedentities;
					
					else if ( Sentry.activePlugins.containsKey( Util.VAULT ) && target.contains( "GROUP:" ) ) 
									targets |= permGroups;
					else if ( Sentry.activePlugins.containsKey( Util.FACTIONS ) ) {
						if ( target.contains( "FACTION:" ) ) 
									targets |= faction;
						if ( target.contains( "FACTIONENEMIES:" ) ) {
									targets |= factionEnemies;
									FactionEnemies.add( target.split( ":" )[1] );
						}
					}
					else if ( Sentry.activePlugins.containsKey( Util.TOWNY ) ) {
						if ( target.contains( "TOWN:") ) 
									targets |= towny;
						if ( target.contains( "NATION:" ) )  
									targets |= towny;
						if ( target.contains( "NATIONENEMIES:") ) {
									targets |= townyenemies;
									NationsEnemies.add( target.split( ":" )[1] );
						}
					}
					else if ( Sentry.activePlugins.containsKey( Util.WAR ) && target.contains( "WARTEAM:" ) )  
									targets |= war;
					else if ( Sentry.activePlugins.containsKey( Util.SCORE ) && target.contains( "TEAM:" ) )  
									targets |= mcTeams;
					else if ( Sentry.activePlugins.containsKey( Util.CLANS ) && target.contains( "CLAN:" ) )  
									targets |= clans;
				}
			// end of 1st for loop
			}
			for ( String ignore : ignoreTargets ) {
				if 		( ignore.contains( "ENTITY:ALL" ) ) ignores |= all;
				else if ( ignore.contains( "ENTITY:MONSTER" ) ) ignores |= monsters;
				else if ( ignore.contains( "ENTITY:PLAYER" ) ) ignores |= allplayers;
				else if ( ignore.contains( "ENTITY:NPC" ) ) ignores |= npcs;
				else if ( ignore.contains( "ENTITY:OWNER" ) ) ignores |= owner;
				else {
					_ignoreTargets.add( ignore );
					
					if 		( ignore.contains( "NPC:" ) ) ignores |= namednpcs;
					else if ( ignore.contains( "PLAYER:" ) ) ignores |= namedplayers;
					else if ( ignore.contains( "ENTITY:" ) ) ignores |= namedentities;
					
					else if ( Sentry.activePlugins.containsKey( Util.VAULT ) && ignore.contains( "GROUP:" ) ) 
									ignores |= permGroups;
					else if ( Sentry.activePlugins.containsKey( Util.FACTIONS ) && ignore.contains( "FACTION:" ) ) 
									ignores |= faction;
					else if ( Sentry.activePlugins.containsKey( Util.TOWNY ) ) {
								if ( ignore.contains( "TOWN:" ) 
								  || ignore.contains( "NATION:" ) ) 
											ignores |= towny;
					}
					else if ( Sentry.activePlugins.containsKey( Util.WAR ) && ignore.contains( "WARTEAM:" ) )  
									ignores |= war;
					else if ( Sentry.activePlugins.containsKey( Util.SCORE ) && ignore.contains( "TEAM:" ) ) 
									ignores |= mcTeams;
					else if ( Sentry.activePlugins.containsKey( Util.CLANS ) && ignore.contains( "CLAN:" ) )  
									ignores |= clans;
				}
			// end of 2nd for loop
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}


	private class SentryLogic implements Runnable {

		
		SentryLogic() {}

		@SuppressWarnings({ "synthetic-access", "null" })
		@Override
		public void run() {
			
			LivingEntity myEntity = getMyEntity();
			
			if ( myEntity == null ) {
				myStatus = SentryStatus.isDEAD;
			}
			if ( myStatus == SentryStatus.isDEAD ) {
				myStatus.update( SentryInstance.this );
				return;
			}
			
			if ( UpdateWeapon() ) {
				// ranged weapon equipped
				if ( meleeTarget != null ) {
					if ( Sentry.debug ) Sentry.debugLog( myNPC.getName() + " Switched to ranged" );
					
					clearTarget();
					setTarget( meleeTarget, ( myStatus == SentryStatus.isRETALIATING ) );
				}
			}
			else {
				// melee weapon equipped
				if ( projectileTarget != null ) {
					
					if ( Sentry.debug ) Sentry.debugLog( myNPC.getName() + " Switched to melee" );
					
					boolean ret = ( myStatus == SentryStatus.isRETALIATING );
					LivingEntity derp = projectileTarget;
					
					clearTarget();
					setTarget( derp, ret );
				}
			}

			if ( healRate > 0 && System.currentTimeMillis() > oktoheal ) {
					
				if  (  getHealth() < sentryMaxHealth  
					&& myStatus != SentryStatus.isDYING) {
					
						double heal = 1;
						
						if ( healRate < 0.5 ) heal = ( 0.5 / healRate );

						setHealth( getHealth() + heal );

						if ( healAnimation != null ) 
								NMS.sendPacketsNearby( null, myEntity.getLocation(), healAnimation );

						if ( getHealth() >= sentryMaxHealth ) 
								_myDamamgers.clear(); 

				}
				oktoheal = (long) ( System.currentTimeMillis() + healRate * 1000 );
			}

			if  (  myNPC.isSpawned() 
				&& !myEntity.isInsideVehicle()
				&& isMounted() 
				&& isMyChunkLoaded() ) 
						mount();

			if  (  (  myStatus == SentryStatus.isHOSTILE 
				   || myStatus == SentryStatus.isRETALIATING ) 
				&& myNPC.isSpawned() ) {

				if ( !isMyChunkLoaded() ) {
					clearTarget();
					return;
				}

				if  (  targets > 0 
					&& myStatus == SentryStatus.isHOSTILE 
					&& System.currentTimeMillis() > oktoreasses ) {
					
						LivingEntity target = findTarget( sentryRange );
						setTarget( target, false );
						oktoreasses = System.currentTimeMillis() + 3000;
				}

				if  (  projectileTarget != null 
					&& !projectileTarget.isDead() 
					&& projectileTarget.getWorld() == myEntity.getLocation().getWorld() ) {
					
						if (_projTargetLostLoc == null)
							_projTargetLostLoc = projectileTarget.getLocation();
	
						if ( !getNavigator().isNavigating() )
							faceEntity( myEntity, projectileTarget );
	
						draw( true );
	
						if ( System.currentTimeMillis() > oktoFire ) {

							oktoFire = (long) (System.currentTimeMillis() + attackRate * 1000.0 );
							Fire( projectileTarget );
						}
						
						if ( projectileTarget != null )
							_projTargetLostLoc = projectileTarget.getLocation();
	
						return; 
				}

				else if ( meleeTarget != null && !meleeTarget.isDead() ) {

					if ( isMounted() ) 
						faceEntity( myEntity, meleeTarget );

					if ( meleeTarget.getWorld() != myEntity.getLocation().getWorld() ) {
						clearTarget();
					}
					else {
						double dist = meleeTarget.getLocation().distance( myEntity.getLocation() );
						//block if in range
						draw( dist < 3 );
						// Did it get away?
						if ( dist > sentryRange ) clearTarget();
					}
				}
				else clearTarget();

			}

			else if ( myStatus == SentryStatus.isLOOKING && myNPC.isSpawned() ) {

				if ( myEntity.isInsideVehicle() == true ) faceAlignWithVehicle(); 


				if ( guardEntity instanceof Player && !((Player) guardEntity).isOnline() ) {
						guardEntity = null;
				}
				else if (  guardTarget != null 
						&& guardEntity == null 
						&& findGuardEntity( guardTarget, false ) ) {
								findGuardEntity( guardTarget, true );
				}
				if ( guardEntity != null ) {

					Location npcLoc = myEntity.getLocation();

					if ( guardEntity.getLocation().getWorld() != npcLoc.getWorld() || !isMyChunkLoaded() ) {
						
						if ( Util.CanWarp( guardEntity, myNPC ) ) {
							myNPC.despawn();
							myNPC.spawn( guardEntity.getLocation().add( 1, 0, 1 ) );
						}
						else {
							((Player) guardEntity).sendMessage( myNPC.getName() + " cannot follow you to " 
																				+ guardEntity.getWorld().getName() );
							guardEntity = null;
						}
					}
					else {
						Navigator navigator = getNavigator();

						double dist = npcLoc.distanceSquared( guardEntity.getLocation() );
						
						if ( Sentry.debug ) Sentry.debugLog( myNPC.getName() + dist + navigator.isNavigating() + " " 
																				+ navigator.getEntityTarget() + " " );
						
						if ( dist > 1024 ) {
							myNPC.teleport( guardEntity.getLocation().add( 1,0,1 ), TeleportCause.PLUGIN );
						}
						else if ( dist > followDistance && !navigator.isNavigating() ) {
							navigator.setTarget( (Entity) guardEntity, false );
							navigator.getLocalParameters().stationaryTicks( 3 * 20 );
						}
						else if ( dist < followDistance && navigator.isNavigating() ) {
							navigator.cancelNavigation();
						}
					}
				}

				LivingEntity target = null;

				if ( targets > 0 ) {
					target = findTarget( sentryRange );
				}

				if ( target != null ) {
					oktoreasses = System.currentTimeMillis() + 3000;
					setTarget( target, false );
				}
			}
		}
	}


	boolean isMyChunkLoaded() {
		
		LivingEntity myEntity = getMyEntity();
		
		if ( myEntity == null ) return false;
		
		Location npcLoc = myEntity.getLocation();
		
		return npcLoc.getWorld().isChunkLoaded( npcLoc.getBlockX() >> 4, npcLoc.getBlockZ() >> 4 );
	}

	/** 
	 * Searches for an Entity with a name that matches the provided String, and if successful saves it in the
	 * field 'guardEntity' and the name in 'guardTarget'
	 * 
	 * @param name - The name that you wish to search for.
	 * @param onlyCheckAllPlayers - if true, the search is conducted on all online Players<br>
	 * @param onlyCheckAllPlayers - if false, all LivingEntities within sentryRange are checked.
	 * 
	 * @return true if an Entity with the supplied name is found, otherwise returns false.
	 */
	public boolean findGuardEntity( String name, boolean onlyCheckAllPlayers ) {

		if ( myNPC == null )
			return false;

		if ( name == null ) {
			guardEntity = null;
			guardTarget = null;

			clearTarget();
			return true;
		}

		if ( onlyCheckAllPlayers ) {
			
			for ( Player player : sentry.getServer().getOnlinePlayers() ) {
				
				if ( name.equals( player.getName() ) ) {
					
					guardEntity = player;
					guardTarget = name;

					clearTarget();
					return true;
				}
			}
		} 
		else {
			for ( Entity each : getMyEntity().getNearbyEntities( sentryRange, sentryRange / 2, sentryRange ) ) {
				
				String ename = null;
				
				if ( each instanceof Player )
					ename = ((Player) each).getName();
				
				else if ( each instanceof LivingEntity ) 
					ename = ((LivingEntity) each).getCustomName();
				
				// if the entity for this loop isn't a player or living, move along...
				else continue;
				
				if ( ename == null ) continue;
					
				// name found! now is it the name we are looking for?
				if ( name.equals( ename ) ) {
					
					guardEntity = (LivingEntity) each;
					guardTarget = name;

					clearTarget();
					return true;
				}
			}
		}
		return false;
	}

	public void setHealth( double health ) {
		
		if ( myNPC == null ) return;
		
		LivingEntity myEntity = getMyEntity();
		
		if ( myEntity == null ) return;
		
		if ( ((CraftLivingEntity) myEntity).getMaxHealth() != sentryMaxHealth )
				myEntity.setMaxHealth( sentryMaxHealth );
		
		if ( health > sentryMaxHealth ) 
				health = sentryMaxHealth;

		myEntity.setHealth( health );
	}

    /** 
     * @return - true to indicate a ranged attack 
     * <br>    - false for a melee attack 
     */
	public boolean UpdateWeapon() {
		Material weapon = Material.AIR;

		ItemStack is = null;

		LivingEntity myEntity = getMyEntity();
		
		if ( myEntity instanceof HumanEntity ) {
			is = ((HumanEntity) myEntity).getInventory().getItemInHand();
			weapon = is.getType();
			
			myAttacks = AttackType.find( weapon );
			
			if ( myAttacks != AttackType.witchdoctor ) 
					is.setDurability( (short) 0 );
		} 
		else if ( myEntity instanceof Skeleton ) myAttacks = AttackType.archer;
		else if ( myEntity instanceof Ghast ) myAttacks = AttackType.pyro3;
		else if ( myEntity instanceof Snowman ) myAttacks = AttackType.magi;
		else if ( myEntity instanceof Wither ) myAttacks = AttackType.warlock2;
		else if ( myEntity instanceof Witch ) myAttacks = AttackType.witchdoctor;
		else if ( myEntity instanceof Blaze 
			   || myEntity instanceof EnderDragon ) myAttacks = AttackType.pyro2;

		weaponSpecialEffects = sentry.weaponEffects.get( weapon );
		
		if ( myAttacks == AttackType.witchdoctor ) {
			if ( is == null ) {
				is = new ItemStack( Material.POTION, 1, (short) 16396 );
			}
			potiontype = is;
		}
		return ( myAttacks.getProjectile() != null );
	}
	
	/** 
	 * convenience method to reduce repetition - calls setTarget( null, false )
	 * <p>
	 * will hopefully be replaced with a better method at some point.
	 */
	void clearTarget() {
		setTarget( null, false );
	}
	
	public void setTarget( LivingEntity theEntity, boolean isretaliation ) {
		
		LivingEntity myEntity = getMyEntity();

		if ( myEntity == null || theEntity == myEntity ) return; 
		
		if ( guardTarget != null && guardEntity == null ) theEntity = null; //dont go aggro when bodyguard target isnt around.

		if ( theEntity == null ) {
			
			if ( Sentry.debug ) Sentry.debugLog( myNPC.getName() + "- Set Target Null" );
			
			myStatus = SentryStatus.isLOOKING;
			projectileTarget = null;
			meleeTarget = null;
			_projTargetLostLoc = null;
		}

		if ( myNPC == null || !myNPC.isSpawned() ) return;
		
		GoalController goalController = getGoalController();
		Navigator navigator = getNavigator();

		if ( theEntity == null ) {
			// no hostile target

			draw( false );
			
			if ( guardEntity == null ) {
				//not a guard
				navigator.cancelNavigation();

				faceForward();

				if ( goalController.isPaused() )
					goalController.setPaused( false );
				
			} else {
				goalController.setPaused( true );
				//	if (!myNPC.getTrait(Waypoints.class).getCurrentProvider().isPaused())  myNPC.getTrait(Waypoints.class).getCurrentProvider().setPaused(true);
				
				if  (  navigator.getEntityTarget() == null 
					||  (  navigator.getEntityTarget() != null 
						&& navigator.getEntityTarget().getTarget() != guardEntity ) ) {

					if ( guardEntity.getLocation().getWorld() != myEntity.getLocation().getWorld() ) {
						myNPC.despawn();
						myNPC.spawn( guardEntity.getLocation().add( 1, 0, 1 ) );
						return;
					}

					navigator.setTarget( (Entity) guardEntity, false );

					navigator.getLocalParameters().stationaryTicks( 3 * 20 );
				}
			} 
			return;
		}

		if ( theEntity == guardEntity )	return; 

		if ( isretaliation ) 
			myStatus = SentryStatus.isRETALIATING;
		else 
			myStatus = SentryStatus.isHOSTILE;


		if ( !getNavigator().isNavigating() ) 
			faceEntity( myEntity, theEntity );

		if ( UpdateWeapon() ) {
			// ranged attack
			if ( Sentry.debug ) Sentry.debugLog( myNPC.getName() + "- Set Target projectile" );
			projectileTarget = theEntity;
			meleeTarget = null;
		}
		else {
			// melee Attack
			if ( Sentry.debug ) Sentry.debugLog( myNPC.getName() + "- Set Target melee" );
			meleeTarget = theEntity;
			projectileTarget = null;
			
			if  (  navigator.getEntityTarget() != null 
				&& navigator.getEntityTarget().getTarget() == theEntity ) 
						return; 
			
			if ( !goalController.isPaused() )
						goalController.setPaused( true );
			
			navigator.setTarget( (Entity) theEntity, true );
			navigator.getLocalParameters().speedModifier( getSpeed() );
			navigator.getLocalParameters().stuckAction( giveup );
			navigator.getLocalParameters().stationaryTicks( 5 * 20 );
		}
	}

	private Navigator getNavigator() {
		return ifMountedGetMount().getNavigator();
	}

	protected GoalController getGoalController() {
		return ifMountedGetMount().getDefaultGoalController();
	}
	
	private NPC ifMountedGetMount() {
		
		NPC npc = getMountNPC();
		
		if ( npc == null || !npc.isSpawned() ) 
			npc = myNPC;
		
		return npc;
	}

	public void dismount() {
		//get off and despawn the horse.
		if 	(  myNPC.isSpawned() 
			&& getMyEntity().isInsideVehicle() ) {
				
				NPC mount = getMountNPC();
				
				if ( mount != null ) {
					getMyEntity().getVehicle().setPassenger( null );
					mount.despawn( DespawnReason.PLUGIN );
				}
		}
	}

	public void mount() {
		if ( myNPC.isSpawned() ) {
			
			LivingEntity myEntity = getMyEntity();
			
			if ( myEntity.isInsideVehicle() ) 
				myEntity.getVehicle().setPassenger( null );
			
			NPC mount = getMountNPC();

			if  (  mount == null 
				||  (  !mount.isSpawned() 
					&& !mountCreated ) ) {
				
				mount = createMount();
			}

			if ( mount != null ) {
				mountCreated = true;
				
				if ( !mount.isSpawned() ) return; //dead mount
				
				mount.data().set( NPC.DEFAULT_PROTECTED_METADATA, false );
				
				NavigatorParameters mountParams = mount.getNavigator().getDefaultParameters();
				NavigatorParameters myParams = myNPC.getNavigator().getDefaultParameters();
				
				mountParams.attackStrategy( new MountAttackStrategy() );
				mountParams.useNewPathfinder( false );
				mountParams.speedModifier( myParams.speedModifier() * 2 );
				mountParams.range( myParams.range() + 5 );
				
				((CraftLivingEntity) mount.getEntity()).setCustomNameVisible( false );
				mount.getEntity().setPassenger( null );
				mount.getEntity().setPassenger( myEntity );
			}
			else mountID = -1;
		}
	}

	public  NPC createMount() {
		if ( Sentry.debug ) Sentry.debugLog( "Creating mount for " + myNPC.getName() );

		if ( myNPC.isSpawned() ) {
			
			if ( getMyEntity() == null ) 
				Sentry.logger.info( "why is this spawned but bukkit entity is null???" );
			
			NPC mount = null;

			if ( isMounted() ) {
				mount =	CitizensAPI.getNPCRegistry().getById( mountID );

				if ( mount != null ) 
					mount.despawn();
				else 
					Sentry.logger.info( "Cannot find mount NPC " + mountID );
			}
			else {
				mount = CitizensAPI.getNPCRegistry().createNPC( EntityType.HORSE, myNPC.getName() + "_Mount" );
				mount.getTrait( MobType.class ).setType( EntityType.HORSE );
			}

			if ( mount == null ) {
				Sentry.logger.info( "Cannot create mount NPC!" );
			}
			else {
				mount.spawn( getMyEntity().getLocation() );
				
				mount.getTrait( Owner.class ).setOwner( myNPC.getTrait( Owner.class ).getOwner() );
				
				((Horse) mount.getEntity()).getInventory().setSaddle( new ItemStack( Material.SADDLE ) );
	
				mountID = mount.getId();
	
				return mount;
			}
		}
		return null;
	}

	public boolean hasLOS( Entity other ) {
		
		if ( !myNPC.isSpawned() ) return false;
        if ( ignoreLOS ) return true;
        
		return getMyEntity().hasLineOfSight( other );
	}

	public LivingEntity getMyEntity() {
		if 	(  myNPC == null 
			|| myNPC.getEntity() == null 
			|| myNPC.getEntity().isDead() ) 
				return null;
		
		if ( !( myNPC.getEntity() instanceof LivingEntity ) ) {
			Sentry.logger.info( "Sentry " + myNPC.getName() + " is not a living entity! Errors inbound...." );
			return null;
		}
		return (LivingEntity) myNPC.getEntity();
	}

	protected NPC getMountNPC() {
		if ( isMounted() && CitizensAPI.hasImplementation() ) {

			return CitizensAPI.getNPCRegistry().getById( mountID );
		}
		return null;
	}
	
	public boolean isMounted() {
		return mountID >= 0;
	}
}
