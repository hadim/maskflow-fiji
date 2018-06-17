[![](https://travis-ci.com/hadim/maskflow-fiji.svg?branch=master)](https://travis-ci.com/hadim/maskflow-fiji)

[![](https://travis-ci.org/hadim/maskflow-fiji.svg?branch=master)](https://travis-ci.org/hadim/maskflow-fiji)

# maskflow-fiji

A Fiji plugin for object detection and tracking based on [Mask RCNN](https://arxiv.org/abs/1703.06870).

It allows object detection, segmentation and tracking using a pre-trained model with the associated `maskflow` Python package.

## Usage

- Start [Fiji](https://imagej.net/Fiji/Downloads).
- Click on `Help ▶ Update...`.
- In the new window, click on `Manage update sites`.
- Scroll to find `Maskflow` in the column `Name`. Click on it.
- Click `Close` and then `Apply changes`.
- Restart Fiji.
- Open your image.
- Run the commands at `Plugins ► Maskflow`.

## Screenshots

![Output of the microtubule model.](./screenshot.gif "Output of the microtubule model.")

## Scripting

Here is an example script:

```python
# @Dataset data
# @CommandService cs
# @ModuleService ms

from sc.fiji.maskrcnn import ObjectsDetector

inputs = {"model": None,
          "modelName": "Microtubule",
          "dataset": data,
          "fillROIManager": True}}
module = ms.waitFor(cs.run(ObjectsDetector, True, inputs))

table = module.getOutput("table")
masks = module.getOutput("masks")
```

The plugin also comes with an object tracker based on the centroid of the detected masks:

```python
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

```

There is also a command that combine both detection and tracking:

```python
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

```

## Available Models

| Objects | Version | Description | Size | URL |
| --- | --- | --- | --- | --- |

TODO

## GPU Support

This type of neural networks are much more faster on GPU than CPU. See the following benchmark where the detector has been run on one image. Note that the size does not matter since the images are padded to the size of the network during preprocessing.

*Test done with `Fiji_MaskRCNN-0.4.8`.*

|Model | Version | Device Type | Device | Platform | Duration of Detection (s) |
| --- | --- | --- | --- | --- | --- |
Microtubule | 1.0 | GPU | GeForce GTX 1050 Ti 4 GB | Linux | **2-3** |
Microtubule | 1.0 | CPU | Intel i7-7700HQ 2.80GHz 8 cores 16 GB | Linux | **10-11** |

To enable GPU support ([only Linux at the moment](https://github.com/tensorflow/tensorflow/issues/16660)), you need to manually remove `libtensorflow_jni-1.8.0.jar` from the `jars` folder in your Fiji folder and copy [`libtensorflow_jni_gpu-1.8.0.jar`](http://central.maven.org/maven2/org/tensorflow/libtensorflow_jni_gpu/1.8.0/libtensorflow_jni_gpu-1.8.0.jar) instead.

## Authors

`maskflow-fiji` has been created by [Hadrien Mary](mailto:hadrien.mary@gmail.com).

## License

MIT. See [LICENSE.txt](LICENSE.txt)