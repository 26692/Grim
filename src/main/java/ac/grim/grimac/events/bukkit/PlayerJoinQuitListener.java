package ac.grim.grimac.events.bukkit;

import ac.grim.grimac.GrimAC;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.predictionengine.MovementCheckRunner;
import io.github.retrooper.packetevents.PacketEvents;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.concurrent.ConcurrentLinkedQueue;

public class PlayerJoinQuitListener implements Listener {

    // Allow other plugins to modify login location or flight status
    @EventHandler(priority = EventPriority.MONITOR)
    public void playerJoinEvent(PlayerJoinEvent event) {
        Player bukkitPlayer = event.getPlayer();

        if (PacketEvents.get().getPlayerUtils().isGeyserPlayer(bukkitPlayer)) return;

        GrimPlayer player = new GrimPlayer(bukkitPlayer);
        player.lastX = bukkitPlayer.getLocation().getX();
        player.lastY = bukkitPlayer.getLocation().getY();
        player.lastZ = bukkitPlayer.getLocation().getZ();
        player.lastXRot = bukkitPlayer.getLocation().getYaw();
        player.lastYRot = bukkitPlayer.getLocation().getPitch();
        player.x = bukkitPlayer.getLocation().getX();
        player.y = bukkitPlayer.getLocation().getY();
        player.z = bukkitPlayer.getLocation().getZ();
        player.xRot = bukkitPlayer.getLocation().getYaw();
        player.yRot = bukkitPlayer.getLocation().getPitch();

        player.packetStateData.packetPlayerX = bukkitPlayer.getLocation().getX();
        player.packetStateData.packetPlayerY = bukkitPlayer.getLocation().getY();
        player.packetStateData.packetPlayerZ = bukkitPlayer.getLocation().getZ();
        player.packetStateData.packetPlayerXRot = bukkitPlayer.getLocation().getYaw();
        player.packetStateData.packetPlayerYRot = bukkitPlayer.getLocation().getPitch();

        player.uncertaintyHandler.pistonPushing.add(0d);

        GrimAC.playerGrimHashMap.put(event.getPlayer(), player);

        MovementCheckRunner.queuedPredictions.put(event.getPlayer().getUniqueId(), new ConcurrentLinkedQueue<>());
    }

    // Better compatibility with other plugins that use our API
    @EventHandler(priority = EventPriority.HIGH)
    public void playerQuitEvent(PlayerQuitEvent event) {
        MovementCheckRunner.queuedPredictions.remove(event.getPlayer().getUniqueId());
        GrimAC.playerGrimHashMap.remove(event.getPlayer());
    }
}
