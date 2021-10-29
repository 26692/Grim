package ac.grim.grimac.utils.blockstate.helper;

import ac.grim.grimac.utils.anticheat.Version;
import ac.grim.grimac.utils.blockstate.BaseBlockState;
import ac.grim.grimac.utils.blockstate.FlatBlockState;
import ac.grim.grimac.utils.blockstate.MagicBlockState;
import org.bukkit.Material;

public class BlockStateHelper {
    public static BaseBlockState create(Material material) {
        return Version.isFlat() ? new FlatBlockState(material) : new MagicBlockState(material);
    }
}
