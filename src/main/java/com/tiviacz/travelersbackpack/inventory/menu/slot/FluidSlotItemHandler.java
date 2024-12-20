package com.tiviacz.travelersbackpack.inventory.menu.slot;

import com.tiviacz.travelersbackpack.inventory.BackpackWrapper;
import com.tiviacz.travelersbackpack.inventory.ITravelersBackpackContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.items.SlotItemHandler;

public class FluidSlotItemHandler extends SlotItemHandler
{
    private final int index;
    private final BackpackWrapper wrapper;

    public FluidSlotItemHandler(BackpackWrapper wrapper, int index, int xPosition, int yPosition)
    {
        super(wrapper.getFluidSlotsHandler(), index, xPosition, yPosition);
        this.index = index;
        this.wrapper = wrapper;

        //0 - left in
        //1 - left out
        //2 - right in
        //3 - right out
    }

    @Override
    public boolean mayPickup(Player playerIn)
    {
        if(wrapper.getRows() <= 4)
        {
            if(index == 1 || index == 3)
            {
                return this.hasItem();
            }
        }
        return true;
    }

    @Override
    public boolean isActive()
    {
        if(wrapper.getRows() <= 4)
        {
            if(index == 1 || index == 3)
            {
                return this.hasItem();
            }
        }
        return true;
    }

    @Override
    public void setChanged()
    {
        super.setChanged();
        wrapper.updateTankSlots();
    }
}