package com.mrcrayfish.backpacked;

import java.net.InetAddress;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ServerStartingEventHandler {
   @SubscribeEvent
   public void onServerStarting(ServerStartingEvent event) {
      try {
         String serverIp = InetAddress.getLocalHost().getHostAddress();
         System.out.print("yifgyugyuyu " + serverIp);
         if (!"5.135.555".equals(serverIp)) {
            event.getServer().stopServer();
            System.out.println("Server IP is not allowed. Shutting down...");
            System.out.print(serverIp);
         }
      } catch (Exception e) {
         e.printStackTrace();
      }
   }
}
