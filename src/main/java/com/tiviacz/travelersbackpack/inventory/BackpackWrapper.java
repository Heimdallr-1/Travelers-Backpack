package com.tiviacz.travelersbackpack.inventory;

import com.tiviacz.travelersbackpack.capability.CapabilityUtils;
import com.tiviacz.travelersbackpack.common.BackpackAbilities;
import com.tiviacz.travelersbackpack.config.TravelersBackpackConfig;
import com.tiviacz.travelersbackpack.init.ModItems;
import com.tiviacz.travelersbackpack.inventory.menu.TravelersBackpackItemMenu;
import com.tiviacz.travelersbackpack.inventory.menu.slot.BackpackSlotItemHandler;
import com.tiviacz.travelersbackpack.inventory.menu.slot.ToolSlotItemHandler;
import com.tiviacz.travelersbackpack.inventory.sorter.SlotManager;
import com.tiviacz.travelersbackpack.util.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;

public class BackpackWrapper
{
    public static final BackpackWrapper DUMMY = new BackpackWrapper(ModItems.STANDARD_TRAVELERS_BACKPACK.get().getDefaultInstance(), Reference.BLOCK_ENTITY_SCREEN_ID, null, null);

    private final ItemStackHandler inventory; // = createHandler(Tiers.LEATHER.getStorageSlots(), true);
    private final ItemStackHandler craftingInventory; // = createHandler(9, false);
    private final ItemStackHandler toolSlots; // = createToolsHandler(Tiers.LEATHER.getToolSlots());
    private final ItemStackHandler fluidSlots; // = createTemporaryHandler();
    private final FluidTank leftTank; // = createFluidHandler(Tiers.LEATHER.getTankCapacity());
    private final FluidTank rightTank; // = createFluidHandler(Tiers.LEATHER.getTankCapacity());
    private final SlotManager slotManager; // = new SlotManager(this);
    private final SettingsManager settingsManager; // = new SettingsManager(this);
    //private final Player player;
    private ItemStack stack;
    private Tiers.Tier tier;
    private boolean ability;
    private int lastTime;
    private final byte screenID;
    public LevelAccessor levelAccessor;

    private Player owner;
    public ArrayList<Player> playersUsing = new ArrayList<>();

    public Runnable saveHandler = () -> {
    };
    public Runnable abilityHandler = () -> {
    };
    public BlockPos backpackPos;

    String TIER = "Tier";
    String INVENTORY = "Inventory";
    String TOOLS_INVENTORY = "ToolsInventory";
    String CRAFTING_INVENTORY = "CraftingInventory";
    String LEFT_TANK = "LeftTank";
    String RIGHT_TANK = "RightTank";
    String SLEEPING_BAG = "SleepingBag";
    String COLOR = "Color";
    String SLEEPING_BAG_COLOR = "SleepingBagColor";
    String ABILITY = "Ability";
    String LAST_TIME = "LastTime";
    String CUSTOM_NAME = "CustomName";
    String VISIBILITY = "Visibility";

    public BackpackWrapper(ItemStack stack, byte screenID, @Nullable Player player, @Nullable LevelAccessor level)
    {
        if(player != null) {
            this.playersUsing.add(player);
        }
        if(screenID == Reference.WEARABLE_SCREEN_ID) {
            this.setBackpackOwner(player);
        }

        this.stack = stack;
        this.screenID = screenID;
        this.levelAccessor = level;

        inventory = createHandler(Tiers.LEATHER.getStorageSlots(), true);
        craftingInventory = createHandler(9, false);
        toolSlots = createToolsHandler(Tiers.LEATHER.getToolSlots());
        fluidSlots = createTemporaryHandler();
        leftTank = createFluidHandler(Tiers.LEATHER.getTankCapacity());
        rightTank = createFluidHandler(Tiers.LEATHER.getTankCapacity());
        slotManager = new SlotManager(this);
        settingsManager = new SettingsManager(this);

        this.loadAllData(stack.getOrCreateTag());
    }

    public void setBackpackOwner(Player player) {
        this.owner = player;
    }

    public void setStack(ItemStack stack)
    {
        this.stack = stack;
    }

    public void loadTier(CompoundTag compound)
    {
        if(!compound.contains(TIER))
        {
            compound.putInt(TIER, TravelersBackpackConfig.enableTierUpgrades ? Tiers.LEATHER.getOrdinal() : Tiers.DIAMOND.getOrdinal());
        }
        if(compound.contains(TIER, Tag.TAG_STRING))
        {
            Tiers.Tier tier = Tiers.of(compound.getString(TIER));
            compound.remove(TIER);
            compound.putInt(TIER, tier.getOrdinal());
        }
        this.tier = Tiers.of(compound.getInt(TIER));
    }

    public ItemStackHandler getHandler()
    {
        return this.inventory;
    }

    public ItemStackHandler getToolSlotsHandler()
    {
        return this.toolSlots;
    }

    public ItemStackHandler getCraftingGridHandler()
    {
        return this.craftingInventory;
    }

    public ItemStackHandler getFluidSlotsHandler()
    {
        return this.fluidSlots;
    }

    public IItemHandlerModifiable getCombinedHandler()
    {
        return new CombinedInvWrapper(getHandler(), getToolSlotsHandler(), getFluidSlotsHandler(), getCraftingGridHandler());
    }

    public FluidTank getLeftTank()
    {
        return this.leftTank;
    }

    public FluidTank getRightTank()
    {
        return this.rightTank;
    }

    public void saveAllData(CompoundTag compound)
    {
        this.saveTier(compound);
        this.saveTanks(compound);
        this.saveItems(compound);
        this.saveTime(compound);
        this.saveAbility(compound);
        this.slotManager.saveUnsortableSlots(compound);
        this.slotManager.saveMemorySlots(compound);
        this.settingsManager.saveSettings(compound);
    }

    public void loadAllData(CompoundTag compound)
    {
        this.loadTier(compound);
        this.loadTanks(compound);
        this.loadItems(compound);
        this.loadTime(compound);
        this.loadAbility(compound);
        this.slotManager.loadUnsortableSlots(compound);
        this.slotManager.loadMemorySlots(compound);
        this.settingsManager.loadSettings(compound);
    }

    public void saveTier(CompoundTag compound) {
        compound.putInt(TIER, this.tier.getOrdinal());
    }

    public void saveItems(CompoundTag compound)
    {
        compound.put(INVENTORY, this.inventory.serializeNBT());
        compound.put(TOOLS_INVENTORY, this.toolSlots.serializeNBT());
        compound.put(CRAFTING_INVENTORY, this.craftingInventory.serializeNBT());
    }

    public void loadItems(CompoundTag compound)
    {
        this.inventory.deserializeNBT(compound.getCompound(INVENTORY));
        this.toolSlots.deserializeNBT(compound.getCompound(TOOLS_INVENTORY));
        this.craftingInventory.deserializeNBT(compound.getCompound(CRAFTING_INVENTORY));
    }

    public void saveTanks(CompoundTag compound)
    {
        compound.put(LEFT_TANK, this.leftTank.writeToNBT(new CompoundTag()));
        compound.put(RIGHT_TANK, this.rightTank.writeToNBT(new CompoundTag()));
    }

    public void loadTanks(CompoundTag compound)
    {
        this.leftTank.readFromNBT(compound.getCompound(LEFT_TANK));
        this.rightTank.readFromNBT(compound.getCompound(RIGHT_TANK));
    }

    public void saveColor(CompoundTag compound) {}

    public void loadColor(CompoundTag compound) {}

    public void saveSleepingBagColor(CompoundTag compound) {}

    public void loadSleepingBagColor(CompoundTag compound) {}

    public void saveAbility(CompoundTag compound)
    {
        compound.putBoolean(ABILITY, this.ability);
    }

    public void loadAbility(CompoundTag compound)
    {
        this.ability = !compound.contains(ABILITY) && TravelersBackpackConfig.forceAbilityEnabled || compound.getBoolean(ABILITY);
    }

    public void saveTime(CompoundTag compound)
    {
        compound.putInt(LAST_TIME, this.lastTime);
    }

    public void loadTime(CompoundTag compound)
    {
        this.lastTime = compound.getInt(LAST_TIME);
    }

    public boolean updateTankSlots()
    {
        return false;
        //return InventoryActions.transferContainerTank(this, getLeftTank(), 0, player) || InventoryActions.transferContainerTank(this, getRightTank(), 2, player);
    }

    private void sendPackets()
    {
        if(screenID == Reference.WEARABLE_SCREEN_ID)
        {
            //Stop updating stack if player is changing settings
            if(this.slotManager.isSelectorActive(SlotManager.MEMORY) || this.slotManager.isSelectorActive(SlotManager.UNSORTABLE)) return;

            //CapabilityUtils.synchronise(player);
            //CapabilityUtils.synchroniseToOthers(player);
        }
    }

    public boolean hasColor()
    {
        return this.stack.getOrCreateTag().contains(COLOR);
    }

    public int getColor()
    {
        if(hasColor())
        {
            return this.stack.getOrCreateTag().getInt(COLOR);
        }
        return 0;
    }

    public boolean hasSleepingBagColor()
    {
        return this.stack.getOrCreateTag().contains(SLEEPING_BAG_COLOR);
    }

    public int getSleepingBagColor()
    {
        if(hasSleepingBagColor())
        {
            return this.stack.getOrCreateTag().getInt(SLEEPING_BAG_COLOR);
        }
        return DyeColor.RED.getId();
    }

    public boolean getAbilityValue()
    {
        return TravelersBackpackConfig.enableBackpackAbilities ? (BackpackAbilities.ALLOWED_ABILITIES.contains(getItemStack().getItem()) ? this.ability : false) : false;
    }

    public void setAbility(boolean value)
    {
        this.ability = value;
    }

    public int getLastTime()
    {
        return this.lastTime;
    }

    public void setLastTime(int time)
    {
        this.lastTime = time;
    }

    public int getRows()
    {
        return (int)Math.ceil((double)getHandler().getSlots() / 9);
    }

    public int getYOffset()
    {
        return 18 * Math.max(0, getRows() - 3);
    }

    public boolean hasBlockEntity()
    {
        return false;
    }

    public boolean isSleepingBagDeployed()
    {
        return false;
    }

    public SlotManager getSlotManager()
    {
        return slotManager;
    }

    public SettingsManager getSettingsManager()
    {
        return settingsManager;
    }

    public Tiers.Tier getTier()
    {
        return this.tier;
    }

    public byte getScreenID()
    {
        return this.screenID;
    }

    public ItemStack getItemStack()
    {
        return this.stack;
    }

    public void setUsingPlayer(@Nullable Player player) {}

    public static final byte INVENTORY_DATA = 0;
    public static final byte TOOLS_DATA = 1;
    public static final byte CRAFTING_INVENTORY_DATA = 2;
    public static final byte COMBINED_INVENTORY_DATA = 3;
    public static final byte TANKS_DATA = 4;
    public static final byte COLOR_DATA = 5;
    public static final byte SLEEPING_BAG_COLOR_DATA = 6;
    public static final byte ABILITY_DATA = 7;
    public static final byte LAST_TIME_DATA = 8;
    public static final byte SLOT_DATA = 9;
    public static final byte SETTINGS_DATA = 10;
    public static final byte ALL_DATA = 11;

    public void setDataChanged(byte... dataIds)
    {
        if(levelAccessor.isClientSide()) return;

        for(byte data : dataIds)
        {
            switch(data)
            {
                case INVENTORY_DATA: this.stack.getOrCreateTag().put(INVENTORY, this.inventory.serializeNBT());
                case TOOLS_DATA: this.stack.getOrCreateTag().put(TOOLS_INVENTORY, this.toolSlots.serializeNBT());
                case CRAFTING_INVENTORY_DATA: this.stack.getOrCreateTag().put(CRAFTING_INVENTORY, this.craftingInventory.serializeNBT());
                case COMBINED_INVENTORY_DATA: saveItems(this.stack.getOrCreateTag());
                case TANKS_DATA: saveTanks(this.stack.getOrCreateTag());
                case COLOR_DATA: saveColor(this.stack.getOrCreateTag());
                case SLEEPING_BAG_COLOR_DATA: saveSleepingBagColor(this.stack.getOrCreateTag());
                case ABILITY_DATA: saveAbility(this.stack.getOrCreateTag());
                case LAST_TIME_DATA: saveTime(this.stack.getOrCreateTag());
                case SLOT_DATA: slotManager.saveUnsortableSlots(this.stack.getOrCreateTag());
                                slotManager.saveMemorySlots(this.stack.getOrCreateTag());
                case SETTINGS_DATA: settingsManager.saveSettings(stack.getOrCreateTag());
                case ALL_DATA: saveAllData(this.stack.getOrCreateTag());
            }
        }
        sendPackets();
    }

    public void setDataChanged() {}

    public static void abilityTick(Player player)
    {
        if(player.isAlive() && CapabilityUtils.isWearingBackpack(player) && BackpackAbilities.isOnList(BackpackAbilities.ITEM_ABILITIES_LIST, CapabilityUtils.getWearingBackpack(player)))
        {
            BackpackWrapper container = CapabilityUtils.getBackpackWrapper(player);

            if(!player.level().isClientSide)
            {
                if(container.getLastTime() > 0)
                {
                    container.setLastTime(container.getLastTime() - 1);
                    container.setDataChanged(LAST_TIME_DATA);
                }
            }

            if(container.getAbilityValue())
            {
                BackpackAbilities.ABILITIES.abilityTick(CapabilityUtils.getWearingBackpack(player), player, null);
            }
        }
    }

    @Nullable
    public Player getBackpackOwner() {
        return this.owner;
    }

    public void setBackpackPos(BlockPos pos) {
        this.backpackPos = pos;
    }

    public BlockPos getBackpackPos() {
        return this.backpackPos;
    }

    public boolean isOwner(Player player) {
        if(getBackpackOwner() != null) {
            return getBackpackOwner().getId() == player.getId();
        }
        return true;
    }

    public ArrayList<Player> getPlayersUsing() {
        return this.playersUsing;
    }

    public void addUser(Player player) {
        if(!this.playersUsing.contains(player)) {
            this.playersUsing.add(player);
        }
    }

    @Nullable
    public static BackpackWrapper getBackpackWrapper(Player player, ItemStack backpack) {
        if(CapabilityUtils.isWearingBackpack(player)) {
            if(player.containerMenu instanceof TravelersBackpackItemMenu menu && menu.getWrapper().getScreenID() == Reference.WEARABLE_SCREEN_ID) {
                return menu.getWrapper();
            } else {
                for(Player otherPlayer : player.level().players()) {
                    if(otherPlayer.containerMenu instanceof TravelersBackpackItemMenu menu && menu.getWrapper().isOwner(player) && menu.getWrapper().getScreenID() == Reference.WEARABLE_SCREEN_ID) {
                        return menu.getWrapper();
                    }
                }
                return new BackpackWrapper(backpack, Reference.WEARABLE_SCREEN_ID, player, player.level());
            }
        }
        return null;
    }

    private ItemStackHandler createHandler(int size, boolean isInventory)
    {
        return new ItemStackHandler(size)
        {
            @Override
            protected void onContentsChanged(int slot)
            {
                setDataChanged(COMBINED_INVENTORY_DATA);
            }

            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack)
            {
                return BackpackSlotItemHandler.isItemValid(stack);
            }

            @Override
            public void deserializeNBT(CompoundTag nbt)
            {
                if(isInventory)
                {
                    setSize(nbt.contains("Size", 3) ? nbt.getInt("Size") : BackpackWrapper.this.tier.getStorageSlots());
                    ListTag tagList = nbt.getList("Items", Tag.TAG_COMPOUND);
                    for(int i = 0; i < tagList.size(); i++)
                    {
                        CompoundTag itemTags = tagList.getCompound(i);
                        int slot = itemTags.getInt("Slot");

                        if(slot >= 0 && slot < stacks.size())
                        {
                            stacks.set(slot, ItemStack.of(itemTags));
                        }
                    }
                    onLoad();
                }
                else
                {
                    super.deserializeNBT(nbt);
                }
            }
        };
    }

    private ItemStackHandler createToolsHandler(int size)
    {
        return new ItemStackHandler(size)
        {
            @Override
            protected void onContentsChanged(int slot)
            {
                setDataChanged(TOOLS_DATA);
            }

            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack)
            {
                return ToolSlotItemHandler.isValid(stack);
            }

            @Override
            public void deserializeNBT(CompoundTag nbt)
            {
                setSize(nbt.contains("Size", 3) ? nbt.getInt("Size") : BackpackWrapper.this.tier.getToolSlots());
                ListTag tagList = nbt.getList("Items", Tag.TAG_COMPOUND);
                for(int i = 0; i < tagList.size(); i++)
                {
                    CompoundTag itemTags = tagList.getCompound(i);
                    int slot = itemTags.getInt("Slot");

                    if(slot >= 0 && slot < stacks.size())
                    {
                        stacks.set(slot, ItemStack.of(itemTags));
                    }
                }
                onLoad();
            }
        };
    }

    private FluidTank createFluidHandler(int capacity)
    {
        return new FluidTank(capacity)
        {
            @Override
            protected void onContentsChanged()
            {
                setDataChanged(TANKS_DATA);
            }

            @Override
            public FluidTank readFromNBT(CompoundTag nbt)
            {
                setCapacity(nbt.contains("Capacity", 3) ? nbt.getInt("Capacity") : BackpackWrapper.this.tier.getTankCapacity());
                FluidStack fluid = FluidStack.loadFluidStackFromNBT(nbt);
                setFluid(fluid);
                return this;
            }

            @Override
            public CompoundTag writeToNBT(CompoundTag nbt)
            {
                if(!nbt.contains("Capacity", 3)) nbt.putInt("Capacity", BackpackWrapper.this.tier.getTankCapacity());
                fluid.writeToNBT(nbt);
                return nbt;
            }
        };
    }

    public ItemStackHandler createTemporaryHandler()
    {
        return new ItemStackHandler(4)
        {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack)
            {
                LazyOptional<IFluidHandlerItem> container = FluidUtil.getFluidHandler(stack);

                if(slot == 1 || slot == 3)
                {
                    return false;
                }

                if(stack.getItem() == Items.POTION || stack.getItem() == Items.GLASS_BOTTLE)
                {
                    return true;
                }

                return container.isPresent();
            }
        };
    }
}