# @Dataset data
# @CommandService cs
# @ModuleService ms

from sc.fiji.maskflow import ObjectDetector
from sc.fiji.maskflow import ObjectTracker

inputs = {"model": None,
          "modelName": "Microtubule",
          "dataset": data,
          "fillROIManager": True}
module = ms.waitFor(cs.run(ObjectDetector, True, inputs))

table = module.getOutput("table")
masks = module.getOutput("masks")

inputs = {"masks": masks,
          "table": table,
          "linkingMaxDistance": 10,
          "gapClosingMaxDistance": 10,
          "maxFrameGap": 5,
          "fillROIManager": True}

module = ms.waitFor(cs.run(ObjectTracker, True, inputs))
table = module.getOutput("resultTable")
