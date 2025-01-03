/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.mcframes

import codechicken.lib.lighting.LightModel
import codechicken.lib.render.CCModel._
import codechicken.lib.render.uv.IconTransformation
import codechicken.lib.render.{CCModel, CCRenderState, TextureUtils, Vertex5}
import codechicken.lib.vec._
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler
import cpw.mods.fml.relauncher.{Side, SideOnly}
import mrtjp.core.block.BlockCore
import mrtjp.core.resource.SoundLib
import mrtjp.core.vec.InvertX
import mrtjp.mcframes.api.{IFrame, IFramePlacement, MCFramesAPI}
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.client.renderer.RenderBlocks
import net.minecraft.client.renderer.texture.IIconRegister
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.{ItemBlock, ItemStack}
import net.minecraft.util.{IIcon, Vec3}
import net.minecraft.world.{IBlockAccess, World}
import net.minecraftforge.common.util.ForgeDirection

import scala.collection.JavaConversions._

class BlockFrame
    extends BlockCore("mcframes.frame", Material.wood)
    with IFrame {
  setResistance(5.0f)
  setHardness(2.0f)
  setStepSound(Block.soundTypeWood)
  setCreativeTab(CreativeTabs.tabTransport)

  override def getItemBlockClass = classOf[ItemBlockFrame]

  override def stickOut(w: World, x: Int, y: Int, z: Int, side: Int) = true
  override def stickIn(w: World, x: Int, y: Int, z: Int, side: Int) = true

  override def isSideSolid(
      w: IBlockAccess,
      x: Int,
      y: Int,
      z: Int,
      side: ForgeDirection
  ) = false

  override def isOpaqueCube = false

  override def renderAsNormalBlock = false

  override def collisionRayTrace(
      world: World,
      x: Int,
      y: Int,
      z: Int,
      start: Vec3,
      end: Vec3
  ) =
    MCFramesAPI.instance.raytraceFrame(x, y, z, 0, start, end)

  @SideOnly(Side.CLIENT)
  override def getSelectedBoundingBoxFromPool(
      w: World,
      x: Int,
      y: Int,
      z: Int
  ) =
    Cuboid6.full.copy.add(new Vector3(x, y, z)).toAABB

  override def getRenderType = RenderFrame.renderID

  override def getIcon(side: Int, meta: Int) = RenderFrame.icon

  @SideOnly(Side.CLIENT)
  override def registerBlockIcons(reg: IIconRegister) {
    RenderFrame.registerIcons(reg)
  }
}

object ItemBlockFrame {
  var placements = Seq[IFramePlacement]()
}

class ItemBlockFrame(b: Block) extends ItemBlock(b) {
  override def getMetadata(meta: Int) = meta

  override def onItemUse(
      item: ItemStack,
      player: EntityPlayer,
      world: World,
      x: Int,
      y: Int,
      z: Int,
      side: Int,
      hitX: Float,
      hitY: Float,
      hitZ: Float
  ): Boolean = {
    if (
      ItemBlockFrame.placements.exists(
        _.onItemUse(
          item,
          player,
          world,
          x,
          y,
          z,
          side,
          new Vector3(hitX, hitY, hitZ)
        )
      )
    ) {
      SoundLib.playBlockPlacement(world, x, y, z, field_150939_a)
      true
    } else super.onItemUse(item, player, world, x, y, z, side, hitX, hitY, hitZ)
  }

  override def func_150936_a(
      world: World,
      x: Int,
      y: Int,
      z: Int,
      side: Int,
      player: EntityPlayer,
      stack: ItemStack
  ) = true
}

object RenderFrame extends ISimpleBlockRenderingHandler {
  var renderID = -1
  var icon: IIcon = _

  private val model = parseModel("frame")
  private val models = new Array[CCModel](64)

  private def parseModel(name: String) = {
    val m = combine(
      parseObjModels(
        this.getClass
          .getResource("/assets/mcframes/obj/" + name + ".obj")
          .openStream(),
        7,
        InvertX
      ).values()
    )

    m.apply(new Scale(1.00075, 1.00075, 1.00075))
    m.apply(new Translation(Vector3.center))
    m
  }

  override def getRenderId = renderID

  override def shouldRender3DInInventory(modelId: Int) = true

  override def renderInventoryBlock(
      b: Block,
      meta: Int,
      id: Int,
      rb: RenderBlocks
  ) {
    renderInvBlock(rb, meta)
  }

  override def renderWorldBlock(
      world: IBlockAccess,
      x: Int,
      y: Int,
      z: Int,
      b: Block,
      id: Int,
      rb: RenderBlocks
  ) = {
    renderWorldBlock(rb, world, x, y, z, world.getBlockMetadata(x, y, z))
    true
  }

  def renderWorldBlock(
      r: RenderBlocks,
      w: IBlockAccess,
      x: Int,
      y: Int,
      z: Int,
      meta: Int
  ) {
    TextureUtils.bindAtlas(0)
    val state = CCRenderState.instance
    state.resetInstance()
    state.lightMatrix.locate(w, x, y, z)
    state.setBrightnessInstance(w, x, y, z)

    if (r.hasOverrideBlockTexture) {
      getOrGenerateModel(0).render(
        new Translation(x, y, z),
        new IconTransformation(r.overrideBlockTexture)
      )
    } else RenderFrame.render(new Vector3(x, y, z), 0)
  }

  def renderInvBlock(r: RenderBlocks, meta: Int) {
    TextureUtils.bindAtlas(0)
    val state = CCRenderState.instance
    state.resetInstance()
    state.setDynamicInstance()
    state.pullLightmapInstance()

    state.startDrawingInstance()
    RenderFrame.render(new Vector3(-0.5, -0.5, -0.5), 0)

    state.renderInstance()
    state.drawInstance()
  }

  def registerIcons(reg: IIconRegister) {
    icon = reg.registerIcon("mcframes:frame")
  }

  def render(pos: Vector3, mask: Int) {
    getOrGenerateModel(mask).render(
      pos.translation,
      new IconTransformation(icon)
    )
  }

  def getOrGenerateModel(mask: Int) = {
    var m = models(mask & 0x3f)
    if (m == null) {
      m = FrameModelGen.generate(model, mask)
      models(mask & 0x3f) = m
    }
    m
  }
}

object FrameModelGen {
  val w = 2.0 / 16.0
  val l = 16.0 / 16.0
  val i = 1.0 / 16.0
  val u = 0.5
  val v = 0.5

  def generate(box: CCModel, mask: Int) = {
    var m = generateSinglePeg
    m = generateQuartRotated(m)
    m = generateEightRotated(m)
    m = generateBackface(m)

    var b = Seq.newBuilder[CCModel]
    b += box
    for (s <- 0 until 6)
      if ((mask & 1 << s) == 0)
        b += generateSided(m.copy, s)

    finishModel(combine(b.result()))
  }

  def generateSinglePeg = {
    val dw = w / 2.0
    val dl = l / 2.0

    val m = quadModel(4)
    m.verts(0) = new Vertex5(dw, i, -dl, u + dw, v - dl)
    m.verts(1) = new Vertex5(dw, i, dl, u + dw, v + dl)
    m.verts(2) = new Vertex5(-dw, i, dl, u - dw, v + dl)
    m.verts(3) = new Vertex5(-dw, i, -dl, u - dw, v - dl)
    m.apply(new Translation(u, 0, v))
  }

  def generateQuartRotated(m: CCModel) = {
    combine(
      Seq(
        m,
        m.copy.apply(
          Rotation
            .quarterRotations(1)
            .at(Vector3.center) `with` new Translation(0, 0.01, 0)
        )
      )
    )
  }

  def generateEightRotated(m: CCModel) = {
    m.apply(new Rotation(math.Pi / 4, 0, 1, 0).at(Vector3.center))
  }

  def generateBackface(m: CCModel) = {
    combine(Seq(m, m.backfacedCopy()))
  }

  def generateSided(m: CCModel, side: Int) = {
    m.apply(Rotation.sideRotations(side).at(Vector3.center.copy))
  }

  def finishModel(m: CCModel) = {
    m.shrinkUVs(0.0005)
    m.computeNormals()
    m.computeLighting(LightModel.standardLightModel)
  }
}
