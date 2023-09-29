Hi, I'm a grim user from China and I found a 100% vulnerability in velocity

package net.ccbluex.liquidbounce.features.module.modules.combat;

import java.lang.reflect.Field;
import net.ccbluex.liquidbounce.api.minecraft.client.entity.IEntityPlayerSP;
import net.ccbluex.liquidbounce.api.minecraft.client.multiplayer.IWorldClient;
import net.ccbluex.liquidbounce.event.EventTarget;
import net.ccbluex.liquidbounce.event.PacketEvent;
import net.ccbluex.liquidbounce.event.TickEvent;
import net.ccbluex.liquidbounce.features.module.Module;
import net.ccbluex.liquidbounce.features.module.ModuleCategory;
import net.ccbluex.liquidbounce.features.module.ModuleInfo;
import net.ccbluex.liquidbounce.injection.backend.MinecraftImpl;
import net.ccbluex.liquidbounce.injection.backend.PacketImpl;
import net.ccbluex.liquidbounce.injection.backend.WorldClientImpl;
import net.ccbluex.liquidbounce.utils.ClientUtils;
import net.ccbluex.liquidbounce.value.BoolValue;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import net.minecraft.network.play.server.SPacketExplosion;
import net.minecraft.network.play.server.SPacketPlayerPosLook;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Timer;
import net.minecraft.util.math.BlockPos;


@ModuleInfo(name="GrimVelocity", description="GrimVelocity", category=ModuleCategory.COMBAT)
public final class GrimVelocity4
        extends Module {
    private final BoolValue sendC03Value = new BoolValue("SendC03", true);
    private final BoolValue breakValue = new BoolValue("BreakBlock", true);
    private final BoolValue alwayValue = new BoolValue("Alway", false);

    private boolean gotVelo = false;
    private boolean lastWasTeleport = false;

    @Override
    public void onEnable() {
        this.gotVelo = false;
        this.lastWasTeleport = false;
    }

    @EventTarget
    public final void onTick(final TickEvent event) {
        IEntityPlayerSP thePlayer = mc.getThePlayer();
        if (thePlayer == null) return;
        IWorldClient theWorld = mc.getTheWorld();
        if (theWorld == null) return;
        Timer timer = ((MinecraftImpl) mc).getWrapped().timer;
        if (timer == null) return;
        if (this.alwayValue.get() || this.gotVelo) {
            NetHandlerPlayClient connection = ((MinecraftImpl) mc).getWrapped().getConnection();
            if (connection == null) return;
            this.gotVelo = false;
            if (this.sendC03Value.get()) {
                connection.sendPacket(new CPacketPlayer(thePlayer.getOnGround()));
                try {
                    Field f = timer.getClass().getDeclaredField("field_74277_g");
                    f.setAccessible(true);
                    long t = (long) f.get(timer);
                    f.set(timer, t + 50L);
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            BlockPos pos = new BlockPos(thePlayer.getPosX(), thePlayer.getPosY() + 1.0, thePlayer.getPosZ());
            connection.sendPacket(new CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, pos, EnumFacing.DOWN));
            if (this.breakValue.get()) {
                ((WorldClientImpl) theWorld).getWrapped().setBlockToAir(pos);
            }
        }
    }

    @EventTarget
    public final void onPacket(final PacketEvent event) {
        IEntityPlayerSP thePlayer = mc.getThePlayer();
        if (thePlayer == null) return;
        Packet<?> packet = ((PacketImpl) event.getPacket()).getWrapped();
        if (packet instanceof SPacketPlayerPosLook) {
            this.lastWasTeleport = true;
        } else if (!this.lastWasTeleport && packet instanceof SPacketEntityVelocity) {
            SPacketEntityVelocity veloPacket = (SPacketEntityVelocity) packet;
            if (veloPacket.getEntityID() == thePlayer.getEntityId()) {
                event.cancelEvent();
                this.gotVelo = true;
            }
        } else if (packet instanceof SPacketExplosion) {
            SPacketExplosion veloPacket = (SPacketExplosion) packet;
            if (veloPacket.getMotionX() != 0f || veloPacket.getMotionY() != 0f || veloPacket.getMotionZ() != 0f) {
                event.cancelEvent();
                this.gotVelo = true;
            }
        } else if (packet.getClass().getName().startsWith("net.minecraft.network.play.server.SPacket")) {
            this.lastWasTeleport = false;
        }
    }
}
