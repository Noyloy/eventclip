package com.eventclip.spike
import android.app.Activity
import android.os.Bundle
import android.view.SurfaceView
/** Nonexported native surface host for same-release instrumentation. Never touches project/files/React. */
class ProductSurfaceSmokeActivity:Activity(){lateinit var surface:SurfaceView;private set
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);surface=SurfaceView(this);setContentView(surface)}
}
