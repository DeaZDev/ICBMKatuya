package icbm.classic.mod.waila;

import icbm.classic.ICBMClassic;
import icbm.classic.content.blocks.TileCamouflage;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

/**
 * Fixes waila showing camo blocks as something other than camo
 *
 * @see <a href="https://github.com/BuiltBrokenModding/VoltzEngine/blob/development/license.md">License</a> for what you can and can't do with the code.
 * Created by Dark(DarkGuardsman, Robert) on 10/29/2017.
 */
public class WailaCamoBlockHandler extends WailaHandler
{
    @Override
    public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config)
    {
        Block block = accessor.getBlock();
        if (block == ICBMClassic.blockCamo)
        {
            TileEntity tile = accessor.getTileEntity();
            if (tile instanceof TileCamouflage && ((TileCamouflage) tile).getMimicBlock() != null)
            {
                return new ItemStack(((TileCamouflage) tile).getMimicBlock(), 1, ((TileCamouflage) tile).getMimicBlockMeta());
            }
        }
        return null;
    }
}
