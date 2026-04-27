/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.relocation.asm

import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper.{
  INSTANCE => mapper
}
import mrtjp.relocation.handler.RelocationMod
import net.minecraft.launchwrapper.{IClassTransformer, Launch}
import org.objectweb.asm.Opcodes._
import org.objectweb.asm.tree._
import org.objectweb.asm.{ClassReader, ClassWriter}

import scala.collection.JavaConversions._

class Transformer extends IClassTransformer {
  type MethodChecker = (String, MethodNode) => Boolean
  type InsTransformer = MethodNode => Unit

  lazy val deobfEnv =
    (Launch.blackboard get "fml.deobfuscatedEnvironment").asInstanceOf[Boolean]
  lazy val blockClass = mapper.unmap("net/minecraft/block/Block")
  lazy val teClass = mapper.unmap("net/minecraft/tileentity/TileEntity")

  def transformBlockRender(m: MethodNode) {
    val old = m.instructions.toArray.collectFirst { case i: MethodInsnNode =>
      i
    }.get
    val list = new InsnList
    list.add(new VarInsnNode(ILOAD, 2))
    list.add(new VarInsnNode(ILOAD, 3))
    list.add(new VarInsnNode(ILOAD, 4))
    list.add(
      new MethodInsnNode(
        INVOKESTATIC,
        "mrtjp/relocation/ASMHacks",
        "getRenderType",
        "(Lnet/minecraft/block/Block;III)I",
        false
      )
    )
    m.instructions.insert(old, list)
    m.instructions.remove(old)
  }

  def transformTERender(m: MethodNode) {
    val old = m.instructions.toArray.collect { case i: MethodInsnNode =>
      i
    }.last
    m.instructions.insert(old, new InsnNode(POP))
    m.instructions.insert(
      old,
      new MethodInsnNode(
        INVOKESTATIC,
        "mrtjp/relocation/ASMHacks",
        "renderTileEntityAt",
        old.desc,
        false
      )
    )
    m.instructions.remove(old)
  }

  val classData = Map[String, (MethodChecker, MethodChecker, InsTransformer)](
    "net.minecraft.client.renderer.RenderBlocks" -> ((
      (_: String, m: MethodNode) => m.name == "renderBlockByRenderType",
      (n: String, m: MethodNode) =>
        mapper.mapMethodName(n, m.name, m.desc) == "func_147805_b",
      transformBlockRender _
    )),
    "net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher" -> ((
      (_: String, m: MethodNode) => m.name == "renderTileEntity",
      (n: String, m: MethodNode) =>
        mapper.mapMethodName(n, m.name, m.desc) == "func_147544_a",
      transformTERender _
    ))
  )

  var matched = Set[String]()

  override def transform(name: String, tName: String, data: Array[Byte]) = {
    if (classData.keys.contains(tName) && !matched.contains(tName)) {
      RelocationMod.log.info(s"transforming: $tName")
      val (ch1, ch2, tr) = classData(tName)

      val node = new ClassNode
      val reader = new ClassReader(data)
      reader.accept(node, 0)

      for (m @ (_m: MethodNode) <- node.methods)
        if ((deobfEnv && ch1(name, m)) || (!deobfEnv && ch2(name, m))) {
          RelocationMod.log.info(s"$name $tName ${m.name} ${m.desc}")
          tr(m)
        }

      matched += tName

      val writer = new ClassWriter(ClassWriter.COMPUTE_MAXS)
      node.accept(writer)
      writer.toByteArray
    } else data
  }
}
