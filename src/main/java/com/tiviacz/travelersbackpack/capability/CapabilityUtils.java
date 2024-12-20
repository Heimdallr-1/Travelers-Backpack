package com.tiviacz.travelersbackpack.capability;

import com.tiviacz.travelersbackpack.TravelersBackpack;
import com.tiviacz.travelersbackpack.inventory.BackpackWrapper;
import com.tiviacz.travelersbackpack.items.TravelersBackpackItem;
import io.wispforest.accessories.api.AccessoriesCapability;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.Curios;
import top.theillusivec4.curios.api.CuriosApi;

public class CapabilityUtils
{
    public static LazyOptional<ITravelersBackpack> getCapability(Player player) {
        if(player == null) {
            return LazyOptional.empty();
        }
        return player.getCapability(TravelersBackpackCapability.TRAVELERS_BACKPACK_CAPABILITY, TravelersBackpackCapability.DEFAULT_FACING);
    }

    public static void synchronise(Player player) {
        CapabilityUtils.getCapability(player).ifPresent(ITravelersBackpack::synchronise);
    }

    public static boolean isWearingBackpack(Player player) {
        if(TravelersBackpack.enableCurios()) {
            if(CuriosApi.getCuriosInventory(player).isPresent()) {
                if(CuriosApi.getCuriosInventory(player).resolve().isPresent()) {
                    return CuriosApi.getCuriosInventory(player).resolve().get().isEquipped(t -> t.getItem() instanceof TravelersBackpackItem);
                }
            }
            return false;
        }
        if(getCapability(player).isPresent()) {
            return getCapability(player).resolve().get().hasWearable() && getCapability(player).resolve().get().getWearable().getItem() instanceof TravelersBackpackItem;
        }
        return false;
    }

    public static ItemStack getWearingBackpack(Player player) {
        if(TravelersBackpack.enableCurios()) {
                return isWearingBackpack(player) ? CuriosApi.getCuriosInventory(player).resolve().get().findFirstCurio(t -> t.getItem() instanceof TravelersBackpackItem).get().stack() : ItemStack.EMPTY;
        }
        return isWearingBackpack(player) ? getCapability(player).resolve().get().getWearable() : ItemStack.EMPTY;
    }

    public static void equipBackpack(Player player, ItemStack stack) {
        if(getCapability(player).isPresent() && !isWearingBackpack(player)) {
            getCapability(player).ifPresent(attachment -> attachment.setWearable(stack));
            player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0F, (1.0F + (player.level().random.nextFloat() - player.level().random.nextFloat()) * 0.2F) * 0.7F);

            //Sync
            synchronise(player);
        }
    }

    @Nullable
    public static BackpackWrapper getBackpackWrapper(Player player, ItemStack stack) {
        if(TravelersBackpack.enableCurios()) {
            if(isWearingBackpack(player)) {
                return BackpackWrapper.getBackpackWrapper(player, stack);
            }
            return null;
        }
        if(isWearingBackpack(player)) {
            return CapabilityUtils.getCapability(player).map(ITravelersBackpack::getContainer).orElse(null);
        }
        return null;
    }

    @Nullable
    public static BackpackWrapper getBackpackWrapper(Player player) {
        if(TravelersBackpack.enableCurios()) {
            if(isWearingBackpack(player)) {
                return BackpackWrapper.getBackpackWrapper(player, getWearingBackpack(player));
            }
            return null;
        }
        if(isWearingBackpack(player)) {
            return CapabilityUtils.getCapability(player).map(ITravelersBackpack::getContainer).orElse(null);
        }
        return null;
    }
}