=======
Chromecast - Cordova
=================

Plugin chromecast for Cordova Android Application.

you can use the chromecast javascript API in a webview in Phonegap application like you did it with a desktop chrome application.
 
=========

##Installation

* prepare libs and tools with this [tutorial](https://developers.google.com/cast/docs/android_sender)
* copy adt_path/sdk/extras/android/support/v7/mediarouter, adt_path/sdk/extras/android/support/v7/appcompat and adt_path/sdk/extras/google/google_play_services/libproject/google-play-services_lib to a custom folder.
* go to each lib folder via terminal, and update the project:
```   
android update lib-project -p .
```
* **mediarouter** depends on **appcompat**, so in **mediarouter** folder , edit **project.properties**, add appcompat as dependency:
```
android.library.reference.1=path/to/appcompat
```
* after added platform android, edit your_project/platforms/android/project.properties. content should like this:
```
android.library.reference.1=CordovaLib
android.library.reference.2=path/to/appcompat
android.library.reference.3=path/to/mediarouter
# Project target.
target=android-19
```
* you can use after_prepare hook as well to achieve this, just copy  hoos/after_prepare/add_libs_to_project.properties.js to your_project/hooks/after_prepare/,
and copy hooks/after_prepare/dependencies.json to your_project/config/

* make sure all the android build target are the same (in file project.properties). For lib mediarouter, target must be bigger than 18
```
target=android-19
```
##now you can use cordova to build the project.



About [SesamTV](http://www.sesamtv.com)
==========
Since 2003, Sesam TV has pioneered home media convergence with its famous SesamTV Media Center software and more than 2 millions users.  Bringing innovation, experience and products to the connected home, SesamTV empowers leading TV Operators and actors in the industry.

Licence : 

    Copyright (C) <2013> <SesamTV>

    Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
