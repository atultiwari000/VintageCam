package com.vintagecam.app.di

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
    abstract fun bindCameraEngine(impl: CameraXEngineImpl): CameraEngine
}
