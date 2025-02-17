package icbm.classic.content.machines.launcher.cruise;

import com.builtbroken.mc.api.items.ISimpleItemRenderer;
import com.builtbroken.mc.core.network.packet.PacketType;
import com.builtbroken.mc.imp.transform.region.Cube;
import com.builtbroken.mc.imp.transform.vector.Pos;
import com.builtbroken.mc.prefab.tile.Tile;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import icbm.classic.ICBMClassic;
import icbm.classic.client.models.MXiaoFaSheQi;
import icbm.classic.client.models.MXiaoFaSheQiJia;
import icbm.classic.client.render.RenderMissile;
import icbm.classic.content.explosive.Explosives;
import icbm.classic.content.explosive.ex.Explosion;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import org.lwjgl.opengl.GL11;

/**
 * @see <a href="https://github.com/BuiltBrokenModding/VoltzEngine/blob/development/license.md">License</a> for what you can and can't do with the code.
 * Created by Dark(DarkGuardsman, Robert) on 1/10/2017.
 */
public class TileCruiseLauncherClient extends TileCruiseLauncher implements ISimpleItemRenderer
{
    public static final ResourceLocation TEXTURE_FILE = new ResourceLocation(ICBMClassic.DOMAIN, "textures/models/" + "cruise_launcher.png");

    public static final MXiaoFaSheQi MODEL0 = new MXiaoFaSheQi();
    public static final MXiaoFaSheQiJia MODEL1 = new MXiaoFaSheQiJia();

    private ItemStack cachedMissileStack;

    public TileCruiseLauncherClient()
    {
        super();
        this.renderNormalBlock = false;
        this.renderTileEntity = true;
    }

    @Override
    public Tile newTile()
    {
        return new TileCruiseLauncherClient();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderDynamic(Pos pos, float deltaFrame, int pass)
    {
        float yaw = (float)currentAim.yaw();
        float pitch = (float)currentAim.pitch();

        GL11.glPushMatrix();
        GL11.glTranslatef((float) pos.x() + 0.5F, (float) pos.y() + 1.5F, (float) pos.z() + 0.5F);
        FMLClientHandler.instance().getClient().renderEngine.bindTexture(TEXTURE_FILE);
        GL11.glRotatef(180F, 0.0F, 0.0F, 1.0F);
        MODEL0.render(0.0625F);
        GL11.glRotatef(-yaw, 0F, 1F, 0F);
        GL11.glRotatef(-pitch, 1F, 0F, 0F);
        MODEL1.render(0.0625F);
        GL11.glPopMatrix();

        if (cachedMissileStack != null)
        {
            GL11.glPushMatrix();
            GL11.glTranslatef((float) pos.x() + 0.5F, (float) pos.y() + 1, (float) pos.z() + 0.5f);
            GL11.glRotatef(yaw, 0F, 1F, 0F);
            GL11.glRotatef(pitch-90, 1F, 0F, 0F);

            Explosives e = Explosives.get(cachedMissileStack.getItemDamage());
            Explosion missile = e == null ? (Explosion) Explosives.CONDENSED.handler : (Explosion) e.handler;
            if (missile.missileModelPath.contains("missiles"))
            {
                GL11.glScalef(0.00625f, 0.00625f, 0.00625f);
            }
            else
            {
                GL11.glScalef(0.05f, 0.05f, 0.05f);
            }
            RenderMissile.renderMissile(missile);
            GL11.glPopMatrix();
        }
    }

    @Override
    public void renderInventoryItem(IItemRenderer.ItemRenderType type, ItemStack itemStack, Object... data)
    {
        GL11.glPushMatrix();
        GL11.glTranslatef(0f, 0.4f, 0f);
        GL11.glRotatef(180f, 0f, 0f, 1f);
        GL11.glScalef(0.55f, 0.5f, 0.55f);

        FMLClientHandler.instance().getClient().renderEngine.bindTexture(TEXTURE_FILE);

        MODEL0.render(0.0625F);
        MODEL1.render(0.0625F);
        GL11.glPopMatrix();
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player)
    {
        return new GuiCruiseLauncher(player, this);
    }

    @Override
    public void readDescPacket(ByteBuf buf)
    {
        super.readDescPacket(buf);
        if (buf.readBoolean())
        {
            cachedMissileStack = ByteBufUtils.readItemStack(buf);
        }
        else
        {
            cachedMissileStack = null;
        }
        setTarget(new Pos(buf.readInt(), buf.readInt(), buf.readInt()));
    }

    @Override
    public boolean read(ByteBuf data, int id, EntityPlayer player, PacketType type)
    {
        if (!super.read(data, id, player, type))
        {
            switch (id)
            {
                //GUI description packet
                case 0:
                {
                    setEnergy(data.readInt());
                    this.setFrequency(data.readInt());
                    this.setTarget(new Pos(data.readInt(), data.readInt(), data.readInt()));
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    @Override
    public IIcon getIcon()
    {
        return Blocks.anvil.getIcon(0, 0);
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox()
    {
        return new Cube(-1, 0, -1, 1, 3, 1).add(toPos()).toAABB();
    }
}
