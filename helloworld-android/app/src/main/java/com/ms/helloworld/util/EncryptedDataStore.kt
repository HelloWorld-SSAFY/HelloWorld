package com.ms.helloworld.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private const val DATASTORE_NAME = "encrypted_token_prefs"
private const val ACCESS_TOKEN_KEY = "access_token"
private const val REFRESH_TOKEN_KEY = "refresh_token"

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = DATASTORE_NAME)

@Singleton
class EncryptedDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val accessTokenKey = stringPreferencesKey(ACCESS_TOKEN_KEY)
    private val refreshTokenKey = stringPreferencesKey(REFRESH_TOKEN_KEY)

    // 토큰 저장
    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.dataStore.edit { preferences ->
            preferences[accessTokenKey] = accessToken
            preferences[refreshTokenKey] = refreshToken
        }
    }

    // 액세스 토큰 조회
    fun getAccessToken(): String? {
        return runBlocking {
            context.dataStore.data.first()[accessTokenKey]
        }
    }

    // 리프레시 토큰 조회
    fun getRefreshToken(): String? {
        return runBlocking {
            context.dataStore.data.first()[refreshTokenKey]
        }
    }

    // 토큰 삭제
    suspend fun clearTokens() {
        context.dataStore.edit { preferences ->
            preferences.remove(accessTokenKey)
            preferences.remove(refreshTokenKey)
        }
    }
}