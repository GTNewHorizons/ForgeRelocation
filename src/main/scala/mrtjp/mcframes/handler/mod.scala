/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.mcframes.handler

import cpw.mods.fml.common.Mod
import cpw.mods.fml.common.event.{
  FMLInitializationEvent,
  FMLPostInitializationEvent,
  FMLPreInitializationEvent
}
import mrtjp.core.data.{ModConfig, SpecialConfigGui, TModGuiFactory}
import mrtjp.mcframes.api.MCFramesAPI
import mrtjp.mcframes.{BlockFrame, BlockMotor, StickRegistry}
import mrtjp.relocation.Tags
import net.minecraft.client.gui.GuiScreen

@Mod(
  modid = MCFramesMod.modID,
  useMetadata = true,
  modLanguage = "scala",
  guiFactory = "mrtjp.mcframes.handler.GuiConfigFactory",
  acceptedMinecraftVersions = "[1.7.10]",
  dependencies = "required-after:MrTJPCoreMod",
  name = MCFramesMod.modName,
  version = MCFramesMod.version
)
object MCFramesMod {
  MCFramesAPI.instance = MCFramesAPI_Impl

  final val modID = "MCFrames"
  final val modName = "MCFrames"
  final val version = Tags.VERSION

  var blockFrame: BlockFrame = _
  var blockMotor: BlockMotor = _

  @Mod.EventHandler
  def preInit(event: FMLPreInitializationEvent) {
    MCFramesProxy.preinit()
  }

  @Mod.EventHandler
  def init(event: FMLInitializationEvent) {
    MCFramesConfig.loadConfig()
    MCFramesProxy.init()
  }

  @Mod.EventHandler
  def postInit(event: FMLPostInitializationEvent) {
    MCFramesProxy.postinit()
  }
}

class MCFramesConfigGui(parent: GuiScreen)
    extends SpecialConfigGui(parent, "MCFrames", MCFramesConfig.config)
class GuiConfigFactory extends TModGuiFactory {
  override def mainConfigGuiClass() = classOf[MCFramesConfigGui]
}

object MCFramesConfig extends ModConfig("MCFrames") {
  var setMap = Array(
    "minecraft:bed -> minecraft:bed",
    "minecraft:wooden_door -> minecraft:wooden_door",
    "minecraft:iron_door -> minecraft:iron_door"
  )

  override protected def initValues() {
    val sets = new BaseCategory("Latched Sets", buildLatchSetsDesc)
    setMap = sets.put("latch registry", setMap)
    setMap = sets.put(
      "latch registry",
      StickRegistry.parseAndAddLatchSets(setMap),
      true
    )
  }

  def buildLatchSetsDesc: String = {
    var s = "Used to define which pairs of blocks will be stuck together. \n"
    s += "Latched sets will always move in pairs, even if only one of them are actually connected to a block. \n"
    s += "'block1 -> block2' means that if block1 is moved, any block2 connected to it will also move. \n"
    s += "However, moving block2 does not move block1. To do that, you must also register block2 -> block1. \n"
    s += "Sets are defined using the syntax of key -> value. \n"
    s += "Possible keys and values:\n"
    s += "    '<modID>:<blockname>' - to assign block from a mod for every meta. \n"
    s += "    '<modID>:<blockname>m<meta>' - to assign block from mod for specific meta. \n"
    s
  }
}
