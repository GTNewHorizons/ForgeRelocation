package mrtjp.relocation.asm

import java.util.{Map => JMap}
import cpw.mods.fml.relauncher.{IFMLCallHook, IFMLLoadingPlugin}

@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions(value =
  Array("mrtjp.relocation.asm.*", "scala")
)
class RelocationPlugin extends IFMLLoadingPlugin with IFMLCallHook {
  override def getASMTransformerClass = Array(
    "mrtjp.relocation.asm.Transformer"
  )
  override def getSetupClass = "mrtjp.relocation.asm.RelocationPlugin"
  override def getModContainerClass = null
  override def getAccessTransformerClass = null
  override def injectData(data: JMap[String, AnyRef]): Unit = {}
  override def call(): Void = null
}
