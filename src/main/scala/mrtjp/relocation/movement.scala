/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.relocation

import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.lib.packet.PacketCustom
import codechicken.lib.vec.BlockCoord
import cpw.mods.fml.relauncher.{Side, SideOnly}
import mrtjp.core.math.MathLib
import mrtjp.core.world.WorldLib
import mrtjp.relocation.api.{IMovementCallback, IMovementDescriptor}
import mrtjp.relocation.handler.{RelocationConfig, RelocationSPH}
import net.minecraft.client.Minecraft
import net.minecraft.init.Blocks
import net.minecraft.world.{ChunkCoordIntPair, World}
import net.minecraftforge.common.DimensionManager
import scala.collection.concurrent.TrieMap
import scala.collection.mutable.{
  HashMap => MHashMap,
  MultiMap => MMultiMap,
  Set => MSet
}

import scala.ref.WeakReference

object MovementManager2 {
  val serverRelocations = MHashMap[Int, WorldStructs]()
  val clientRelocations = TrieMap[Int, WorldStructs]()

  def relocationMap(isClient: Boolean) =
    if (isClient) clientRelocations else serverRelocations

  def getWorldStructs(w: World) =
    relocationMap(w.isRemote)
      .getOrElseUpdate(w.provider.dimensionId, new WorldStructs)

  def getWorld(dim: Int, isClient: Boolean) =
    if (!isClient) DimensionManager.getWorld(dim)
    else getClientWorld(dim)

  @SideOnly(Side.CLIENT)
  private def getClientWorld(dim: Int): World = {
    val w = Minecraft.getMinecraft.theWorld
    if (w.provider.dimensionId == dim) w else null
  }

  def writeDesc(w: World, chunks: Set[ChunkCoordIntPair], out: MCDataOutput) = {
    var send = false
    for (
      s <- getWorldStructs(w).structs if s.getChunks.exists(chunks.contains)
    ) {
      send = true
      out.writeShort(s.id)
      s.writeDesc(out)
    }
    if (send) out.writeShort(Short.MaxValue)
    send
  }

  def readDesc(w: World, in: MCDataInput) {
    var id = in.readUShort()
    while (id != Short.MaxValue) {
      val struct = new BlockStruct
      struct.id = id
      struct.readDesc(in)
      addStructToWorld(w, struct)
      id = in.readUShort()
    }
  }

  def read(w: World, in: MCDataInput, key: Int) = key match {
    case 1 =>
      val struct = new BlockStruct
      struct.id = in.readUShort()
      struct.readDesc(in)
      addStructToWorld(w, struct)
      for (b <- struct.allBlocks) // rerender all moving blocks
        w.func_147479_m(b.x, b.y, b.z)
    case 2 =>
      val id = in.readUShort()
      getWorldStructs(w).structs.find(_.id == id) match {
        case Some(struct) => clientCycleMove(w, struct)
        case None =>
          throw new RuntimeException(
            s"DC: Moving structure with id $id was not found client-side."
          )
      }
    case _ =>
      throw new RuntimeException(
        s"DC: Packet with ID $key was not handled. " +
          s"Skipped ${in.asInstanceOf[PacketCustom].getByteBuf.array().length} bytes."
      )
  }

  def sendStruct(w: World, struct: BlockStruct) {
    val out = RelocationSPH.getStream(w, struct.getChunks, 1)
    out.writeShort(struct.id)
    struct.writeDesc(out)
    RelocationSPH.forceSendData()
  }

  def sendCycle(w: World, struct: BlockStruct) {
    RelocationSPH.getStream(w, struct.getChunks, 2).writeShort(struct.id)
    RelocationSPH.forceSendData()
  }

  def isMoving(w: World, x: Int, y: Int, z: Int) = {
    if (w == null) false
    else getWorldStructs(w).contains(x, y, z)
  }

  def addStructToWorld(w: World, b: BlockStruct) {
    getWorldStructs(w).addStruct(b)
    b.onAdded(w)
  }

  def getEnclosedStructure(w: World, x: Int, y: Int, z: Int) =
    getWorldStructs(w).structs.find(_.contains(x, y, z)).orNull

  def tryStartMove(
      w: World,
      blocks: Set[BlockCoord],
      moveDir: Int,
      speed: Double,
      c: IMovementCallback
  ): Boolean = {
    if (blocks.size > RelocationConfig.moveLimit) return false

    val map = new MHashMap[(Int, Int), MSet[Int]]
      with MMultiMap[(Int, Int), Int]
    for (b <- blocks)
      map.addBinding(MathLib.normal(b, moveDir), MathLib.basis(b, moveDir))

    val shift = if ((moveDir & 1) == 1) 1 else -1
    val rowB = Set.newBuilder[BlockRow]
    for (normal <- map.keys) {
      val line = map(normal).toArray
      val sline =
        if (shift == 1) line.sorted else line.sorted(Ordering[Int].reverse)
      for ((basis, size) <- MathLib.splitLine(sline, shift)) {
        val c = MathLib.rhrAxis(moveDir, normal, basis + shift)
        rowB += new BlockRow(c, moveDir, size)
      }
    }

    val rows = rowB.result()
    if (
      rows.exists(row =>
        !MovingTileRegistry.canRunOverBlock(w, row.pos.x, row.pos.y, row.pos.z)
      )
    ) return false
    for (r <- rows) TileMovingRow.setBlockForRow(w, r)

    val struct = new BlockStruct
    struct.id = BlockStruct.claimID()
    struct.speed = speed
    struct.rows = rows
    struct.callback = WeakReference(c)
    addStructToWorld(w, struct)
    sendStruct(w, struct)

    true
  }

  def onTick(isClient: Boolean) {
    val map = relocationMap(isClient)
    for ((dim, ws) <- map) if (ws.nonEmpty) {
      ws.pushAll()
      val world = getWorld(dim, isClient)
      if (world != null)
        for (bs <- ws.structs)
          for (br <- bs.rows)
            br.pushEntities(world, bs.progress)
    }

    if (!isClient) {
      val fin = map
        .map(pair => (pair._1, pair._2.removeFinished()))
        .filter(_._2.nonEmpty)
      for ((dim, b) <- fin) {
        val w = getWorld(dim, false)
        if (w != null) for (s <- b) {
          cycleMove(w, s)
          sendCycle(w, s)
        }
      }
    }
  }

  def onWorldUnload(w: World) {
    getWorldStructs(w).clear()
  }

  def clientCycleMove(w: World, struct: BlockStruct) {
    getWorldStructs(w).removeStruct(struct)
    struct.rows.foreach(_.pushEntities(w, 1.0))
    cycleMove(w, struct)
  }

  def cycleMove(w: World, struct: BlockStruct) {
    struct.doMove(w)
    struct.postMove(w)
    struct.endMove(w)

    Utils.rescheduleTicks(
      w,
      struct.preMoveBlocks,
      struct.allBlocks,
      struct.moveDir
    )

    val changes = MSet[BlockCoord]()
    for (r <- struct.rows) r.cacheChanges(w, changes)
    for (bc <- changes)
      w.notifyBlockOfNeighborChange(bc.x, bc.y, bc.z, Blocks.air)

    Utils.rerenderBlocks(w, struct.preMoveBlocks)
  }
}

class WorldStructs {
  var structs: Set[BlockStruct] = Set.empty

  def isEmpty = structs.isEmpty

  def nonEmpty = !isEmpty

  def contains(x: Int, y: Int, z: Int) = structs.exists(_.contains(x, y, z))

  def addStruct(b: BlockStruct) { structs += b }

  def pushAll() { structs.foreach(_.push()) }

  def removeFinished() = {
    val finished = structs.filter(_.isFinished)
    structs = structs.filterNot(_.isFinished)
    finished
  }

  def removeStruct(s: BlockStruct) {
    structs = structs.filterNot(_ == s)
  }

  def clear() { structs = Set.empty }

  def getChunks: Set[ChunkCoordIntPair] = {
    structs.flatMap(_.getChunks)
  }
}

object BlockStruct {
  private var maxID = 0
  def claimID() = {
    if (maxID < 32765)
      maxID += 1 // little less than Short.MaxValue (reserved for terminator)
    else maxID = 0
    maxID
  }
}

class MoveDesc(b: WeakReference[BlockStruct]) extends IMovementDescriptor {
  def this(b: BlockStruct) = this(new WeakReference(b))

  override def isMoving = b match {
    case WeakReference(b: BlockStruct) => !b.isFinished
    case _                             => false
  }

  override def getProgress = b match {
    case WeakReference(b: BlockStruct) => b.progress
    case _                             => -1
  }

  override def getSize = b match {
    case WeakReference(b: BlockStruct) => b.allBlocks.size
    case _                             => 0
  }
}

class BlockStruct {
  var id = -1
  var speed = 1 / 16d
  var rows: Set[BlockRow] = Set.empty
  var callback = WeakReference[IMovementCallback](null)

  var progress = 0.0d

  lazy val allBlocks = rows.flatMap(_.allBlocks)
  lazy val preMoveBlocks = rows.flatMap(_.preMoveBlocks)
  lazy val postMoveBlocks = rows.flatMap(_.postMoveBlocks)

  def moveDir = rows.head.moveDir

  def contains(x: Int, y: Int, z: Int) = rows.exists(_.contains(x, y, z))

  def push() { progress = math.min(1.0, progress + speed) }

  def isFinished = progress >= 1.0d

  def getChunks: Set[ChunkCoordIntPair] = {
    val c = Set.newBuilder[ChunkCoordIntPair]
    for (b <- allBlocks)
      c += new ChunkCoordIntPair(b.x >> 4, b.z >> 4)
    c.result()
  }

  def onAdded(w: World) {
    if (!w.isRemote) callback match {
      case WeakReference(c) =>
        c.setDescriptor(new MoveDesc(this))
        c.onMovementStarted()
      case _ =>
    }
  }

  def doMove(w: World) {
    for (r <- rows) r.doMove(w)
  }

  def postMove(w: World) {
    for (r <- rows) r.postMove(w)
  }

  def endMove(w: World) {
    for (r <- rows) r.endMove(w)
    if (!w.isRemote) callback match {
      case WeakReference(c) => c.onMovementFinished()
      case _                =>
    }
  }

  override def equals(obj: scala.Any) = obj match {
    case that: BlockStruct => id == that.id
    case _                 => false
  }

  def writeDesc(out: MCDataOutput) {
    out.writeFloat(progress.toFloat)
    out.writeFloat(speed.toFloat)
    out.writeByte(rows.size)
    for (r <- rows) {
      out.writeLong(WorldLib.packCoords(r.pos))
      out.writeByte(r.moveDir)
      out.writeShort(r.size)
    }
  }

  def readDesc(in: MCDataInput) {
    progress = in.readFloat()
    speed = in.readFloat()
    val rb = Set.newBuilder[BlockRow]
    for (i <- 0 until in.readUByte())
      rb += new BlockRow(
        WorldLib.unpackCoords(in.readLong()),
        in.readUByte(),
        in.readUShort()
      )
    rows = rb.result()
  }
}

class BlockRow(val pos: BlockCoord, val moveDir: Int, val size: Int) {
  val allBlocks = 0 to size map { i => pos.copy.offset(moveDir ^ 1, i) }
  val preMoveBlocks = allBlocks drop 1
  val postMoveBlocks = allBlocks dropRight 1

  def contains(x: Int, y: Int, z: Int) = {
    import MathLib.{basis, normal, shift}

    import math.{max, min}

    if (normal(x, y, z, moveDir) == normal(pos, moveDir)) {
      val b1 = basis(pos, moveDir)
      val b2 = b1 + size * shift(moveDir ^ 1)
      min(b1, b2) to max(b1, b2) contains basis(x, y, z, moveDir)
    } else false
  }

  def pushEntities(w: World, progress: Double) {
    WorldLib.uncheckedGetTileEntity(w, pos.x, pos.y, pos.z) match {
      case te: TileMovingRow => te.pushEntities(this, progress)
      case _                 =>
    }
  }

  def doMove(w: World) {
    if (pos.y < 0 || pos.y >= 256) return

    w.removeTileEntity(pos.x, pos.y, pos.z)
    WorldLib.uncheckedSetBlock(
      w,
      pos.x,
      pos.y,
      pos.z,
      Blocks.air,
      0
    ) // Remove movement block

    for (b <- preMoveBlocks)
      MovingTileRegistry.move(w, b.x, b.y, b.z, moveDir)
  }

  def postMove(w: World) {
    for (b <- postMoveBlocks)
      MovingTileRegistry.postMove(w, b.x, b.y, b.z)
  }

  def endMove(w: World) {}

  def cacheChanges(w: World, changes: MSet[BlockCoord]) {
    for (i <- 0 to size) {
      val c = pos.copy.offset(moveDir ^ 1, i)
      changes += c
      for (s <- 0 until 6; s1 <- 0 until 6 if s1 != (s ^ 1))
        changes += c.copy.offset(s).offset(s1)
    }
  }
}
