package mrtjp.relocation.asm

import java.util.{Map => JMap}
import cpw.mods.fml.relauncher.IFMLLoadingPlugin

@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions(value =
  Array("mrtjp.relocation.asm.*", "scala")
)
class RelocationPlugin extends IFMLLoadingPlugin {

  override def getASMTransformerClass = Array(
    "mrtjp.relocation.asm.Transformer"
  )
  override def getSetupClass = null
  override def getModContainerClass = null
  override def getAccessTransformerClass = null
  override def injectData(data: JMap[String, AnyRef]): Unit = {}
}
