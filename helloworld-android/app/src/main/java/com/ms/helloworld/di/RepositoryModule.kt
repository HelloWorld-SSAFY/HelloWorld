package com.ms.helloworld.di

import com.ms.helloworld.network.api.OnboardingApiService
import com.ms.helloworld.repository.OnboardingRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideOnboardingRepository(
        apiService: OnboardingApiService
    ): OnboardingRepository {
        return OnboardingRepository(apiService)
    }
}