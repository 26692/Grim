package ac.grim.grimac.manager.init.stop;

import ac.grim.grimac.manager.init.Initable;
import ac.grim.grimac.utils.anticheat.LogUtil;
import io.github.retrooper.packetevents.PacketEvents;

public class TerminatePacketEvents implements Initable {
    @Override
    public void start() {
        LogUtil.info("Terminating PacketEvents...");
        PacketEvents.get().terminate();
    }
}
