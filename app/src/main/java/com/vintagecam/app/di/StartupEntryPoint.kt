package com.vintagecam.app.di

import com.vintagecam.app.audio.CameraSoundEngine
import com.vintagecam.profiles.data.ProfileRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface StartupEntryPoint {
    val cameraSoundEngine: CameraSoundEngine
    val profileRepository: ProfileRepository
}
