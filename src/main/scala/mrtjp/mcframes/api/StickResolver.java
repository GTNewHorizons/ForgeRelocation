/*
 * Copyright (c) 2015. Created by MrTJP. All rights reserved.
 */
package mrtjp.mcframes.api;

import java.util.Set;

import net.minecraft.world.World;

import mrtjp.relocation.api.BlockPos;

/**
 * Helper class used to resolve frame structures.
 */
public abstract class StickResolver {

    /**
     * Method used to obtain the block coordinates of every block that is part of the same framed structure as the one
     * that was given as an argument.
     *
     * @param world      The world
     * @param x          The x coordinate of any block in the structure.
     * @param y          The y coordinate of any block in the structure.
     * @param z          The z coordinate of any block in the structure.
     * @param exclusions All coordinates to not include in the structure. generally, one of these is the motor block
     *                   that moved the structure.
     * @return A set of all block coordinates that are part of the structure that the input block was in.
     */
    public abstract Set<BlockPos> getStructure(World world, int x, int y, int z, BlockPos... exclusions);
}
