package ac.grim.grimac.checks.movement;

import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.data.VectorData;
import ac.grim.grimac.utils.data.VelocityData;
import io.github.retrooper.packetevents.utils.vector.Vector3f;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.util.Vector;

public class ExplosionHandler {
    Short2ObjectOpenHashMap<Vector> firstBreadMap = new Short2ObjectOpenHashMap<>();
    GrimPlayer player;

    VelocityData lastExplosionsKnownTaken = new VelocityData(-1, new Vector());
    VelocityData firstBreadAddedExplosion = null;

    public ExplosionHandler(GrimPlayer player) {
        this.player = player;
    }

    public void handleTransactionPacket(short transactionID) {
        if (firstBreadMap.containsKey(transactionID)) {
            firstBreadAddedExplosion = new VelocityData(-1, lastExplosionsKnownTaken.vector.clone().add(firstBreadMap.get(transactionID)));
        }

        if (firstBreadMap.containsKey((short) (transactionID + 1))) {
            firstBreadAddedExplosion = null;
            lastExplosionsKnownTaken.vector.add(firstBreadMap.remove((short) (transactionID + 1)));
        }
    }

    public void addPlayerExplosion(short breadOne, Vector3f explosion) {
        firstBreadMap.put(breadOne, new Vector(explosion.getX(), explosion.getY(), explosion.getZ()));
    }

    public void handlePlayerExplosion(double offset) {
        if (player.knownExplosion == null && player.firstBreadExplosion == null) {
            return;
        }

        ChatColor color = ChatColor.GREEN;

        if (!player.predictedVelocity.hasVectorType(VectorData.VectorType.Explosion))
            return;

        // Unsure knockback was taken
        if (player.firstBreadExplosion != null) {
            player.firstBreadExplosion.offset = Math.min(player.firstBreadExplosion.offset, offset);
        }

        // 100% known kb was taken
        if (player.knownExplosion != null) {
            offset = Math.min(player.knownExplosion.offset, offset);

            if (offset > 0.05) {
                color = ChatColor.RED;
            }

            // Add offset to violations
            Bukkit.broadcastMessage(color + "Explosion offset is " + offset);
        }
    }

    public VelocityData getPossibleExplosions() {
        if (lastExplosionsKnownTaken.vector.lengthSquared() < 1e-5)
            return null;

        VelocityData returnLastExplosion = lastExplosionsKnownTaken;
        lastExplosionsKnownTaken = new VelocityData(-1, new Vector());

        return returnLastExplosion;
    }

    public VelocityData getFirstBreadAddedExplosion() {
        return firstBreadAddedExplosion;
    }
}
