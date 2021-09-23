package ac.grim.grimac.checks.impl.velocity;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.data.VelocityData;
import ac.grim.grimac.utils.math.GrimMath;
import io.github.retrooper.packetevents.event.impl.PacketPlaySendEvent;
import io.github.retrooper.packetevents.packettype.PacketType;
import io.github.retrooper.packetevents.packetwrappers.play.out.entityvelocity.WrappedPacketOutEntityVelocity;
import io.github.retrooper.packetevents.utils.player.ClientVersion;
import io.github.retrooper.packetevents.utils.vector.Vector3d;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.util.concurrent.ConcurrentLinkedQueue;

// We are making a velocity sandwich between two pieces of transaction packets (bread)
@CheckData(name = "AntiKB", configName = "Knockback")
public class KnockbackHandler extends PacketCheck {
    ConcurrentLinkedQueue<VelocityData> firstBreadMap = new ConcurrentLinkedQueue<>();
    GrimPlayer player;

    ConcurrentLinkedQueue<VelocityData> lastKnockbackKnownTaken = new ConcurrentLinkedQueue<>();
    VelocityData firstBreadOnlyKnockback = null;

    boolean wasExplosionZeroPointZeroThree = false;

    double offsetToFlag;
    double setbackVL;
    double decay;

    public KnockbackHandler(GrimPlayer player) {
        super(player);
        this.player = player;
    }

    @Override
    public void onPacketSend(final PacketPlaySendEvent event) {
        byte packetID = event.getPacketId();

        if (packetID == PacketType.Play.Server.ENTITY_VELOCITY) {
            WrappedPacketOutEntityVelocity velocity = new WrappedPacketOutEntityVelocity(event.getNMSPacket());
            int entityId = velocity.getEntityId();

            GrimPlayer player = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getPlayer());
            if (player == null) return;

            // Detect whether this knockback packet affects the player or if it is useless
            Entity playerVehicle = player.bukkitPlayer.getVehicle();
            if ((playerVehicle == null && entityId != player.entityID) || (playerVehicle != null && entityId != playerVehicle.getEntityId())) {
                return;
            }

            // If the player isn't in a vehicle and the ID is for the player, the player will take kb
            // If the player is in a vehicle and the ID is for the player's vehicle, the player will take kb
            Vector3d playerVelocity = velocity.getVelocity();

            // Wrap velocity between two transactions
            player.sendTransaction();
            addPlayerKnockback(entityId, player.lastTransactionSent.get(), new Vector(playerVelocity.getX(), playerVelocity.getY(), playerVelocity.getZ()));
            event.setPostTask(player::sendAndFlushTransaction);
        }
    }

    private void addPlayerKnockback(int entityID, int breadOne, Vector knockback) {
        double minimumMovement = 0.003D;
        if (player.getClientVersion().isOlderThanOrEquals(ClientVersion.v_1_8))
            minimumMovement = 0.005D;

        if (Math.abs(knockback.getX()) < minimumMovement) {
            knockback.setX(0D);
        }

        if (Math.abs(knockback.getY()) < minimumMovement) {
            knockback.setY(0D);
        }

        if (Math.abs(knockback.getZ()) < minimumMovement) {
            knockback.setZ(0D);
        }

        firstBreadMap.add(new VelocityData(entityID, breadOne, knockback));
    }

    public VelocityData getRequiredKB(int entityID, int transaction) {
        tickKnockback(transaction);

        VelocityData returnLastKB = null;
        for (VelocityData data : lastKnockbackKnownTaken) {
            if (data.entityID == entityID)
                returnLastKB = data;
        }

        lastKnockbackKnownTaken.clear();

        return returnLastKB;
    }

    private void tickKnockback(int transactionID) {
        VelocityData data = firstBreadMap.peek();
        while (data != null) {
            if (data.transaction == transactionID) { // First bread knockback
                firstBreadOnlyKnockback = new VelocityData(data.entityID, data.transaction, data.vector);
                break; // All knockback after this will have not been applied
            } else if (data.transaction < transactionID) { // This kb has 100% arrived to the player
                if (firstBreadOnlyKnockback != null) // Don't require kb twice
                    lastKnockbackKnownTaken.add(new VelocityData(data.entityID, data.transaction, data.vector, data.offset));
                else
                    lastKnockbackKnownTaken.add(new VelocityData(data.entityID, data.transaction, data.vector));
                firstBreadOnlyKnockback = null;
                firstBreadMap.poll();
                data = firstBreadMap.peek();
            } else { // We are too far ahead in the future
                break;
            }
        }
    }

    public void forceExempt() {
        // Unsure knockback was taken
        if (player.firstBreadKB != null) {
            player.firstBreadKB.offset = 0;
        }

        if (player.likelyKB != null) {
            player.likelyKB.offset = 0;
        }
    }

    public void handlePredictionAnalysis(double offset, Vector vector) {
        if (vector.lengthSquared() < player.uncertaintyHandler.getZeroPointZeroThreeThreshold())
            wasExplosionZeroPointZeroThree = true;

        if (player.firstBreadKB != null) {
            player.firstBreadKB.offset = Math.min(player.firstBreadKB.offset, offset);
        }

        if (player.likelyKB != null) {
            player.likelyKB.offset = Math.min(player.likelyKB.offset, offset);
        }
    }

    public void handlePlayerKb(double offset, boolean force) {
        boolean wasZero = wasExplosionZeroPointZeroThree;
        wasExplosionZeroPointZeroThree = false;

        if (player.likelyKB == null && player.firstBreadKB == null) {
            return;
        }

        if (!force && !wasZero && player.predictedVelocity.isKnockback() &&
                player.likelyKB == null && player.firstBreadKB != null) {
            // The player took this knockback, this tick, 100%
            // Fixes exploit that would allow players to take knockback an infinite number of times
            if (player.firstBreadKB.offset < offsetToFlag) {
                firstBreadOnlyKnockback = null;
            }
        }

        if (force || wasZero || player.predictedVelocity.isKnockback()) {
            // Unsure knockback was taken
            if (player.firstBreadKB != null) {
                player.firstBreadKB.offset = Math.min(player.firstBreadKB.offset, offset);
            }

            // 100% known kb was taken
            if (player.likelyKB != null) {
                player.likelyKB.offset = Math.min(player.likelyKB.offset, offset);
            }
        }

        if (player.likelyKB != null) {
            if (player.likelyKB.offset > offsetToFlag) {
                increaseViolations();

                String formatOffset = "o: " + formatOffset(player.likelyKB.offset);

                if (player.likelyKB.offset == Integer.MAX_VALUE) {
                    formatOffset = "ignored knockback";
                }

                alert(formatOffset, "AntiKB", GrimMath.floor(violations) + "");
            }
        }
    }

    public VelocityData getFirstBreadOnlyKnockback(int entityID, int transaction) {
        tickKnockback(transaction);
        if (firstBreadOnlyKnockback != null && firstBreadOnlyKnockback.entityID == entityID)
            return firstBreadOnlyKnockback;
        return null;
    }

    @Override
    public void reload() {
        super.reload();
        offsetToFlag = getConfig().getDouble("Knockback.threshold", 0.00001);
        setbackVL = getConfig().getDouble("Knockback.setbackvl", 10);
    }
}
