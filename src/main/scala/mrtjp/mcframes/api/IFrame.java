/*
 * Copyright (c) 2014. Created by MrTJP. All rights reserved.
 */
package mrtjp.mcframes.api;

import net.minecraft.world.World;

import mrtjp.relocation.api.Relocator;

/**
 * Interface that can be implemented on Tile Entities or Blocks that wish to act as frames, which are the blocks that
 * stick together and form a moving structure when moved through the {@link Relocator}. No other action besides
 * implementation of this interface is needed for the block to function.
 */
public interface IFrame {

    /**
     * Used to check if this frame block is allowed to grab a block on the given side.
     *
     * Convention requires that this method yields the same result on both client and server.
     *
     * @param w    The world.
     * @param x    The x coordinate.
     * @param y    The y coordinate.
     * @param z    The z coordinate.
     * @param side The side of this block, as a ForgeDirection
     * @return True if the side can grab another block.
     */
    boolean stickOut(World w, int x, int y, int z, int side);

    /**
     * Used to check if this frame block can be grabbed on the given side by another frame.
     *
     * Convention requires that this method yields the same result on both client and server.
     *
     * @param w    The world.
     * @param x    The x coordinate.
     * @param y    The y coordinate.
     * @param z    The z coordinate.
     * @param side The side of this block, as a ForgeDirection
     * @return True if the side can be grabbed by a frame block.
     */
    boolean stickIn(World w, int x, int y, int z, int side);
}
