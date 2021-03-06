package mekanism.generators.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.MekanismConfig.generators;
import mekanism.common.FluidSlot;
import mekanism.common.MekanismItems;
import mekanism.common.base.ISustainedData;
import mekanism.common.util.ChargeUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.*;

import java.util.ArrayList;
import java.util.EnumSet;

public class TileEntityBioGenerator extends TileEntityGenerator implements IFluidHandler, ISustainedData
{
	/** The FluidSlot biofuel instance for this generator. */
	public FluidSlot bioFuelSlot = new FluidSlot(24000, -1);

	public TileEntityBioGenerator()
	{
		super("bio", "BioGenerator", 160000, generators.bioGeneration*2);
		inventory = new ItemStack[2];
	}

	@Override
	public void onUpdate()
	{
		super.onUpdate();

		if(inventory[0] != null)
		{
			ChargeUtils.charge(1, this);
			
			FluidStack fluid = FluidContainerRegistry.getFluidForFilledItem(inventory[0]);

			if(fluid != null && FluidRegistry.isFluidRegistered("bioethanol"))
			{
				if(fluid.getFluid() == FluidRegistry.getFluid("bioethanol"))
				{
					int fluidToAdd = fluid.amount;

					if(bioFuelSlot.fluidStored+fluidToAdd <= bioFuelSlot.MAX_FLUID)
					{
						bioFuelSlot.setFluid(bioFuelSlot.fluidStored+fluidToAdd);

						if(inventory[0].getItem().getContainerItem(inventory[0]) != null)
						{
							inventory[0] = inventory[0].getItem().getContainerItem(inventory[0]);
						}
						else {
							inventory[0].stackSize--;
						}

						if(inventory[0].stackSize == 0)
						{
							inventory[0] = null;
						}
					}
				}
			}
			else {
				int fuel = getFuel(inventory[0]);
				ItemStack prevStack = inventory[0].copy();

				if(fuel > 0)
				{
					int fuelNeeded = bioFuelSlot.MAX_FLUID - bioFuelSlot.fluidStored;

					if(fuel <= fuelNeeded)
					{
						bioFuelSlot.fluidStored += fuel;

						if(inventory[0].getItem().getContainerItem(inventory[0]) != null)
						{
							inventory[0] = inventory[0].getItem().getContainerItem(inventory[0]);
						}
						else {
							inventory[0].stackSize--;
						}

						if(inventory[0].stackSize == 0)
						{
							inventory[0] = null;
						}
					}
				}
			}
		}

		if(canOperate())
		{
			if(!worldObj.isRemote)
			{
				setActive(true);
			}

			bioFuelSlot.setFluid(bioFuelSlot.fluidStored - 1);
			setEnergy(electricityStored + generators.bioGeneration);
		}
		else {
			if(!worldObj.isRemote)
			{
				setActive(false);
			}
		}
	}

	@Override
	public boolean isItemValidForSlot(int slotID, ItemStack itemstack)
	{
		if(slotID == 0)
		{
			if(getFuel(itemstack ) > 0)
			{
				return true;
			}
			else {
				if(FluidRegistry.isFluidRegistered("bioethanol"))
				{
					if(FluidContainerRegistry.getFluidForFilledItem(itemstack) != null)
					{
						if(FluidContainerRegistry.getFluidForFilledItem(itemstack).getFluid() == FluidRegistry.getFluid("bioethanol"))
						{
							return true;
						}
					}
				}

				return false;
			}
		}
		else if(slotID == 1)
		{
			return ChargeUtils.canBeCharged(itemstack);
		}

		return true;
	}

	@Override
	public boolean canOperate()
	{
		return electricityStored < BASE_MAX_ENERGY && bioFuelSlot.fluidStored > 0 && MekanismUtils.canFunction(this);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbtTags)
	{
		super.readFromNBT(nbtTags);

		bioFuelSlot.fluidStored = nbtTags.getInteger("bioFuelStored");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbtTags)
	{
		super.writeToNBT(nbtTags);

		nbtTags.setInteger("bioFuelStored", bioFuelSlot.fluidStored);
	}

	public int getFuel(ItemStack itemstack)
	{
		return itemstack.getItem() == MekanismItems.BioFuel ? 200 : 0;
	}

	/**
	 * Gets the scaled fuel level for the GUI.
	 * @param i - multiplier
	 * @return
	 */
	public int getScaledFuelLevel(int i)
	{
		return bioFuelSlot.fluidStored*i / bioFuelSlot.MAX_FLUID;
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side)
	{
		return ForgeDirection.getOrientation(side) == MekanismUtils.getRight(facing) ? new int[] {1} : new int[] {0};
	}

	@Override
	public boolean canSetFacing(int facing)
	{
		return facing != 0 && facing != 1;
	}

	@Override
	public void handlePacketData(ByteBuf dataStream)
	{
		super.handlePacketData(dataStream);
		bioFuelSlot.fluidStored = dataStream.readInt();
	}

	@Override
	public ArrayList getNetworkedData(ArrayList data)
	{
		super.getNetworkedData(data);
		data.add(bioFuelSlot.fluidStored);
		return data;
	}

	@Override
	public EnumSet<ForgeDirection> getOutputtingSides()
	{
		return EnumSet.of(ForgeDirection.getOrientation(facing).getOpposite());
	}

    private static final String[] methods = new String[] {"getStored", "getOutput", "getMaxEnergy", "getEnergyNeeded", "getBioFuel", "getBioFuelNeeded"};

	@Override
	public String[] getMethods()
	{
		return methods;
	}

	@Override
	public Object[] invoke(int method, Object[] arguments) throws Exception
	{
		switch(method)
		{
			case 0:
				return new Object[] {electricityStored};
			case 1:
				return new Object[] {output};
			case 2:
				return new Object[] {BASE_MAX_ENERGY};
			case 3:
				return new Object[] {(BASE_MAX_ENERGY -electricityStored)};
			case 4:
				return new Object[] {bioFuelSlot.fluidStored};
			case 5:
				return new Object[] {bioFuelSlot.MAX_FLUID-bioFuelSlot.fluidStored};
			default:
				throw new NoSuchMethodException();
		}
	}

	@Override
	public int fill(ForgeDirection from, FluidStack resource, boolean doFill)
	{
		if(FluidRegistry.isFluidRegistered("bioethanol") && from != ForgeDirection.getOrientation(facing))
		{
			if(resource.getFluid() == FluidRegistry.getFluid("bioethanol"))
			{
				int fuelTransfer = 0;
				int fuelNeeded = bioFuelSlot.MAX_FLUID - bioFuelSlot.fluidStored;
				int attemptTransfer = resource.amount;

				if(attemptTransfer <= fuelNeeded)
				{
					fuelTransfer = attemptTransfer;
				}
				else {
					fuelTransfer = fuelNeeded;
				}

				if(doFill)
				{
					bioFuelSlot.setFluid(bioFuelSlot.fluidStored + fuelTransfer);
				}

				return fuelTransfer;
			}
		}

		return 0;
	}

	@Override
	public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain)
	{
		return null;
	}

	@Override
	public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain)
	{
		return null;
	}

	@Override
	public boolean canFill(ForgeDirection from, Fluid fluid)
	{
		return FluidRegistry.isFluidRegistered("bioethanol") && fluid == FluidRegistry.getFluid("bioethanol");
	}

	@Override
	public boolean canDrain(ForgeDirection from, Fluid fluid)
	{
		return false;
	}

	@Override
	public FluidTankInfo[] getTankInfo(ForgeDirection from)
	{
		return null;
	}

	@Override
	public void writeSustainedData(ItemStack itemStack)
	{
		itemStack.stackTagCompound.setInteger("fluidStored", bioFuelSlot.fluidStored);
	}

	@Override
	public void readSustainedData(ItemStack itemStack) 
	{
		bioFuelSlot.setFluid(itemStack.stackTagCompound.getInteger("fluidStored"));
	}
}