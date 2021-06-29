package ac.grim.grimac.events.packets;

import ac.grim.grimac.GrimAC;
import ac.grim.grimac.player.GrimPlayer;
import io.github.retrooper.packetevents.event.PacketListenerAbstract;
import io.github.retrooper.packetevents.event.PacketListenerPriority;
import io.github.retrooper.packetevents.event.impl.PacketPlayReceiveEvent;
import io.github.retrooper.packetevents.event.impl.PacketPlaySendEvent;
import io.github.retrooper.packetevents.packettype.PacketType;
import io.github.retrooper.packetevents.packetwrappers.play.in.transaction.WrappedPacketInTransaction;

public class PacketPingListener extends PacketListenerAbstract {

    // Must listen on LOWEST (maybe low) to stop Tuinity packet limiter from kicking players for transaction spam
    public PacketPingListener() {
        super(PacketListenerPriority.LOWEST);
    }

    @Override
    public void onPacketPlayReceive(PacketPlayReceiveEvent event) {
        byte packetID = event.getPacketId();

        if (packetID == PacketType.Play.Client.TRANSACTION) {
            WrappedPacketInTransaction transaction = new WrappedPacketInTransaction(event.getNMSPacket());
            short id = transaction.getActionNumber();

            // Vanilla always uses an ID starting from 1
            if (id <= 0) {
                GrimPlayer player = GrimAC.playerGrimHashMap.get(event.getPlayer());
                if (player == null) return;
                player.addTransactionResponse(id);
                event.setCancelled(true);
            }
        }
    }

    @Override
    public void onPacketPlaySend(PacketPlaySendEvent event) {
        byte packetID = event.getPacketId();

        if (packetID == PacketType.Play.Server.TRANSACTION) {
            WrappedPacketInTransaction transaction = new WrappedPacketInTransaction(event.getNMSPacket());
            short id = transaction.getActionNumber();

            // Vanilla always uses an ID starting from 1
            if (id < 0) {
                GrimPlayer player = GrimAC.playerGrimHashMap.get(event.getPlayer());
                if (player == null) return;
                player.addTransactionSend(id);
            }
        }
    }
}
