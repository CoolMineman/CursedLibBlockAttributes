/*
 * Copyright (c) 2019 AlexIIL
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package alexiil.mc.lib.attributes.misc;

import java.util.function.Consumer;

import net.minecraft.container.slot.Slot;
import net.minecraft.entity.player.Player;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayer;
import net.minecraft.item.ItemInstance;
import net.minecraft.packet.Id103Packet;
import alexiil.mc.lib.attributes.Convertible;

/**
 * Various methods for getting {@link Reference}'s and {@link Consumer}'s from a
 * {@link PlayerEntity}'s various inventories.
 */
public final class PlayerInvUtil {
    private PlayerInvUtil() {
    }

    /**
     * Either inserts the given item into the player's inventory or drops it in
     * front of them. Note that this will always keep a reference to the passed
     * stack (and might modify it!)
     */
    public static void insertItemIntoPlayerInventory(Player player, ItemInstance stack) {
        if (player.inventory.pickupItem(stack) && stack.count == 0) {
            return;
        }
        player.dropItem(stack, /* PreventPlayerQuickPickup = */ false);
    }

    /**
     * Creates a {@link Reference} to the given player's
     * {@link PlayerInventory#getCursorStack() cursor stack}, that updates the
     * client whenever it is changed.
     */
    public static Reference<ItemInstance> referenceGuiCursor(ServerPlayer player) {
        return Reference.callable((player.inventory::getHeldItem), s -> {
            player.inventory.setCursorItem(s);
            Id103Packet updateCursorStackPacket = new Id103Packet();
            updateCursorStackPacket.field_593 = 0; //Slot is in inventory
            updateCursorStackPacket.field_594 = player.inventory.selectedHotbarSlot; // Slot to Update
            updateCursorStackPacket.field_595 = s; //Item to update on client
            player.packetHandler.send(updateCursorStackPacket);
        }, s -> true);
    }

    /** Creates a {@link Reference} to the what the player is currently holding in the given {@link Hand}. */
    public static Reference<ItemInstance> referenceHand(Player player) {
        return Reference.callable(player::getHeldItem, s -> player.inventory.setCursorItem(s), s -> true);
    }

    /** Creates a {@link Reference} to the given {@link Slot}. If the slot is an instance of {@link StackReference}, or
     * is {@link Convertible#getAs(Object, Class)} to one, then that is returned. */
    public static Reference<ItemInstance> referenceSlot(Slot slot) {
        StackReference ref = Convertible.getAs(slot, StackReference.class);
        if (ref != null) {
            return ref;
        }
        return Reference.callable(slot::getItem, slot::setStack, slot::canInsert);
    }

    /** Returns a {@link Consumer} that will call {@link #insertItemIntoPlayerInventory} for every {@link ItemStack}
     * passed to it. */
    public static Consumer<ItemInstance> createPlayerInsertable(Player player) {
        return stack -> insertItemIntoPlayerInventory(player, stack);
    }
}
