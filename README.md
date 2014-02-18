ChromeCast plugin for Cordova
=========

##Installation

* prepare libs and tools with this [tutorial](https://github.com/googlecast/cast-android-tictactoe/blob/master/INSTALL_CAST_ECLIPSE.txt)
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

