package com.tiviacz.travelersbackpack.inventory.menu;

import com.mojang.datafixers.util.Pair;
import com.tiviacz.travelersbackpack.TravelersBackpack;
import com.tiviacz.travelersbackpack.config.TravelersBackpackConfig;
import com.tiviacz.travelersbackpack.inventory.BackpackWrapper;
import com.tiviacz.travelersbackpack.inventory.CraftingContainerImproved;
import com.tiviacz.travelersbackpack.inventory.ITravelersBackpackContainer;
import com.tiviacz.travelersbackpack.inventory.menu.slot.BackpackSlotItemHandler;
import com.tiviacz.travelersbackpack.inventory.menu.slot.FluidSlotItemHandler;
import com.tiviacz.travelersbackpack.inventory.menu.slot.ResultSlotExt;
import com.tiviacz.travelersbackpack.inventory.menu.slot.ToolSlotItemHandler;
import com.tiviacz.travelersbackpack.inventory.sorter.SlotManager;
import com.tiviacz.travelersbackpack.network.ClientboundUpdateRecipePacket;
import com.tiviacz.travelersbackpack.util.ItemStackUtils;
import com.tiviacz.travelersbackpack.util.Reference;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import net.minecraftforge.network.PacketDistributor;

public class TravelersBackpackBaseMenu extends AbstractContainerMenu
{
    public Inventory inventory;
    public BackpackWrapper wrapper;
    public CraftingContainerImproved craftSlots;
    public ResultContainer resultSlots = new ResultContainer();
    public Player player;

    private final int BACKPACK_INV_START = 1, BACKPACK_INV_END;
    private final int TOOL_START, TOOL_END;
    private final int BUCKET_LEFT_IN, BUCKET_LEFT_OUT;
    private final int BUCKET_RIGHT_IN, BUCKET_RIGHT_OUT;
    private final int PLAYER_INV_START, PLAYER_HOT_END;
    private final int CRAFTING_GRID_START, CRAFTING_GRID_END;

    public TravelersBackpackBaseMenu(final MenuType<?> type, final int windowID, final Inventory inventory, final BackpackWrapper wrapper)
    {
        super(type, windowID);
        this.inventory = inventory;
        this.player = inventory.player;
        this.wrapper = wrapper;
        this.craftSlots = new CraftingContainerImproved(wrapper, this);

        //Craft result = 0;
        this.BACKPACK_INV_END = BACKPACK_INV_START + wrapper.getHandler().getSlots() - 1;
        this.TOOL_START = BACKPACK_INV_END + 1;
        this.TOOL_END = TOOL_START + wrapper.getToolSlotsHandler().getSlots() - 1;
        this.BUCKET_LEFT_IN = TOOL_END + 1;
        this.BUCKET_LEFT_OUT = BUCKET_LEFT_IN + 1;
        this.BUCKET_RIGHT_IN = BUCKET_LEFT_OUT + 1;
        this.BUCKET_RIGHT_OUT = BUCKET_RIGHT_IN + 1;
        this.CRAFTING_GRID_START = BUCKET_RIGHT_OUT + 1;
        this.CRAFTING_GRID_END = CRAFTING_GRID_START + 8;
        this.PLAYER_INV_START = CRAFTING_GRID_END + 1;
        this.PLAYER_HOT_END =  PLAYER_INV_START + 35;

        //Craft Result
        this.addCraftResult();

        //Backpack Inventory
        this.addBackpackInventory(wrapper);

        //Functional Slots
        this.addToolSlots(wrapper);
        this.addFluidSlots(wrapper);

        //Crafting Widget
        this.addCraftingSlots();

        //Player Inventory
        this.addPlayerInventoryAndHotbar(inventory, inventory.selected);
    }

    public BackpackWrapper getWrapper() {
        return this.wrapper;
    }

    public void addCraftResult()
    {
        this.addSlot(new ResultSlotExt(wrapper, inventory.player, this.craftSlots, this.resultSlots, 0, 270, 113));
    }

    public void addBackpackInventory(BackpackWrapper wrapper)
    {
        int slot = 0;

        for(int i = 0; i < wrapper.getRows(); i++)
        {
            int lastRowSlots = wrapper.getHandler().getSlots() % 9;
            if(lastRowSlots == 0) lastRowSlots = 9;

            int slotsInRow = i == wrapper.getRows() - 1 ? lastRowSlots : 9;

            for(int j = 0; j < slotsInRow; j++)
            {
                this.addSlot(new BackpackSlotItemHandler(wrapper.getHandler(), slot++, 44 + j * 18, 7 + i * 18));
            }
        }
    }

    public void addFluidSlots(BackpackWrapper wrapper)
    {
        //Left In bucket
        this.addSlot(new FluidSlotItemHandler(wrapper, 0, 6, 7)
        {
            @Override
            public boolean isActive()
            {
                return !TravelersBackpackBaseMenu.this.wrapper.getSettingsManager().showToolSlots() && super.isActive();
            }
        });

        //Left Out bucket
        this.addSlot(new FluidSlotItemHandler(wrapper, 1, 6, 37)
        {
            @Override
            public boolean isActive()
            {
                return !TravelersBackpackBaseMenu.this.wrapper.getSettingsManager().showToolSlots() && super.isActive();
            }
        });

        //Right In bucket
        this.addSlot(new FluidSlotItemHandler(wrapper, 2, 226, 7));

        //Right Out bucket
        this.addSlot(new FluidSlotItemHandler(wrapper, 3, 226, 37));
    }

    public void addToolSlots(BackpackWrapper wrapper)
    {
        for(int i = 0; i < wrapper.getToolSlotsHandler().getSlots(); i++)
        {
            this.addSlot(new ToolSlotItemHandler(inventory.player, wrapper, i, 6, 7 + 18 * i));
        }
    }

    public void addCraftingSlots()
    {
        for(int i = 0; i < 3; i++)
        {
            for(int j = 0; j < 3; j++)
            {
                this.addSlot(new Slot(this.craftSlots, j + i * 3, 252 + j * 18, 47 + i * 18)
                {
                    @Override
                    public boolean isActive()
                    {
                        return TravelersBackpackBaseMenu.this.wrapper.getSettingsManager().showCraftingGrid();
                    }

                    @Override
                    public boolean mayPlace(ItemStack stack)
                    {
                        return TravelersBackpackBaseMenu.this.wrapper.getSettingsManager().hasCraftingGrid();
                    }
                });
            }
        }
    }

    public void addPlayerInventoryAndHotbar(Inventory inventory, int currentItemIndex)
    {
        for(int y = 0; y < 3; y++)
        {
            for(int x = 0; x < 9; x++)
            {
                this.addSlot(new Slot(inventory, x + y * 9 + 9, 44 + x*18, (71 + this.wrapper.getYOffset()) + y*18));
            }
        }

        for(int x = 0; x < 9; x++)
        {
            this.addSlot(new Slot(inventory, x, 44 + x*18, 129 + this.wrapper.getYOffset()));
        }
    }

    protected void canCraft(Level level, Player player)
    {
        if(wrapper.getSettingsManager().hasCraftingGrid())
        {
            slotChangedCraftingGrid(level, player);
        }
    }

    @Override
    public void slotsChanged(Container container)
    {
        super.slotsChanged(container);
        canCraft(inventory.player.level(), inventory.player);
    }

    @Override
    public void sendAllDataToRemote()
    {
        super.sendAllDataToRemote();

        //Sync on opening
        this.slotsChanged(new RecipeWrapper(wrapper.getCraftingGridHandler()));
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot)
    {
        return slot.container != this.resultSlots && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        Slot slot = getSlot(index);
        ItemStack result = ItemStack.EMPTY;

        if(slot != null && slot.hasItem())
        {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if(index >= 0 && index <= CRAFTING_GRID_END) //BUCKETRIGHTOUT??
            {
                if(index == 0)
                {
                    return handleShiftCraft(player, slot);
                }

                else if(!moveItemStackTo(stack, PLAYER_INV_START, PLAYER_HOT_END + 1, true))
                {
                    return ItemStack.EMPTY;
                }
            }

            if(index >= PLAYER_INV_START)
            {
                //Check Memory Slots
                if(!wrapper.getSlotManager().getMemorySlots().isEmpty())
                {
                    for(Pair<Integer, ItemStack> pair : wrapper.getSlotManager().getMemorySlots())
                    {
                        if(ItemStackUtils.isSameItemSameTags(pair.getSecond(), stack) && getSlot(pair.getFirst() + 1).getItem().getCount() != getSlot(pair.getFirst() + 1).getItem().getMaxStackSize())
                        {
                            if(moveItemStackTo(stack, pair.getFirst() + 1, pair.getFirst() + 2, false))
                            {
                                break;
                            }
                        }
                    }
                }

                if(ToolSlotItemHandler.isValid(stack))
                {
                    if(!moveItemStackTo(stack, TOOL_START, TOOL_END + 1, false))
                    {
                        if(!moveItemStackTo(stack, BACKPACK_INV_START, BACKPACK_INV_END + 1, false))
                        {
                            return ItemStack.EMPTY;
                        }
                    }
                }

                if(!moveItemStackTo(stack, BACKPACK_INV_START, BACKPACK_INV_END + 1, false))
                {
                    return ItemStack.EMPTY;
                }
            }

            if(stack.isEmpty())
            {
                slot.set(ItemStack.EMPTY);
            }

            else
            {
                slot.setChanged();
            }

            if(stack.getCount() == result.getCount())
            {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, stack);
        }
        return result;
    }

    public ItemStack handleShiftCraft(Player player, Slot resultSlot)
    {
        ItemStack outputCopy = ItemStack.EMPTY;

        if(resultSlot != null && resultSlot.hasItem())
        {
            craftSlots.checkChanges = false;
            Recipe<CraftingContainer> recipe = (Recipe<CraftingContainer>)resultSlots.getRecipeUsed();
            while(recipe != null && recipe.matches(craftSlots, player.level()))
            {
                ItemStack recipeOutput = resultSlot.getItem().copy();
                outputCopy = recipeOutput.copy();

                recipeOutput.getItem().onCraftedBy(recipeOutput, player.level(), player);

                if(!player.level().isClientSide)
                {
                    if(wrapper.getSettingsManager().shiftClickToBackpack())
                    {
                        if(!moveItemStackTo(recipeOutput, BACKPACK_INV_START, BACKPACK_INV_END + 1, false))
                        {
                            if(!moveItemStackTo(recipeOutput, PLAYER_INV_START, PLAYER_HOT_END + 1, true))
                            {
                                craftSlots.checkChanges = true;
                                return ItemStack.EMPTY;
                            }
                        }
                    }
                    else
                    {
                        if(!moveItemStackTo(recipeOutput, PLAYER_INV_START, PLAYER_HOT_END + 1, true))
                        {
                            if(!moveItemStackTo(recipeOutput, BACKPACK_INV_START, BACKPACK_INV_END + 1, false))
                            {
                                craftSlots.checkChanges = true;
                                return ItemStack.EMPTY;
                            }
                        }
                    }
                }

                resultSlot.onQuickCraft(recipeOutput, outputCopy);
                resultSlot.setChanged();

                if(!player.level().isClientSide && recipeOutput.getCount() == outputCopy.getCount())
                {
                    craftSlots.checkChanges = true;
                    return ItemStack.EMPTY;
                }

                resultSlots.setRecipeUsed(recipe);
                resultSlot.onTake(player, recipeOutput);
            }
            craftSlots.checkChanges = true;
            slotChangedCraftingGrid(player.level(), player);
            wrapper.setDataChanged(ITravelersBackpackContainer.CRAFTING_INVENTORY_DATA);
        }
        craftSlots.checkChanges = true;
        return resultSlots.getRecipeUsed() == null ? ItemStack.EMPTY : outputCopy;
    }

    public void slotChangedCraftingGrid(Level level, Player player)
    {
        if(!level.isClientSide && craftSlots.checkChanges)
        {
            ItemStack itemstack = ItemStack.EMPTY;

            Recipe<CraftingContainer> oldRecipe = (Recipe<CraftingContainer>)resultSlots.getRecipeUsed();
            Recipe<CraftingContainer> recipe = oldRecipe;

            if(recipe == null || !recipe.matches(craftSlots, level))
            {
                recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftSlots, level).orElse(null);
            }

            if(recipe != null)
            {
                itemstack = recipe.assemble(craftSlots, level.registryAccess());
            }

            if(oldRecipe != recipe)
            {
                TravelersBackpack.NETWORK.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer)player), new ClientboundUpdateRecipePacket(recipe, itemstack));
                resultSlots.setItem(0, itemstack);
                resultSlots.setRecipeUsed(recipe);
            }
            else if(recipe != null)
            {
                if(recipe.isSpecial() || !recipe.getClass().getName().startsWith("net.minecraft") && !ItemStack.matches(itemstack, resultSlots.getItem(0)))
                {
                    TravelersBackpack.NETWORK.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer)player), new ClientboundUpdateRecipePacket(recipe, itemstack));
                    resultSlots.setItem(0, itemstack);
                    resultSlots.setRecipeUsed(recipe);
                }
            }
        }
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player)
    {
        if(wrapper.getSlotManager().isSelectorActive(SlotManager.UNSORTABLE) || wrapper.getSlotManager().isSelectorActive(SlotManager.MEMORY))
        {
            return;
        }
        super.clicked(slotId, dragType, clickType, player);
    }

    @Override
    public void removed(Player player)
    {
        if(wrapper.getScreenID() != Reference.BLOCK_ENTITY_SCREEN_ID)
        {
            this.wrapper.setDataChanged(ITravelersBackpackContainer.ALL_DATA);
        }

        if(wrapper.getScreenID() == Reference.BLOCK_ENTITY_SCREEN_ID)
        {
            if(wrapper.getSlotManager().isSelectorActive(SlotManager.UNSORTABLE) || wrapper.getSlotManager().isSelectorActive(SlotManager.MEMORY)) wrapper.getSlotManager().setChanged();

            this.wrapper.setUsingPlayer(null);
        }

        if(wrapper.getSlotManager().isSelectorActive(SlotManager.UNSORTABLE)) wrapper.getSlotManager().setSelectorActive(SlotManager.UNSORTABLE, false);
        if(wrapper.getSlotManager().isSelectorActive(SlotManager.MEMORY)) wrapper.getSlotManager().setSelectorActive(SlotManager.MEMORY, false);

        clearSlotsAndPlaySound(player, this.wrapper.getFluidSlotsHandler(), 4);
        shiftTools(this.wrapper);

        if(!TravelersBackpackConfig.craftingSavesItems)
        {
            clearSlotsAndPlaySound(player, this.wrapper.getCraftingGridHandler(), 9);
        }
        else
        {
            checkCraftingGridAndPlaySound(player);
        }

        super.removed(player);
    }

    public void clearSlotsAndPlaySound(Player player, ItemStackHandler handler, int size)
    {
        boolean playSound = false;

        for(int i = 0; i < size; i++)
        {
            boolean flag = clearSlot(player, handler, i);
            if(flag) playSound = true;
        }

        if(playSound)
        {
            this.playSound(player);
        }
    }

    public boolean clearSlot(Player player, ItemStackHandler handler, int index)
    {
        if(!handler.getStackInSlot(index).isEmpty())
        {
            if(player == null) return false;

            if(!player.isAlive() || (player instanceof ServerPlayer serverPlayer && serverPlayer.hasDisconnected()))
            {
                ItemStack stack = handler.getStackInSlot(index).copy();
                handler.setStackInSlot(index, ItemStack.EMPTY);

                player.drop(stack, false);
                return false;
            }
            else
            {
                ItemStack stack = handler.getStackInSlot(index);
                handler.setStackInSlot(index, ItemStack.EMPTY);

                player.getInventory().placeItemBackInInventory(stack);
                return true;
            }
        }
        return false;
    }

    public void playSound(Player player)
    {
        player.level().playSound(player, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, (1.0F + (player.level().getRandom().nextFloat() - player.level().getRandom().nextFloat()) * 0.2F) * 0.7F);
    }

    public void shiftTools(BackpackWrapper wrapper)
    {
        boolean foundEmptySlot = false;
        boolean needsShifting = false;

        for(int i = 0; i < wrapper.getToolSlotsHandler().getSlots(); i++)
        {
            if(foundEmptySlot)
            {
                if(!wrapper.getToolSlotsHandler().getStackInSlot(i).isEmpty())
                {
                    needsShifting = true;
                }
            }

            if(wrapper.getToolSlotsHandler().getStackInSlot(i).isEmpty() && !foundEmptySlot)
            {
                foundEmptySlot = true;
            }
        }

        if(needsShifting)
        {
            NonNullList<ItemStack> tools = NonNullList.withSize(wrapper.getToolSlotsHandler().getSlots(), ItemStack.EMPTY);
            int j = 0;

            for(int i = 0; i < wrapper.getToolSlotsHandler().getSlots(); i++)
            {
                if(!wrapper.getToolSlotsHandler().getStackInSlot(i).isEmpty())
                {
                    tools.set(j, wrapper.getToolSlotsHandler().getStackInSlot(i));
                    j++;
                }
            }

            j = 0;

            for(int i = 0; i < wrapper.getToolSlotsHandler().getSlots(); i++)
            {
                if(!tools.isEmpty())
                {
                    wrapper.getToolSlotsHandler().setStackInSlot(i, tools.get(j));
                    j++;
                }
            }
        }
    }

    //Remove forbidden items from crafting grid, if saving enabled
    public void checkCraftingGridAndPlaySound(Player player)
    {
        boolean playSound = false;

        for(int i = 0; i < wrapper.getCraftingGridHandler().getSlots(); i++)
        {
            boolean flag = clearCraftingGridSlot(player, i);
            if(flag) playSound = true;
        }

        if(playSound)
        {
            this.playSound(player);
        }
    }

    public boolean clearCraftingGridSlot(Player player, int index)
    {
        if(!BackpackSlotItemHandler.isItemValid(wrapper.getCraftingGridHandler().getStackInSlot(index)))
        {
            if(player == null) return false;

            if(!player.isAlive() || (player instanceof ServerPlayer serverPlayer && serverPlayer.hasDisconnected()))
            {
                ItemStack stack = wrapper.getCraftingGridHandler().getStackInSlot(index).copy();
                wrapper.getCraftingGridHandler().setStackInSlot(index, ItemStack.EMPTY);

                player.drop(stack, false);
                return false;
            }
            else
            {
                ItemStack stack = wrapper.getCraftingGridHandler().getStackInSlot(index);
                wrapper.getCraftingGridHandler().setStackInSlot(index, ItemStack.EMPTY);

                player.getInventory().placeItemBackInInventory(stack);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player)
    {
        return true;
    }
}