/*
 *  * Copyright © Wynntils - 2019.
 */

package com.wynntils.modules.music.events;

import java.util.regex.Pattern;
import com.wynntils.core.events.custom.WynnClassChangeEvent;
import com.wynntils.core.events.custom.WynnTerritoryChangeEvent;
import com.wynntils.core.events.custom.WynncraftServerEvent;
import com.wynntils.core.framework.enums.ClassType;
import com.wynntils.core.framework.interfaces.Listener;
import com.wynntils.core.utils.helpers.CommandResponse;
import com.wynntils.modules.music.configs.MusicConfig;
import com.wynntils.modules.music.managers.MusicManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;


public class ClientEvents implements Listener {
	
	public void HybridMusicDefaultSwap() {
 		System.out.println("HybridMusicSwap Triggered: to Default music");
    	 CommandResponse musictoggle = new CommandResponse("/toggle music", (m, t) -> {
    		 if (t.getFormattedText().contains("Music is now fading out...")) {
        		 CommandResponse musictoggle2 = new CommandResponse("/toggle music", (m2, t2) -> {
        	    		 if (t2.getFormattedText().contains("Music is now fading out...")) {
        	    			 CommandResponse musictoggle3 = new CommandResponse("/toggle music", (m3, t3) -> {
        	    				 if (t3.getFormattedText().contains("Music is now fading out...")) {
        	    					 Minecraft.getMinecraft().player.sendChatMessage("/toggle music");
        	    				 }
        	    			 }, Pattern.compile("(§2)"));
                	    	 musictoggle3.setCancel(true);
                	         musictoggle3.executeCommand();
        	    		 }
        	         }, Pattern.compile("(§2)"));
        	    	 musictoggle2.setCancel(true);
        	         musictoggle2.executeCommand();
    		 }
         }, Pattern.compile("(§2)"));
    	 musictoggle.setCancel(true);
         musictoggle.executeCommand();
		if (!MusicManager.getPlayer().isPaused()) { 
    		MusicManager.getPlayer().changePausedState();
    		MusicManager.getPlayer().changePausedState();
    		MusicManager.getPlayer().changePausedState();
    	}
	}
	
	public void HybridMusicWynntilsSwap() {
  		System.out.println("HybridMusicSwap Triggered: to Wynntils music");
		CommandResponse musictoggle = new CommandResponse("/toggle music", (m, t) -> {
	    		 if (t.getFormattedText().contains("You will now hear music again!")) {
	        		 CommandResponse musictoggle2 = new CommandResponse("/toggle music", (m2, t2) -> {
	        	    		 if (t2.getFormattedText().contains("You will now hear music again!")) {
	        	    			 CommandResponse musictoggle3 = new CommandResponse("/toggle music", (m3, t3) -> {
	        	        	    		 if (t3.getFormattedText().contains("You will now hear music again!")) {
	        	        	    	        	Minecraft.getMinecraft().player.sendChatMessage("/toggle music");
	        	        	    		 }
	        	        	         }, Pattern.compile("(§2)"));
	    	        	    	 musictoggle3.setCancel(true);
	    	        	         musictoggle3.executeCommand();
	        	    		 }
	        	         }, Pattern.compile("(§2)"));
	        	    	 musictoggle2.setCancel(true);
	        	         musictoggle2.executeCommand();
	    		 	}
	    		}, Pattern.compile("(§2)"));
	    musictoggle.setCancel(true);
	    musictoggle.executeCommand();
		if (MusicManager.getPlayer().isPaused()) { 
		MusicManager.getPlayer().changePausedState();
		MusicManager.getPlayer().changePausedState();
		MusicManager.getPlayer().changePausedState();
		}
	}

    @SubscribeEvent
    public void onTerritoryUpdate(WynnTerritoryChangeEvent e) {
        if (e.getNewTerritory().equals("Waiting") && MusicConfig.INSTANCE.HybridMusicThing) {
        	 if (!MusicManager.getPlayer().isPaused()) { 
        		 HybridMusicDefaultSwap();
        	 } 
             return;
        } else if (e.getNewTerritory().equals("Waiting")) {
        	return;
        } else if (MusicConfig.INSTANCE.HybridMusicThing) {
        	MusicManager.checkForMusic(e.getNewTerritory());
        	if (MusicManager.getPlayer().getCurrentMusic() == null) {
        		if (!MusicManager.getPlayer().isPaused()) { 
        			HybridMusicDefaultSwap();
        		 }
        	} else {
        		if (MusicManager.getPlayer().isPaused()) { 
        			HybridMusicWynntilsSwap();
        		}
        	}
    } else {
        	MusicManager.checkForMusic(e.getNewTerritory());
        }
    }

    @SubscribeEvent
    public void classChange(WynnClassChangeEvent e) {
        if (e.getCurrentClass() == ClassType.NONE) MusicManager.getPlayer().stop();
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.START) return;

        MusicManager.getPlayer().setupController();
    }

    @SubscribeEvent
    public void serverLeft(WynncraftServerEvent.Leave e) {
        MusicManager.getPlayer().stop();
    }

}
