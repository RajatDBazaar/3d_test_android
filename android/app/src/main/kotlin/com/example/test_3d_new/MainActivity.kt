package com.example.test_3d_new

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import com.example.test_3d_new.MyThreediViewFactory

class MainActivity: FlutterActivity()
 {
     override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
         super.configureFlutterEngine(flutterEngine)

         flutterEngine
              .platformViewsController
              .registry
              .registerViewFactory("myThreeDiView", MyThreediViewFactory(this))


     }
 }

