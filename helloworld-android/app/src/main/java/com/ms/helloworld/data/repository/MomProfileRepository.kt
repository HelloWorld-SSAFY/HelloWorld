package com.ms.helloworld.data.repository

import com.ms.helloworld.data.MomProfile
import kotlinx.coroutines.delay
import java.time.LocalDate

class MomProfileRepository {
    
    suspend fun getMomProfile(): MomProfile {
        delay(1000)
        
        return MomProfile(
            nickname = "소중이 엄마",
            pregnancyWeek = 28,
            dueDate = LocalDate.of(2024, 11, 20)
        )
    }
    
    suspend fun updateMomProfile(profile: MomProfile): Boolean {
        delay(500)
        return true
    }
}