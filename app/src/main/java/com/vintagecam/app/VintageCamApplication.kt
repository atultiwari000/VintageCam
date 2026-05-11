package com.vintagecam.app

import android.app.Application
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import com.vintagecam.app.di.StartupEntryPoint

@HiltAndroidApp
class VintageCamApplication : Application() {
	override fun onCreate() {
		super.onCreate()
		val entryPoint = EntryPointAccessors.fromApplication(this, StartupEntryPoint::class.java)
		entryPoint.cameraSoundEngine.preload(this, entryPoint.profileRepository.getProfiles())
	}
}
