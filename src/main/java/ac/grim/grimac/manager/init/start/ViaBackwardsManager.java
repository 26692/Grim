package ac.grim.grimac.manager.init.start;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.events.bukkit.PlayerJoinQuitListener;
import ac.grim.grimac.manager.init.Initable;
import io.github.retrooper.packetevents.utils.server.ServerVersion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

public class ViaBackwardsManager implements Initable {
    @Override
    public void start() {
        // We have a more accurate version of this patch
        System.setProperty("com.viaversion.ignorePaperBlockPlacePatch", "true");

        if (ServerVersion.getVersion().isNewerThanOrEquals(ServerVersion.v_1_17)) {
            // Enable ping -> transaction packet
            System.setProperty("com.viaversion.handlePingsAsInvAcknowledgements", "true");

            // Check if we support this property
            try {
                Plugin viaBackwards = Bukkit.getPluginManager().getPlugin("ViaBackwards");
                if (viaBackwards != null) {
                    String[] split = viaBackwards.getDescription().getVersion().replace("-SNAPSHOT", "").split("\\.");

                    if (split.length == 3) {
                        // If the version is before 4.0.2
                        if (Integer.parseInt(split[0]) < 4 || (Integer.parseInt(split[1]) == 0 && Integer.parseInt(split[2]) < 2)) {
                            Logger logger = GrimAPI.INSTANCE.getPlugin().getLogger();

                            logger.warning(ChatColor.RED + "Please update ViaBackwards to 4.0.2 or newer");
                            logger.warning(ChatColor.RED + "An important packet is broken for 1.16 and below clients on this ViaBackwards version");
                            logger.warning(ChatColor.RED + "Disabling all checks for 1.16 and below players as otherwise they WILL be falsely banned");
                            logger.warning(ChatColor.RED + "Supported  version: " + ChatColor.WHITE + "https://github.com/ViaVersion/ViaBackwards/actions/runs/1039987269");

                            PlayerJoinQuitListener.isViaLegacyUpdated = false;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }
}
