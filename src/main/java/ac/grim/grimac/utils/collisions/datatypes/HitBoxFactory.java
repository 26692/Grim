package ac.grim.grimac.utils.collisions.datatypes;

import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.blockdata.types.WrappedBlockDataValue;
import io.github.retrooper.packetevents.utils.player.ClientVersion;
import org.bukkit.Material;

public interface HitBoxFactory {
    CollisionBox fetch(GrimPlayer player, Material heldItem, ClientVersion version, WrappedBlockDataValue block, int x, int y, int z);
}
