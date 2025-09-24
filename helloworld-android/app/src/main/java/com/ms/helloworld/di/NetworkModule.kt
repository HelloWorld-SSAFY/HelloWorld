package com.ms.helloworld.di

import android.content.Context
import com.ms.helloworld.network.AuthInterceptor
import com.ms.helloworld.network.TokenAuthenticator
import com.ms.helloworld.network.api.AuthApi
import com.ms.helloworld.network.api.CalendarApi
import com.ms.helloworld.network.api.CoupleApi
import com.ms.helloworld.network.api.DiaryApi
import com.ms.helloworld.network.api.FcmApi
import com.ms.helloworld.network.api.HealthApi
import com.ms.helloworld.network.api.UserApi
import com.ms.helloworld.network.api.WeeklyApi
import com.ms.helloworld.util.LocationManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://j13d204.p.ssafy.io:80" // TODO: 실제 서버 URL로 변경

    // HTTP 로깅 인터셉터
    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    // OkHttpClient 제공
    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)    // HTTP 로그
            .addInterceptor(authInterceptor)       // 토큰 자동 추가
            .authenticator(tokenAuthenticator)     // 401시 토큰 갱신
            .connectTimeout(30, TimeUnit.SECONDS)  // 연결 타임아웃
            .readTimeout(30, TimeUnit.SECONDS)     // 읽기 타임아웃
            .writeTimeout(30, TimeUnit.SECONDS)    // 쓰기 타임아웃
            .build()
    }

    // Retrofit 제공
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * API
     * **/
    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }
    
    @Provides
    @Singleton
    fun provideCalendarApi(retrofit: Retrofit): CalendarApi {
        return retrofit.create(CalendarApi::class.java)
    }

    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi {
        return retrofit.create(UserApi::class.java)
    }

    @Provides
    @Singleton
    fun provideDiaryApi(retrofit: Retrofit): DiaryApi {
        return retrofit.create(DiaryApi::class.java)
    }

    @Provides
    @Singleton
    fun provideCoupleApi(retrofit: Retrofit): CoupleApi {
        return retrofit.create(CoupleApi::class.java)
    }
    
    @Provides
    @Singleton
    fun provideFcmApi(retrofit: Retrofit): FcmApi {
        return retrofit.create(FcmApi::class.java)
    }

    @Provides
    @Singleton
    fun provideHealthApi(retrofit: Retrofit): HealthApi {
        return retrofit.create(HealthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideLocationManager(@ApplicationContext context: Context): LocationManager {
        return LocationManager(context)
    }

    @Provides
    @Singleton
    fun provideWeeklyApi(retrofit: Retrofit): WeeklyApi {
        return retrofit.create(WeeklyApi::class.java)
    }
}