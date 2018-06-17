# @Dataset data
# @CommandService cs
# @ModuleService ms

from sc.fiji.maskrcnn import ObjectsDetectAndTrack

inputs = {"model": None,
          "modelName": "Microtubule",
          "dataset": data,
          "linkingMaxDistance": 10,
          "gapClosingMaxDistance": 10,
          "maxFrameGap": 5,
          "fillROIManager": True}
module = ms.waitFor(cs.run(ObjectsDetectAndTrack, True, inputs))

table = module.getOutput("resultsTable")
masks = module.getOutput("masks")
