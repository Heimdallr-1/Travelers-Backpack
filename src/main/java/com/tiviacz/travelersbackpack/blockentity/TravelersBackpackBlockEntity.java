package com.tiviacz.travelersbackpack.blockentity;

import com.tiviacz.travelersbackpack.blocks.SleepingBagBlock;
import com.tiviacz.travelersbackpack.blocks.TravelersBackpackBlock;
import com.tiviacz.travelersbackpack.common.BackpackAbilities;
import com.tiviacz.travelersbackpack.config.TravelersBackpackConfig;
import com.tiviacz.travelersbackpack.init.ModBlockEntityTypes;
import com.tiviacz.travelersbackpack.init.ModBlocks;
import com.tiviacz.travelersbackpack.init.ModItems;
import com.tiviacz.travelersbackpack.inventory.*;
import com.tiviacz.travelersbackpack.inventory.menu.TravelersBackpackBlockEntityMenu;
import com.tiviacz.travelersbackpack.inventory.menu.slot.BackpackSlotItemHandler;
import com.tiviacz.travelersbackpack.inventory.menu.slot.ToolSlotItemHandler;
import com.tiviacz.travelersbackpack.inventory.sorter.SlotManager;
import com.tiviacz.travelersbackpack.items.TravelersBackpackItem;
import com.tiviacz.travelersbackpack.util.ContainerUtils;
import com.tiviacz.travelersbackpack.util.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;

public class TravelersBackpackBlockEntity extends BlockEntity implements MenuProvider, Nameable
{
    private BackpackWrapper wrapper = BackpackWrapper.DUMMY;
    private boolean isSleepingBagDeployed = false;
    public ArrayList<Integer> infiniteAccessUsers = new ArrayList<>();

    private Player player = null;
    private Component customName = null;

    public String BACKPACK = "Backpack";
    public String SLEEPING_BAG = "SleepingBag";

    public TravelersBackpackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.TRAVELERS_BACKPACK.get(), pos, state);
    }

    @Override
    public void saveAdditional(CompoundTag compound)
    {
        super.saveAdditional(compound);
        writeBackpack(compound);
        compound.putBoolean(SLEEPING_BAG, this.isSleepingBagDeployed);
    }

    @Override
    public void load(CompoundTag compound)
    {
        super.load(compound);
        setBackpackFromNbt(compound);
        this.isSleepingBagDeployed = compound.getBoolean(SLEEPING_BAG);
    }

    public void setBackpack(ItemStack backpack) {
        if(backpack.getItem() instanceof TravelersBackpackItem) {
            if(this.wrapper == BackpackWrapper.DUMMY) {
                this.wrapper = new BackpackWrapper(backpack.copy(), Reference.BLOCK_ENTITY_SCREEN_ID, null, getLevel());
                wrapper.setBackpackPos(getBlockPos());
                wrapper.saveHandler = () -> {
                    this.setChanged();
                    this.notifyBlockUpdate();
                };
                wrapper.abilityHandler = () -> {
                    if(getLevel() != null) {
                        getLevel().updateNeighborsAt(getBlockPos(), getBlockState().getBlock());

                        if(getBlockState().getBlock() == ModBlocks.SPONGE_TRAVELERS_BACKPACK.get()) {
                            ((TravelersBackpackBlock)getBlockState().getBlock()).tryAbsorbWater(getLevel(), getBlockPos());
                        }
                    }
                };
            } else {
                this.wrapper.setStack(backpack.copy());
            }
        }
    }

    private void setBackpackFromNbt(CompoundTag nbt) {
        setBackpack(ItemStack.of(nbt.getCompound(BACKPACK)));
    }

    private void writeBackpack(CompoundTag ret) {
        ItemStack backpackCopy = wrapper.getItemStack().copy();
        if(backpackCopy.getItem() instanceof TravelersBackpackItem) {
            ret.put(BACKPACK, backpackCopy.save(new CompoundTag()));
        }
    }

    public void openBackpack(Player player, MenuProvider containerSupplier, BlockPos pos) {
        if(!player.level().isClientSide) {
            if(this.infiniteAccessUsers.contains(player.getId())) {
                this.infiniteAccessUsers.remove((Object)player.getId());
            }
            NetworkHooks.openScreen((ServerPlayer)player, containerSupplier, buf -> buf.writeBlockPos(pos).writeInt(-1));
        }
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        if(this.wrapper == BackpackWrapper.DUMMY) {
            throw new IllegalStateException("BackpackWrapper is not initialized!");
        }
        return new TravelersBackpackBlockEntityMenu(id, inventory, this.infiniteAccessUsers.contains(player.getId()) ? player.getId() : -1, this.wrapper);
    }

    public BackpackWrapper getWrapper() {
        return this.wrapper;
    }

    private void notifyBlockUpdate()
    {
        BlockState blockstate = getLevel().getBlockState(getBlockPos());
        getLevel().setBlocksDirty(getBlockPos(), blockstate, blockstate);
        getLevel().sendBlockUpdated(getBlockPos(), blockstate, blockstate, 3);
    }

    public void setSleepingBagDeployed(boolean isSleepingBagDeployed)
    {
        this.isSleepingBagDeployed = isSleepingBagDeployed;
    }

    public boolean deploySleepingBag(Level level, BlockPos pos)
    {
        Direction direction = this.getBlockDirection();
        this.isThereSleepingBag(direction);

        if(!this.isSleepingBagDeployed)
        {
            BlockPos sleepingBagPos1 = pos.relative(direction);
            BlockPos sleepingBagPos2 = sleepingBagPos1.relative(direction);

            if(level.getBlockState(sleepingBagPos2).isAir() && level.getBlockState(sleepingBagPos1).isAir())
            {
                if(level.getBlockState(sleepingBagPos1.below()).isCollisionShapeFullBlock(level, sleepingBagPos1.below()) && level.getBlockState(sleepingBagPos2.below()).isCollisionShapeFullBlock(level, sleepingBagPos2.below()))
                {
                    level.playSound(null, sleepingBagPos2, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5F, 1.0F);

                    if(!level.isClientSide)
                    {
                        BlockState sleepingBagState = getProperSleepingBag();
                        level.setBlock(sleepingBagPos1, sleepingBagState.setValue(SleepingBagBlock.FACING, direction).setValue(SleepingBagBlock.PART, BedPart.FOOT).setValue(SleepingBagBlock.CAN_DROP, false), 3);
                        level.setBlock(sleepingBagPos2, sleepingBagState.setValue(SleepingBagBlock.FACING, direction).setValue(SleepingBagBlock.PART, BedPart.HEAD).setValue(SleepingBagBlock.CAN_DROP, false), 3);

                        level.updateNeighborsAt(pos, sleepingBagState.getBlock());
                        level.updateNeighborsAt(sleepingBagPos2, sleepingBagState.getBlock());
                    }

                    this.isSleepingBagDeployed = true;
                    return true;
                }
            }
        }
        return false;
    }

    public boolean removeSleepingBag(Level level, Direction direction)
    {
        this.isThereSleepingBag(direction);

        if(getWrapper().isSleepingBagDeployed())
        {
            BlockPos sleepingBagPos1 = getBlockPos().relative(direction);
            BlockPos sleepingBagPos2 = sleepingBagPos1.relative(direction);

            if(level.getBlockState(sleepingBagPos1).getBlock() instanceof SleepingBagBlock && level.getBlockState(sleepingBagPos2).getBlock() instanceof SleepingBagBlock)
            {
                level.playSound(null, sleepingBagPos2, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5F, 1.0F);
                level.setBlock(sleepingBagPos2, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(sleepingBagPos1, Blocks.AIR.defaultBlockState(), 3);
                this.isSleepingBagDeployed = false;
                return true;
            }
        }
        else
        {
            this.isSleepingBagDeployed = false;
            return true;
        }
        return false;
    }

    public boolean isThereSleepingBag(Direction direction)
    {
        if(level.getBlockState(getBlockPos().relative(direction)).getBlock() instanceof SleepingBagBlock && level.getBlockState(getBlockPos().relative(direction).relative(direction)).getBlock() instanceof SleepingBagBlock)
        {
            return true;
        }
        else
        {
            this.isSleepingBagDeployed = false;
            return false;
        }
    }

    public BlockState getProperSleepingBag()
    {
        return switch(getWrapper().getSleepingBagColor())
        {
                    case 0 -> ModBlocks.WHITE_SLEEPING_BAG.get().defaultBlockState();
            case 1 -> ModBlocks.ORANGE_SLEEPING_BAG.get().defaultBlockState();
            case 2 -> ModBlocks.MAGENTA_SLEEPING_BAG.get().defaultBlockState();
            case 3 -> ModBlocks.LIGHT_BLUE_SLEEPING_BAG.get().defaultBlockState();
            case 4 -> ModBlocks.YELLOW_SLEEPING_BAG.get().defaultBlockState();
            case 5 -> ModBlocks.LIME_SLEEPING_BAG.get().defaultBlockState();
            case 6 -> ModBlocks.PINK_SLEEPING_BAG.get().defaultBlockState();
            case 7 -> ModBlocks.GRAY_SLEEPING_BAG.get().defaultBlockState();
            case 8 -> ModBlocks.LIGHT_GRAY_SLEEPING_BAG.get().defaultBlockState();
            case 9 -> ModBlocks.CYAN_SLEEPING_BAG.get().defaultBlockState();
            case 10 -> ModBlocks.PURPLE_SLEEPING_BAG.get().defaultBlockState();
            case 11 -> ModBlocks.BLUE_SLEEPING_BAG.get().defaultBlockState();
            case 12 -> ModBlocks.BROWN_SLEEPING_BAG.get().defaultBlockState();
            case 13 -> ModBlocks.GREEN_SLEEPING_BAG.get().defaultBlockState();
            case 14 -> ModBlocks.RED_SLEEPING_BAG.get().defaultBlockState();
            case 15 -> ModBlocks.BLACK_SLEEPING_BAG.get().defaultBlockState();
            default -> ModBlocks.RED_SLEEPING_BAG.get().defaultBlockState();
        };
    }

    public boolean isUsableByPlayer(Player player)
    {
        if(this.level.getBlockEntity(getBlockPos()) != this)
        {
            return false;
        }
        else
        {
            return player.distanceToSqr((double)getBlockPos().getX() + 0.5D, (double)getBlockPos().getY() + 0.5D, (double)getBlockPos().getZ() + 0.5D) <= 64.0D;
        }
    }

    public Direction getBlockDirection() {
        if (level == null || !(level.getBlockState(getBlockPos()).getBlock() instanceof TravelersBackpackBlock) || !level.getBlockState(getBlockPos()).hasProperty(TravelersBackpackBlock.FACING)) return Direction.NORTH;
        return level.getBlockState(getBlockPos()).getValue(TravelersBackpackBlock.FACING);
    }

    public boolean hasData()
    {
        boolean isDefaultTier = getWrapper().getTier() == Tiers.LEATHER;
        boolean isInvEmpty = ContainerUtils.isEmpty(getWrapper().getHandler());
        boolean isToolsEmpty = ContainerUtils.isEmpty(getWrapper().getToolSlotsHandler());
        boolean isCraftingGridEmpty = ContainerUtils.isEmpty(getWrapper().getCraftingGridHandler());
        boolean leftTankEmpty = getWrapper().getLeftTank().isEmpty();
        boolean rightTankEmpty = getWrapper().getRightTank().isEmpty();
        boolean hasColor = getWrapper().hasColor();
        boolean hasSleepingBagColor = getWrapper().hasSleepingBagColor();
        boolean hasTime = getWrapper().getLastTime() != 0;
        boolean hasUnsortableSlots = !getWrapper().getSlotManager().getUnsortableSlots().isEmpty();
        boolean hasMemorySlots = !getWrapper().getSlotManager().getMemorySlots().isEmpty();
        boolean hasChangedSettings = !getWrapper().getSettingsManager().isDefault();
        boolean hasCustomName = hasCustomName();
        return !isDefaultTier || !isInvEmpty || !isToolsEmpty || !isCraftingGridEmpty || !leftTankEmpty || !rightTankEmpty || hasColor || hasSleepingBagColor || hasTime || hasUnsortableSlots || hasMemorySlots || hasChangedSettings || hasCustomName;
    }

    public ItemStack transferToItemStack(ItemStack stack)
    {
        CompoundTag compound = new CompoundTag();
        getWrapper().saveTier(compound);
        getWrapper().saveTanks(compound);
        getWrapper().saveItems(compound);
        if(getWrapper().hasColor()) getWrapper().saveColor(compound);
        if(getWrapper().hasSleepingBagColor()) getWrapper().saveSleepingBagColor(compound);
        getWrapper().saveAbility(compound);
        getWrapper().saveTime(compound);
        getWrapper().getSlotManager().saveUnsortableSlots(compound);
        getWrapper().getSlotManager().saveMemorySlots(compound);
        getWrapper().getSettingsManager().saveSettings(compound);
        stack.setTag(compound);
        if(hasCustomName()) stack.setHoverName(getCustomName());
        return stack;
    }

    @Override
    public Component getName()
    {
        return this.customName != null ? this.customName : this.getDefaultName();
    }

    @Nullable
    @Override
    public Component getCustomName()
    {
        return this.customName;
    }

    @Override
    public Component getDisplayName()
    {
        return this.getName();
    }

    public Component getDefaultName()
    {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    public void setCustomName(Component customName)
    {
        this.customName = customName;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt)
    {
        super.onDataPacket(net, pkt);
        this.handleUpdateTag(pkt.getTag());
    }

    @Override
    public CompoundTag getUpdateTag()
    {
        return this.saveWithoutMetadata();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TravelersBackpackBlockEntity blockEntity)
    {
      /*  if(blockEntity.getWrapper().getAbilityValue() && BackpackAbilities.isOnList(BackpackAbilities.BLOCK_ABILITIES_LIST, blockEntity.getItemStack()))
        {
            if(blockEntity.getLastTime() > 0)
            {
                blockEntity.setLastTime(blockEntity.getLastTime() - 1);
                blockEntity.setDataChanged();
            }

            BackpackAbilities.ABILITIES.abilityTick(null, null, blockEntity);
        } */
    }

    private final LazyOptional<IItemHandlerModifiable> inventoryCapability = LazyOptional.of(() -> getWrapper().getHandler()); //new StorageAccessWrapper(getWrapper(), getWrapper().getStorage()));
    private final LazyOptional<IFluidHandler> leftFluidTankCapability = LazyOptional.of(() -> getWrapper().getLeftTank());
    private final LazyOptional<IFluidHandler> rightFluidTankCapability = LazyOptional.of(() -> getWrapper().getRightTank());


    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull final Capability<T> cap, @Nullable final Direction side)
    {
        Direction direction = getBlockDirection();

        if(cap == ForgeCapabilities.ITEM_HANDLER)
        {
            if(getWrapper() != BackpackWrapper.DUMMY) {
                return inventoryCapability.cast();
            }
        }

        if(cap == ForgeCapabilities.FLUID_HANDLER)
        {
            if(getWrapper() == BackpackWrapper.DUMMY) {
                return super.getCapability(cap, side);
            }

            if(side == null)
            {
                return leftFluidTankCapability.cast();
            }
            if(direction == Direction.NORTH)
            {
                switch(side)
                {
                    case WEST: return rightFluidTankCapability.cast();
                    case EAST: return leftFluidTankCapability.cast();
                }
            }
            if(direction == Direction.SOUTH)
            {
                switch(side)
                {
                    case EAST: return rightFluidTankCapability.cast();
                    case WEST: return leftFluidTankCapability.cast();
                }
            }

            if(direction == Direction.EAST)
            {
                switch(side)
                {
                    case NORTH: return rightFluidTankCapability.cast();
                    case SOUTH: return leftFluidTankCapability.cast();
                }
            }

            if(direction == Direction.WEST)
            {
                switch(side)
                {
                    case SOUTH: return rightFluidTankCapability.cast();
                    case NORTH: return leftFluidTankCapability.cast();
                }
            }
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps()
    {
        super.invalidateCaps();
        inventoryCapability.invalidate();
        //craftingInventoryCapability.invalidate();
        leftFluidTankCapability.invalidate();
        rightFluidTankCapability.invalidate();
    }
}