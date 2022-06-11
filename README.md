# Labrador
Pedestrian obstacle guidance application with object detection on android <br/>
This repository is a mobile application of the NIA Sidewalk dataset, provided by AI Hub.
The sidewalk dataset is a public dataset that can help blind people who have difficulty avoiding obstacles on sidewalk (https://aihub.or.kr/).
This repository contains python notebook for custom model with reference to TFLite Model Maker (https://www.tensorflow.org/lite/models/modify/model_maker).
It also contains android application with reference to TFLite(https://www.tensorflow.org/lite/android).
<br/>

## Introduction
Custom model in this application is based on Efficientdet Lite model. It is a light version of Efficientdet for running on edge device.

![Efficeintdet](https://1.bp.blogspot.com/-MQO5qKuTT8c/XpdE8_IwpsI/AAAAAAAAFtg/mSjhF2ws5FYxwcHN6h9_l5DqYzQlNYJwwCLcBGAsYHQ/s640/image1.png)

The lite model shows pretty good performance compared to other models on edge device.
Custom model classifies 7 classes(car, person, bollard, pole, potted plant, tree trunk, traffic light) and localizes these maximum 10 objects.
However, detectection and representation are limited at most 4 objects at application source code cause of reducing inference time. 
This application notifies the user of obstacles in the direction of travel through vibration and sound when the obstacles located on specfic distance.

![example]()

This application also provides a function that calls Transportation Vulnerable Support Center for using a vehicle.

## Test Environment
### Application:
- window 10
- Android Studio Bumblebee
- android 9.0
### Model:
- Ubuntu 18.04.3 LTS
- CUDA Version: 11.2
- Tensorflow Version: 2.9.1

## License
### SideGuide Dataset:

Copyright (c) 2021 AI Hub

All rights reserved.

The copyright notice must be included when using the data and also for secondary works utilizing this data. This data is built to develop AI technology such as intelligent products, services and can be used for commercial or non-commercial purposes for research and development in various fields.
<br/>

### Android Source Code and Library:

Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
<br/>
