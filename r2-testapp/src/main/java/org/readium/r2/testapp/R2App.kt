package org.readium.r2.testapp

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import nl.komponents.kovenant.android.startKovenant
import nl.komponents.kovenant.android.stopKovenant

class R2App : Application() {

  private val TAG = this::class.java.simpleName

  override fun onCreate() {
    super.onCreate()
    // Configure Kovenant with standard dispatchers
    // suitable for an Android environment.
    startKovenant()
  }
  override fun onTerminate() {
    super.onTerminate()
    // Dispose of the Kovenant thread pools.
    // For quicker shutdown you could use
    // `force=true`, which ignores all current
    // scheduled tasks
    stopKovenant()
  }
}

val Context.resolver: ContentResolver
  get() = applicationContext.contentResolver