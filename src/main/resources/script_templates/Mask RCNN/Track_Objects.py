# @Dataset data
# @CommandService cs
# @ModuleService ms

from sc.fiji.maskrcnn import ObjectsDetector
from sc.fiji.maskrcnn import ObjectsTracker

inputs = {"model": None,
          "modelName": "Microtubule",
          "dataset": data,
          "fillROIManager": True}
module = ms.waitFor(cs.run(ObjectsDetector, True, inputs))

table = module.getOutput("table")
masks = module.getOutput("masks")

inputs = {"masks": masks,
          "table": table,
          "linkingMaxDistance": 10,
          "gapClosingMaxDistance": 10,
          "maxFrameGap": 5,
          "fillROIManager": True}
          
module = ms.waitFor(cs.run(ObjectsTracker, True, inputs))
table = module.getOutput("resultTable")
