package icbm.classic.content.machines.radarstation;

import com.builtbroken.mc.api.computer.DataMethodType;
import com.builtbroken.mc.api.computer.DataSystemMethod;
import com.builtbroken.mc.api.items.hz.IItemFrequency;
import com.builtbroken.mc.api.map.radio.IRadioWaveSender;
import com.builtbroken.mc.api.tile.access.IGuiTile;
import com.builtbroken.mc.core.network.IPacketIDReceiver;
import com.builtbroken.mc.core.network.packet.PacketTile;
import com.builtbroken.mc.core.network.packet.PacketType;
import com.builtbroken.mc.core.registry.implement.IRecipeContainer;
import com.builtbroken.mc.imp.transform.region.Cube;
import com.builtbroken.mc.imp.transform.vector.Point;
import com.builtbroken.mc.imp.transform.vector.Pos;
import com.builtbroken.mc.lib.helper.LanguageUtility;
import com.builtbroken.mc.lib.helper.WrenchUtility;
import com.builtbroken.mc.lib.helper.recipe.UniversalRecipe;
import com.builtbroken.mc.lib.world.map.radar.RadarRegistry;
import com.builtbroken.mc.lib.world.map.radio.RadioRegistry;
import com.builtbroken.mc.prefab.gui.ContainerDummy;
import com.builtbroken.mc.prefab.items.ItemBlockBase;
import com.builtbroken.mc.prefab.tile.Tile;
import com.builtbroken.mc.prefab.tile.module.TileModuleInventory;
import cpw.mods.fml.common.registry.GameRegistry;
import icbm.classic.ICBMClassic;
import icbm.classic.content.entity.EntityMissile;
import icbm.classic.prefab.TileFrequency;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.oredict.ShapedOreRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TileRadarStation extends TileFrequency implements IPacketIDReceiver, IRadioWaveSender, IRecipeContainer, IGuiTile
{
    /** Max range the radar station will attempt to find targets inside */
    public final static int MAX_DETECTION_RANGE = 500;

    public final static int GUI_PACKET_ID = 1;


    /** Energy used per tick */
    public static final int ENERGY_USAGE = 20;
    public static final int BUFFER_SIZE = 20000;

    public float rotation = 0;
    public int alarmRange = 100;
    public int safetyRange = 50;

    public boolean emitAll = true;

    public List<Entity> detectedEntities = new ArrayList<Entity>();
    /** List of all incoming missiles, in order of distance. */
    private List<EntityMissile> incomingMissiles = new ArrayList<EntityMissile>();

    public TileRadarStation()
    {
        super("radarStation", Material.iron);
        this.itemBlock = ItemBlockBase.class;
        this.hardness = 10f;
        this.resistance = 10f;
        this.isOpaque = false;
        this.renderTileEntity = true;
        this.renderNormalBlock = false;
        this.canEmmitRedstone = true;
    }

    @DataSystemMethod(name = "incomingMissileData", type = DataMethodType.GET)
    public Object[] getIncomingMissilePos()
    {
        HashMap<String, Double> data = new HashMap();
        data.put("size", (double) incomingMissiles.size());

        for (int i = 0; i < incomingMissiles.size(); i++)
        {
            EntityMissile missile = incomingMissiles.get(i);
            data.put("dim_" + i, (double) missile.dimension);

            data.put("X_" + i, missile.posX);
            data.put("Y_" + i, missile.posY);
            data.put("Z_" + i, missile.posZ);

            data.put("VX_" + i, missile.motionX);
            data.put("VY_" + i, missile.motionY);
            data.put("VZ_" + i, missile.motionZ);
            return new Object[]{data};
        }
        return null;
    }

    @DataSystemMethod(name = "detectedEntityData", type = DataMethodType.GET)
    public Object[] getDetectedMissilePos()
    {
        HashMap<String, Double> data = new HashMap();
        data.put("size", (double) detectedEntities.size());

        for (int i = 0; i < detectedEntities.size(); i++)
        {
            Entity missile = detectedEntities.get(i);
            data.put("dim_" + i, (double) missile.dimension);

            data.put("X_" + i, missile.posX);
            data.put("Y_" + i, missile.posY);
            data.put("Z_" + i, missile.posZ);

            data.put("VX_" + i, missile.motionX);
            data.put("VY_" + i, missile.motionY);
            data.put("VZ_" + i, missile.motionZ);
            return new Object[]{data};
        }
        return null;
    }

    @Override
    protected IInventory createInventory()
    {
        return new TileModuleInventory(this, 2);
    }

    @Override
    public Tile newTile()
    {
        return new TileRadarStation();
    }

    @Override
    public void firstTick()
    {
        super.firstTick();
        this.worldObj.notifyBlocksOfNeighborChange(this.xCoord, this.yCoord, this.zCoord, this.worldObj.getBlock(this.xCoord, this.yCoord, this.zCoord));
    }

    @Override
    public void update()
    {
        super.update();

        if (!this.worldObj.isRemote)
        {
            //Update client every 2 seconds
            if (this.ticks % 40 == 0)
            {
                sendDescPacket();
            }
        }

        //If we have energy
        if (checkExtract())
        {
            this.rotation += 0.08f;

            if (this.rotation > 360)
            {
                this.rotation = 0;
            }

            if (!this.worldObj.isRemote)
            {
                this.extractEnergy();
            }

            int prevDetectedEntities = this.detectedEntities.size();

            if (isServer())
            {
                // Do a radar scan
                this.doScan();
            }

            if (prevDetectedEntities != this.detectedEntities.size())
            {
                this.worldObj.notifyBlocksOfNeighborChange(this.xCoord, this.yCoord, this.zCoord, this.getBlockType());
            }
            //Check for incoming and launch anti-missiles if
            if (this.ticks % 20 == 0 && this.incomingMissiles.size() > 0)
            {
                RadioRegistry.popMessage(oldWorld(), this, getFrequency(), "fireAntiMissile", this.incomingMissiles.get(0));
            }
        }
        else
        {
            if (detectedEntities.size() > 0)
            {
                worldObj.notifyBlocksOfNeighborChange(this.xCoord, this.yCoord, this.zCoord, this.getBlockType());
            }

            incomingMissiles.clear();
            detectedEntities.clear();
        }

        if (ticks % 40 == 0)
        {
            worldObj.notifyBlocksOfNeighborChange(this.xCoord, this.yCoord, this.zCoord, this.getBlockType());
        }
    }

    @Override
    public int getEnergyConsumption()
    {
        return ENERGY_USAGE;
    }

    @Override
    public int getEnergyBufferSize()
    {
        return BUFFER_SIZE;
    }

    private void doScan()
    {
        this.incomingMissiles.clear();
        this.detectedEntities.clear();

        List<Entity> entities = RadarRegistry.getAllLivingObjectsWithin(oldWorld(), xi() + 1.5, yi() + 0.5, zi() + 0.5, MAX_DETECTION_RANGE, null);

        for (Entity entity : entities)
        {
            if (entity instanceof EntityMissile)
            {
                if (((EntityMissile) entity).getTicksInAir() > -1)
                {
                    if (!this.detectedEntities.contains(entity))
                    {
                        this.detectedEntities.add(entity);
                    }

                    if (this.isMissileGoingToHit((EntityMissile) entity))
                    {
                        if (this.incomingMissiles.size() > 0)
                        {
                            /** Sort in order of distance */
                            double dist = new Pos((TileEntity) this).distance(new Pos(entity));

                            for (int i = 0; i < this.incomingMissiles.size(); i++)
                            {
                                EntityMissile daoDan = this.incomingMissiles.get(i);

                                if (dist < new Pos((TileEntity) this).distance(daoDan.toPos()))
                                {
                                    this.incomingMissiles.add(i, (EntityMissile) entity);
                                    break;
                                }
                                else if (i == this.incomingMissiles.size() - 1)
                                {
                                    this.incomingMissiles.add((EntityMissile) entity);
                                    break;
                                }
                            }
                        }
                        else
                        {
                            this.incomingMissiles.add((EntityMissile) entity);
                        }
                    }
                }
            }
            else
            {
                this.detectedEntities.add(entity);
            }
        }

        List<EntityPlayer> players = this.worldObj.getEntitiesWithinAABB(EntityPlayer.class, AxisAlignedBB.getBoundingBox(this.xCoord - MAX_DETECTION_RANGE, this.yCoord - MAX_DETECTION_RANGE, this.zCoord - MAX_DETECTION_RANGE, this.xCoord + MAX_DETECTION_RANGE, this.yCoord + MAX_DETECTION_RANGE, this.zCoord + MAX_DETECTION_RANGE));

        for (EntityPlayer player : players)
        {
            if (player != null)
            {
                boolean youHuoLuan = false;

                for (int i = 0; i < player.inventory.getSizeInventory(); i++)
                {
                    ItemStack itemStack = player.inventory.getStackInSlot(i);

                    if (itemStack != null)
                    {
                        if (itemStack.getItem() instanceof IItemFrequency)
                        {
                            youHuoLuan = true;
                            break;
                        }
                    }
                }

                if (!youHuoLuan)
                {
                    this.detectedEntities.add(player);
                }
            }
        }
    }

    /**
     * Checks to see if the missile will hit within the range of the radar station
     *
     * @param missile - missile being checked
     * @return true if it will
     */
    public boolean isMissileGoingToHit(EntityMissile missile)
    {
        if (missile == null || missile.targetVector == null)
        {
            return false;
        }
        return (missile.toPos().toVector2().distance(new Point(this.xCoord, this.zCoord)) < this.alarmRange && missile.targetVector.toVector2().distance(new Point(this.xCoord, this.zCoord)) < this.safetyRange);
    }

    @Override
    protected PacketTile getGUIPacket()
    {
        PacketTile packet = new PacketTile(this, GUI_PACKET_ID, this.alarmRange, this.safetyRange, this.getFrequency());
        packet.add(detectedEntities.size());
        if (detectedEntities.size() > 0)
        {
            for (Entity entity : detectedEntities)
            {
                if (entity != null && entity.isEntityAlive())
                {
                    packet.add(entity.getEntityId());
                }
                else
                {
                    packet.add(-1);
                }
            }
        }
        return packet;
    }

    @Override
    public void readDescPacket(ByteBuf buf)
    {
        super.readDescPacket(buf);
        setEnergy(buf.readInt());
    }

    @Override
    public void writeDescPacket(ByteBuf buf)
    {
        super.writeDescPacket(buf);
        buf.writeInt(getEnergy());
    }

    @Override
    public boolean read(ByteBuf data, int ID, EntityPlayer player, PacketType type)
    {
        if (!super.read(data, ID, player, type))
        {
            if (this.worldObj.isRemote)
            {
                if (ID == GUI_PACKET_ID)
                {
                    this.alarmRange = data.readInt();
                    this.safetyRange = data.readInt();
                    this.setFrequency(data.readInt());

                    detectedEntities.clear();
                    int entityListSize = data.readInt();
                    for (int i = 0; i < entityListSize; i++)
                    {
                        int id = data.readInt();
                        if (id != -1)
                        {
                            Entity entity = oldWorld().getEntityByID(id);
                            if (entity != null)
                            {
                                detectedEntities.add(entity);
                            }
                        }
                    }
                    return true;
                }
            }
            else if (!this.worldObj.isRemote)
            {
                if (ID == 2)
                {
                    this.safetyRange = data.readInt();
                    return true;
                }
                else if (ID == 3)
                {
                    this.alarmRange = data.readInt();
                    return true;
                }
                else if (ID == 4)
                {
                    this.setFrequency(data.readInt());
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    @Override
    public int getStrongRedstonePower(int side)
    {
        if (incomingMissiles.size() > 0)
        {
            if (this.emitAll)
            {
                return Math.min(15, 5 + incomingMissiles.size());
            }

            for (EntityMissile incomingMissile : this.incomingMissiles)
            {
                Point position = incomingMissile.toPos().toVector2();
                ForgeDirection missileTravelDirection = ForgeDirection.UNKNOWN;
                double closest = -1;

                for (int i = 2; i < 6; i++)
                {
                    double dist = position.distance(new Point(this.xCoord + ForgeDirection.getOrientation(i).offsetX, this.zCoord + ForgeDirection.getOrientation(i).offsetZ));

                    if (dist < closest || closest < 0)
                    {
                        missileTravelDirection = ForgeDirection.getOrientation(i);
                        closest = dist;
                    }
                }

                if (missileTravelDirection.getOpposite().ordinal() == side)
                {
                    return Math.min(15, 5 + incomingMissiles.size());
                }
            }
        }

        return 0;
    }

    /** Reads a tile entity from NBT. */
    @Override
    public void readFromNBT(NBTTagCompound nbt)
    {
        super.readFromNBT(nbt);
        this.safetyRange = nbt.getInteger("safetyBanJing");
        this.alarmRange = nbt.getInteger("alarmBanJing");
        this.emitAll = nbt.getBoolean("emitAll");
    }

    /** Writes a tile entity to NBT. */
    @Override
    public void writeToNBT(NBTTagCompound nbt)
    {
        super.writeToNBT(nbt);
        nbt.setInteger("safetyBanJing", this.safetyRange);
        nbt.setInteger("alarmBanJing", this.alarmRange);
        nbt.setBoolean("emitAll", this.emitAll);
    }

    @Override
    protected boolean onPlayerRightClick(EntityPlayer entityPlayer, int side, Pos hit)
    {
        if (entityPlayer.inventory.getCurrentItem() != null)
        {
            if (WrenchUtility.isUsableWrench(entityPlayer, entityPlayer.inventory.getCurrentItem(), this.xCoord, this.yCoord, this.zCoord))
            {
                if (!this.worldObj.isRemote)
                {
                    this.emitAll = !this.emitAll;
                    entityPlayer.addChatMessage(new ChatComponentText(LanguageUtility.getLocal("message.radar.redstone") + " " + this.emitAll));
                }

                return true;
            }
        }

        if (isServer())
        {
            entityPlayer.openGui(ICBMClassic.INSTANCE, 0, this.worldObj, this.xCoord, this.yCoord, this.zCoord);
        }
        return true;
    }

    @Override
    public void sendRadioMessage(float hz, String header, Object... data)
    {
        RadioRegistry.popMessage(oldWorld(), this, hz, header, data);
    }

    @Override
    public Cube getRadioSenderRange()
    {
        return null;
    }

    @Override
    public void genRecipes(List<IRecipe> recipes)
    {
        // Radar Station
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ICBMClassic.blockRadarStation),
                "?@?", " ! ", "!#!",
                '@', new ItemStack(ICBMClassic.itemRadarGun),
                '!', UniversalRecipe.PRIMARY_PLATE.get(),
                '#', UniversalRecipe.CIRCUIT_T1.get(),
                '?', Items.gold_ingot));
    }

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player)
    {
        return new ContainerDummy(player, this);
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player)
    {
        return null;
    }
}
