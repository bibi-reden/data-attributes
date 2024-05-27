package com.bibireden.data_attributes.mixin.item;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(ArmorItem.class)
abstract class ArmorItemMixin extends ItemMixin {

    @Override
    public int getProtection(final ItemStack itemStack) {
        return ((ArmorItem) (Object) this).getProtection();
    }

    @Override
    public float getToughness(final ItemStack itemStack) {
        return ((ArmorItem) (Object) this).getToughness();
    }
}