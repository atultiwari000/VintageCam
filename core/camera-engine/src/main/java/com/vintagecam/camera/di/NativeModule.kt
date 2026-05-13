package com.vintagecam.camera.di

import com.vintagecam.imageprocessor.NativeImageProcessor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NativeModule {

    @Provides
    @Singleton
    fun provideNativeImageProcessor(): NativeImageProcessor {
        return NativeImageProcessor()
    }
}
