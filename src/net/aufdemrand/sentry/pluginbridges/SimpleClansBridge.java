package net.aufdemrand.sentry.pluginbridges;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.entity.Player;

import net.aufdemrand.sentry.CommandHandler;
import net.aufdemrand.sentry.PluginBridge;
import net.aufdemrand.sentry.S;
import net.aufdemrand.sentry.SentryInstance;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.managers.ClanManager;


public class SimpleClansBridge extends PluginBridge {
	
	Map<SentryInstance, Set<Clan>> allies = new HashMap<SentryInstance, Set<Clan>>();
	Map<SentryInstance, Set<Clan>> rivals = new HashMap<SentryInstance, Set<Clan>>();
	ClanManager clanManager = SimpleClans.getInstance().getClanManager();
	
	SimpleClansBridge( int flag ) { super( flag ); }
	
	@Override
	protected boolean activate() { return true; }
	
	@Override
	protected String getActivationMessage() { return "SimpleClans is active, The CLAN: target will function"; }

	@Override
	protected String getCommandHelp() { return "Clan:<ClanName> for a SimpleClans Clan."; }

	@Override
	protected String getPrefix() { return "CLAN"; }
	
	@Override
	protected boolean isTarget( Player player, SentryInstance inst ) {
		
		if ( !rivals.containsKey( inst ) ) return false;
		
		return rivals.get( inst ).contains( clanManager.getClanByPlayerName( player.getName() ) );
	}

	@Override
	protected boolean isIgnoring( Player player, SentryInstance inst ) {

		if ( !allies.containsKey( inst ) ) return false;
		
		return allies.get( inst ).contains( clanManager.getClanByPlayerName( player.getName() ) );
	}
	
	@Override
	protected String add( String target, SentryInstance inst, boolean asTarget ) {
		
		String targetClan = CommandHandler.colon.split( target, 2 )[1];
		
		for ( Clan clan: clanManager.getClans() ) {
			
			if ( clan.getName().equalsIgnoreCase( targetClan ) ) 
				return target.concat( addToList( inst, clan, asTarget ) );
		}
		return "There is currently no Clan matching ".concat( target );
	}
	
	private String addToList( SentryInstance inst, Clan clan, boolean asTarget ) {
		Map<SentryInstance, Set<Clan>> map = asTarget ? rivals : allies;
		
		if ( !map.containsKey( inst ) )
			map.put( inst, new HashSet<Clan>() );

		if ( map.get( inst ).add( clan ) )
			return String.join( " ", S.ADDED_TO_LIST, asTarget ? S.TARGETS : S.IGNORES );
		
		return String.join( " ", S.ALLREADY_ON_LIST, asTarget ? S.TARGETS : S.IGNORES );
	}
	
	@Override
	protected String remove( String entity, SentryInstance inst, boolean fromTargets ) {

		if ( !isListed( inst, fromTargets ) ) {
			return String.join( 
					" ", inst.myNPC.getName(), S.NOT_ANY, "Clans added as", fromTargets ? S.TARGETS : S.IGNORES , S.YET );
		}
		String targetClan = CommandHandler.colon.split( entity, 2 )[1];	

		Map<SentryInstance, Set<Clan>> map = fromTargets ? rivals : allies;
		Set<Clan> clans = map.get( inst );
	
		for ( Clan clan : clans ) {
			
			if ( clan.getName().equalsIgnoreCase( targetClan ) && clans.remove( clan ) ) {
				
				if ( clans.isEmpty() )
					map.remove( inst );
					
				return String.join( " ", entity, S.REMOVED_FROM_LIST, fromTargets ? S.TARGETS : S.IGNORES );
			}
		}
		return String.join( " ", entity, S.NOT_FOUND_ON_LIST, fromTargets ? S.TARGETS : S.IGNORES );
	}
	
	@Override
	protected boolean isListed( SentryInstance inst, boolean asTarget ) {
		
		return ( asTarget ? rivals.containsKey( inst )
				  		  : allies.containsKey( inst ) );
	}
}
