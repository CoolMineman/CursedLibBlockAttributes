/*
 * Copyright (c) 2019 AlexIIL
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package alexiil.mc.lib.attributes;

import net.minecraft.entity.TileEntity;
import net.minecraft.item.ItemInstance;
import net.minecraft.level.Level;
import concern.BlockPos;

import alexiil.mc.lib.attributes.misc.LimitedConsumer;
import alexiil.mc.lib.attributes.misc.Reference;

final class NullAttributeAdder<T>
    implements CustomAttributeAdder<T>, ItemAttributeAdder<T>, BlockEntityAttributeAdder<T, TileEntity> {

    private NullAttributeAdder() {}

    private static final NullAttributeAdder<Object> INSTANCE = new NullAttributeAdder<>();

    @SuppressWarnings("unchecked")
    public static <T> NullAttributeAdder<T> get() {
        // Safe because we don't actually do anything with the type.
        return (NullAttributeAdder<T>) INSTANCE;
    }

    @Override
    public void addAll(Reference<ItemInstance> stack, LimitedConsumer<ItemInstance> excess, ItemAttributeList<T> to) {
        // NO-OP
    }

    @Override
    public void addAll(Level world, BlockPos pos, int meta, AttributeList<T> to) {
        // NO-OP
    }

    @Override
    public void addAll(TileEntity blockEntity, AttributeList<T> to) {
        // NO-OP
    }

    @Override
    public Class<TileEntity> getBlockEntityClass() {
        return TileEntity.class;
    }
}
