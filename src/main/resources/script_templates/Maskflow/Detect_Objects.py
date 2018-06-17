# @Dataset data
# @CommandService cs
# @ModuleService ms

from sc.fiji.maskflow import ObjectDetector

inputs = {"model": None,
          "modelName": "Microtubule",
          "dataset": data,
          "fillROIManager": True}
module = ms.waitFor(cs.run(ObjectDetector, True, inputs))

table = module.getOutput("table")
masks = module.getOutput("masks")
