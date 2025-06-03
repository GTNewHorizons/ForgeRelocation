/*
 * Copyright (c) 2014.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.relocation

import codechicken.lib.vec.{BlockCoord, Vector3}
import cpw.mods.fml.relauncher.{Side, SideOnly}
import mrtjp.core.math.MathLib
import net.minecraft.block.Block
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher
import net.minecraft.client.renderer.{
  OpenGlHelper,
  RenderBlocks,
  RenderHelper,
  Tessellator
}
import net.minecraft.world.{EnumSkyBlock, IBlockAccess, World}
import net.minecraftforge.common.util.ForgeDirection
import org.lwjgl.opengl.GL11._

object MovingRenderer {
  var isRendering = false
  var renderHack = true

  private var oldWorld: World = null
  private var frame = 0.0f
  private var renderBlocks: RenderBlocks = null

  private def mc = Minecraft.getMinecraft
  private def world = mc.theWorld

  private def render(x: Int, y: Int, z: Int, rpos: Vector3) {
    val oldOcclusion = mc.gameSettings.ambientOcclusion
    mc.gameSettings.ambientOcclusion = 0

    val block = world.getBlock(x, y, z)
    if (block == null) return

    val engine = TileEntityRendererDispatcher.instance.field_147553_e
    if (engine != null) engine.bindTexture(TextureMap.locationBlocksTexture)
    mc.entityRenderer.enableLightmap(frame)

    val light = world.getLightBrightnessForSkyBlocks(
      x,
      y,
      z,
      block.getLightValue(world, x, y, z)
    )
    val l1 = light % 65536
    val l2 = light / 65536

    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, l1, l2)
    glColor4f(0, 0, 0, 0)
    RenderHelper.disableStandardItemLighting()
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
    glEnable(GL_BLEND)
    glDisable(GL_CULL_FACE)
    glShadeModel(
      if (Minecraft.isAmbientOcclusionEnabled) GL_SMOOTH else GL_FLAT
    )

    for (pass <- 0 to 1) {
      val tess = Tessellator.instance
      tess.startDrawingQuads()
      tess.setTranslation(
        -TileEntityRendererDispatcher.staticPlayerX + MathLib
          .clamp(-1f, 1f, rpos.x.toFloat),
        -TileEntityRendererDispatcher.staticPlayerY + MathLib
          .clamp(-1f, 1f, rpos.y.toFloat),
        -TileEntityRendererDispatcher.staticPlayerZ + MathLib.clamp(
          -1f,
          1f,
          rpos.z.toFloat
        )
      )
      tess.setColorOpaque(1, 1, 1)

      block.getRenderBlockPass
      if (block.canRenderInPass(pass)) {
        renderHack = false
        renderBlocks.renderBlockByRenderType(block, x, y, z)
        renderHack = true
      }

      tess.setTranslation(0, 0, 0)
      tess.draw()
    }
    RenderHelper.enableStandardItemLighting()
    mc.entityRenderer.disableLightmap(frame)
    mc.gameSettings.ambientOcclusion = oldOcclusion
  }

  def onRenderWorldEvent() {
    if (oldWorld != world) {
      oldWorld = world
      renderBlocks = new MovingRenderBlocks(new MovingWorld(world))
      renderBlocks.renderAllFaces = true
    }

    for (s <- MovementManager2.getWorldStructs(world).structs)
      for (b <- s.preMoveBlocks)
        render(b.x, b.y, b.z, renderPos(s, frame))
  }

  def onTick() = {
    if (oldWorld != null && world == null) {
      oldWorld = null
      renderBlocks = null
    }
  }

  def onPreRenderTick(time: Float) {
    isRendering = true
    frame = time
  }

  def onPostRenderTick() {
    isRendering = false
  }

  def renderPos(s: BlockStruct, partial: Float) = {
    new Vector3(BlockCoord.sideOffsets(s.moveDir))
      .multiply(s.progress + s.speed * partial)
  }
}

@SideOnly(Side.CLIENT)
class MovingRenderBlocks(w: IBlockAccess) extends RenderBlocks(w) {
  val eps = 1.0 / 0x10000

  override def renderStandardBlock(block: Block, x: Int, y: Int, z: Int) = {
    if (MovementManager2.isMoving(Minecraft.getMinecraft.theWorld, x, y, z)) {
      if (renderMinX == 0.0d) renderMinX += eps
      if (renderMinY == 0.0d) renderMinY += eps
      if (renderMinZ == 0.0d) renderMinZ += eps
      if (renderMaxX == 1.0d) renderMaxX -= eps
      if (renderMaxY == 1.0d) renderMaxY -= eps
      if (renderMaxZ == 1.0d) renderMaxZ -= eps
    }
    super.renderStandardBlock(block, x, y, z)
  }
}

class MovingWorld(val world: World) extends IBlockAccess {
  override def getBlock(x: Int, y: Int, z: Int) = world.getBlock(x, y, z)

  override def getTileEntity(x: Int, y: Int, z: Int) =
    world.getTileEntity(x, y, z)

  def computeLightValue(x: Int, y: Int, z: Int, tpe: EnumSkyBlock) = {
    (for (s <- 0 until 6; c = new BlockCoord(x, y, z).offset(s))
      yield world.getSavedLightValue(tpe, c.x, c.y, c.z)).max
  }

  @SideOnly(Side.CLIENT)
  override def getLightBrightnessForSkyBlocks(
      x: Int,
      y: Int,
      z: Int,
      light: Int
  ) =
    MovementManager2.isMoving(Minecraft.getMinecraft.theWorld, x, y, z) match {
      case true =>
        val l1 = computeLightValue(x, y, z, EnumSkyBlock.Sky)
        val l2 = computeLightValue(x, y, z, EnumSkyBlock.Block)
        l1 << 20 | Seq(l2, light).max << 4
      case false => world.getLightBrightnessForSkyBlocks(x, y, z, light)
    }

  override def getBlockMetadata(x: Int, y: Int, z: Int) =
    world.getBlockMetadata(x, y, z)

  @SideOnly(Side.CLIENT)
  override def isAirBlock(x: Int, y: Int, z: Int) = world.isAirBlock(x, y, z)

  @SideOnly(Side.CLIENT)
  override def getBiomeGenForCoords(x: Int, z: Int) =
    world.getBiomeGenForCoords(x, z)

  @SideOnly(Side.CLIENT)
  override def getHeight = world.getHeight

  @SideOnly(Side.CLIENT)
  override def extendedLevelsInChunkCache() = world.extendedLevelsInChunkCache()

  override def isBlockProvidingPowerTo(x: Int, y: Int, z: Int, side: Int) =
    world.isBlockProvidingPowerTo(x, y, z, side)

  override def isSideSolid(
      x: Int,
      y: Int,
      z: Int,
      side: ForgeDirection,
      _default: Boolean
  ) =
    world.isSideSolid(x, y, z, side, _default)
}
