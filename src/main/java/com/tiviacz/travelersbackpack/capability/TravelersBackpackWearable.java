package com.tiviacz.travelersbackpack.capability;

import com.tiviacz.travelersbackpack.TravelersBackpack;
import com.tiviacz.travelersbackpack.inventory.BackpackWrapper;
import com.tiviacz.travelersbackpack.items.TravelersBackpackItem;
import com.tiviacz.travelersbackpack.network.ClientboundSyncCapabilityPacket;
import com.tiviacz.travelersbackpack.util.Reference;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.PacketDistributor;

public class TravelersBackpackWearable implements ITravelersBackpack
{
    private ItemStack wearable = new ItemStack(Items.AIR, 0);
    private final Player player;
    private BackpackWrapper backpackWrapper;

    public TravelersBackpackWearable(final Player player)
    {
        this.player = player;
        //this.backpackWrapper = new BackpackWrapper(this.wearable, player, Reference.WEARABLE_SCREEN_ID);
    }

    @Override
    public boolean hasWearable()
    {
        return this.wearable.getItem() instanceof TravelersBackpackItem;
    }

    @Override
    public ItemStack getWearable()
    {
        return this.wearable;
    }

    @Override
    public void setWearable(ItemStack stack)
    {
        this.removeWearable();
        if(!(stack.getItem() instanceof TravelersBackpackItem)) return;

        this.wearable = stack;
        this.backpackWrapper = new BackpackWrapper(this.wearable, Reference.WEARABLE_SCREEN_ID, this.player, this.player.level());
        this.backpackWrapper.setBackpackOwner(this.player);

        //Update client
        synchronise();

        //this.wearable = stack;
    }

    public void updateBackpack(ItemStack stack) {
        if(this.backpackWrapper != null) {
            this.wearable = stack;
            this.backpackWrapper.setStack(this.wearable);
        } else {
            setWearable(stack);
        }
    }

    @Override
    public void removeWearable()
    {
        this.wearable = new ItemStack(Items.AIR, 0);
        if(this.backpackWrapper != null) {
            this.backpackWrapper = null;
        }

        //Update client to remove old backpack wrapper
        if(this.player.level() != null && !this.player.level().isClientSide) {
            TravelersBackpack.NETWORK.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> this.player), new ClientboundSyncCapabilityPacket(this.player.getId(), true, this.wearable.getOrCreateTag()));
        }
    }

    @Override
    public BackpackWrapper getContainer()
    {
        return this.backpackWrapper;
    }

    @Override
    public void setContents(ItemStack stack)
    {
    }

    @Override
    public void synchronise()
    {
        if(player != null && !player.level().isClientSide)
        {
            ServerPlayer serverPlayer = (ServerPlayer)player;
            CapabilityUtils.getCapability(serverPlayer).ifPresent(cap -> TravelersBackpack.NETWORK.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new ClientboundSyncCapabilityPacket(serverPlayer.getId(), true, this.wearable.save(new CompoundTag()))));
        }
    }

    @Override
    public void synchroniseToOthers(Player player)
    {
        if(player != null && !player.level().isClientSide)
        {
            ServerPlayer serverPlayer = (ServerPlayer)player;
            CapabilityUtils.getCapability(serverPlayer).ifPresent(cap -> TravelersBackpack.NETWORK.send(PacketDistributor.TRACKING_ENTITY.with(() -> serverPlayer), new ClientboundSyncCapabilityPacket(serverPlayer.getId(), true, this.wearable.save(new CompoundTag()))));
        }
    }

    @Override
    public CompoundTag saveTag()
    {
        CompoundTag compound = new CompoundTag();

        if(hasWearable())
        {
            ItemStack wearable = getWearable();
            wearable.save(compound);
        }
        if(!hasWearable())
        {
            ItemStack wearable = new ItemStack(Items.AIR, 0);
            wearable.save(compound);
        }
        return compound;
    }

    @Override
    public void loadTag(CompoundTag compoundTag)
    {
        ItemStack wearable = ItemStack.of(compoundTag);
        setWearable(wearable);
        setContents(wearable);
    }
}