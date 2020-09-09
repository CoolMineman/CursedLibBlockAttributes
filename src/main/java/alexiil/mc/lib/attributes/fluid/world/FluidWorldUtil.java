/*
 * Copyright (c) 2019 AlexIIL
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package alexiil.mc.lib.attributes.fluid.world;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.level.Level;
import net.minecraft.tile.FlowingFluid;
import net.minecraft.tile.Fluid;
import net.minecraft.tile.Tile;
import net.minecraft.tile.material.Material;
import concern.BlockPos;
import io.github.minecraftcursedlegacy.api.registry.Registries;
import alexiil.mc.lib.attributes.Simulation;
import alexiil.mc.lib.attributes.fluid.FluidVolumeUtil;
import alexiil.mc.lib.attributes.fluid.amount.FluidAmount;
import alexiil.mc.lib.attributes.fluid.volume.FluidKey;
import alexiil.mc.lib.attributes.fluid.volume.FluidKeys;
import alexiil.mc.lib.attributes.fluid.volume.FluidVolume;

public final class FluidWorldUtil {

    private static final Map<Tile, IFluidVolumeDrainable> customDrainables = new IdentityHashMap<>();

    public static void registerCustomDrainable(Tile block, IFluidVolumeDrainable drainer) {
        customDrainables.put(block, drainer);
    }

    /** Attempts to drain the given block of it's fluid. */
    public static FluidVolume drain(Level world, BlockPos pos, Simulation simulation) {
        Tile block = Registries.TILE.getBySerialisedId(world.getTileId(pos.x, pos.y, pos.z));
        if (block instanceof IFluidVolumeDrainable) {
            return ((IFluidVolumeDrainable) block).tryDrainFluid(world, pos, world.getTileMeta(pos.x, pos.y, pos.z), simulation);
        }
        // else if (block instanceof Waterloggable && state.contains(Properties.WATERLOGGED)) {
        //     if (state.get(Properties.WATERLOGGED)) {
        //         FluidVolume fluidVolume = FluidKeys.WATER.fromWorld(world, pos);
        //         if (simulation == Simulation.ACTION) {
        //             world.setBlockState(pos, state.with(Properties.WATERLOGGED, false), 3);
        //         }
        //         return fluidVolume;
        //     }
        //     return FluidVolumeUtil.EMPTY;
        // } else if (block instanceof FluidBlock && state.contains(Properties.LEVEL_15)) {
        //     if (state.get(Properties.LEVEL_15) == 0) {
        //         FluidKey fluidKey = FluidKeys.get(((IFluidBlockMixin) block).__fluid());
        //         if (fluidKey == null) {
        //             return FluidVolumeUtil.EMPTY;
        //         }
        //         FluidVolume fluidVolume = fluidKey.fromWorld(world, pos);
        //         if (simulation == Simulation.ACTION) {
        //             world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
        //         }
        //         return fluidVolume;
        //     }
        // }
        if (block instanceof Fluid) {
            FluidKey fluidKey = FluidKeys.get(((IFluidBlockMixin) block).__fluid());
            if (fluidKey == null) {
                return FluidVolumeUtil.EMPTY;
            }
            FluidVolume fluidVolume = fluidKey.fromWorld(world, pos);
        }
        IFluidVolumeDrainable drainer = customDrainables.get(block);
        if (drainer != null) {
            return drainer.tryDrainFluid(world, pos, world.getTileMeta(pos.x, pos.y, pos.z), simulation);
        }
        return FluidVolumeUtil.EMPTY;
    }

    /**
     * Attempts to place the given fluid volume into the given block position.
     * @return The leftover amount of fluid after placing, or the original volume if it was unable to be placed.
     */
    public static FluidVolume fill(Level world, BlockPos pos, FluidVolume volume, Simulation simulation) {

        if (volume.getAmount_F().isLessThan(FluidAmount.BUCKET)) {
            return volume; // Need at least a buckets worth
        }

        Fluid fluid = volume.getRawFluid();
        if (fluid == null) {
            return volume; // Can't be placed if it doesn't have an associated vanilla fluid
        }

        // This code assumes that placing a fluid in the world will always consume a bucket's worth
        boolean success = false;

        Tile block = Registries.TILE.getBySerialisedId(world.getTileId(pos.x, pos.y, pos.z));
        if (world.getMaterial(pos.x, pos.y, pos.z) == Material.AIR) {
            // The easiest case, probably
            if (simulation == Simulation.ACTION) {
                // BlockState fluidStillState = fluid.getDefaultState().getBlockState();
                // world.setBlockState(pos, fluidStillState, 3);
                world.setTile(pos.x, pos.y, pos.z, fluid.id);
            }
            success = true;

        // } else if (block instanceof FluidFillable) {
        //     // FluidFillable includes waterloggable blocks, but not cauldrons, etc.
        //     FluidFillable fillable = (FluidFillable) block;
        //     if (simulation == Simulation.SIMULATE) {
        //         success = fillable.canFillWithFluid(world, pos, state, fluid);
        //     } else {
        //         success = fillable.tryFillWithFluid(world, pos, state, fluid.getDefaultState());
        //     }
        } else if (block instanceof FlowingFluid) {
            // FluidState fluidState = world.getFluidState(pos);
            // Top up a non-still fluid block, but this consumes a full bucket regardless of the level
            if (((FlowingFluid)block).material == fluid.material) {
                if (simulation == Simulation.ACTION) {
                    world.setTile(pos.x, pos.y, pos.z, fluid.id);
                }
                success = true;
            }
        }

        if (success) {
            volume = volume.copy();
            volume.split(FluidAmount.BUCKET);
            return volume;
        } else {
            return volume;
        }
    }

    // private static Fluid getStillFluid(Fluid fluid) {
    //     if (fluid instanceof FlowableFluid) {
    //         return ((FlowableFluid) fluid).getStill();
    //     }
    //     return fluid;
    // }

}
