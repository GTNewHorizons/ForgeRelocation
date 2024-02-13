/*
 * Copyright (c) 2015. Created by MrTJP. All rights reserved.
 */

// spotless:off
//
// You may wonder, "why is this commented out?"
// When FML sorts the modcontainers, it includes APIs. The API's dependency is always its owner but if an API is found
// embedded in a mod which isn't the owner, that mod is set as the API's dependant. In this case, the same jar contains
// both ForgeRelocation and  MCFrames, each with an API. So when FML determins which APIs are embedded where, it sees
// MCFrames|API embedded in ForgeRelocation and ForgeRelocation|API embedded in MCFrames. This results in the following
// circular load order:
//
//       MCFrames --------------> MCFrames|API <----+
//         ^   ^                        |           |
//         |   |                        |           |
//         |   +-------------------+    |           +- MrTJPCore
//         |                       |    |           |
//         |                       |    v           |
//       ForgeRelocation|API <--- ForgeRelocation <-+
//
// spotless:on

// @API(owner = "MCFrames", provides = "MCFrames|API", apiVersion = Tags.VERSION)
package mrtjp.mcframes.api;

// import cpw.mods.fml.common.API;
// import mrtjp.relocation.Tags;
