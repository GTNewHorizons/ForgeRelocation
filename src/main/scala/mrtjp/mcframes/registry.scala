/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.mcframes

import codechicken.lib.vec.BlockCoord
import mrtjp.core.world.WorldLib._
import mrtjp.mcframes.api.{IFrame, IFrameInteraction}
import net.minecraft.block.Block
import net.minecraft.world.World

object StickRegistry {
  val rKeyVal = raw"([\w:]+)\s*->\s*(.+)".r
  val rName = raw"(.+)".r
  val rNameMetaM = raw"(.+)m(\d+)".r
  val rMod = raw"mod:(\w+)".r

  var latchMap = Map[(Block, Int), Set[(Block, Int)]]().withDefaultValue(Set())
  var interactionList = Seq[IFrameInteraction]()

  def parseKV(kv: Seq[String]) = kv.map {
    case rKeyVal(k, v) => (k, v);
    case s             => throw new MatchError(s"Illegal [k -> v] pair: $s")
  }
  def parseBlockMeta(b: String) = b match {
    case rNameMetaM(name, meta) =>
      Block.getBlockFromName(fixName(name)) -> meta.toInt
    case rName(name) => Block.getBlockFromName(fixName(name)) -> -1
    case _           => throw new MatchError(s"Illegal set part: $b")
  }
  def fixName(name: String) = name.indexOf(':') match {
    case -1 => "minecraft:" + name
    case i  => name
  }

  def parseAndAddLatchSets(kv: Seq[String]): Array[String] = {
    parseKV(kv).foreach(b =>
      addLatchSet(parseBlockMeta(b._1), parseBlockMeta(b._2))
    )
    latchMap
      .map { kv =>
        val (b, i) = kv._1
        val e1 = Block.blockRegistry.getNameForObject(b) + (if (i != -1) s"m$i"
                                                            else "")
        kv._2.map { k =>
          val (b2, i2) = k
          val e2 =
            Block.blockRegistry.getNameForObject(b2) + (if (i2 != -1) s"m$i2"
                                                        else "")
          e1 + " -> " + e2
        }
      }
      .flatten
      .toArray
  }

  def addLatchSet(b1: (Block, Int), b2: (Block, Int)) {
    latchMap += b1 -> (latchMap(b1) + b2)
  }

  def resolveStick(w: World, pos: BlockCoord, side: Int): Boolean = {
    val p2 = pos.copy.offset(side)
    val target = getBlock(w, p2)
    if (
      target != null &&
      target.getBlockHardness(w, p2.x, p2.y, p2.z) < 0
    ) return false

    def getFrame(pos: BlockCoord): IFrame = {
      val b = getBlock(w, pos)
      if (b.isInstanceOf[IFrame]) return b.asInstanceOf[IFrame]
      val te = getTileEntity(w, pos, classOf[IFrame])
      if (te != null) return te
      interactionList.find(_.canInteract(w, pos.x, pos.y, pos.z)).orNull
    }
    val f1 = getFrame(pos)
    if (f1 != null && f1.stickOut(w, pos.x, pos.y, pos.z, side)) {
      val f2 = getFrame(p2)
      return f2 == null || f2.stickIn(w, p2.x, p2.y, p2.z, side ^ 1)
    }

    if (latchSet(w, pos.x, pos.y, pos.z, side)) return true

    false
  }

  def latchSet(w: World, x: Int, y: Int, z: Int, side: Int) = {
    val pos = new BlockCoord(x, y, z).offset(side)
    val b1 = getBlockMetaPair(w, x, y, z)
    val b2 = getBlockMetaPair(w, pos.x, pos.y, pos.z)

    val set = latchMap.getOrElse(b1, latchMap((b1._1, -1)))
    set.contains(b2) || set.contains((b2._1, -1))
  }
}
