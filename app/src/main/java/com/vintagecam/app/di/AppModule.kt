package com.vintagecam.app.di

import com.vintagecam.app.audio.CameraSoundEngine
import com.vintagecam.app.audio.SoundPoolEngine
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import com.vintagecam.camera.CameraEngine
import com.vintagecam.camera.CameraXEngineImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    @ExperimentalCamera2Interop
    abstract fun bindCameraEngine(impl: CameraXEngineImpl): CameraEngine

    @Binds
    abstract fun bindCameraSoundEngine(impl: SoundPoolEngine): CameraSoundEngine
}
