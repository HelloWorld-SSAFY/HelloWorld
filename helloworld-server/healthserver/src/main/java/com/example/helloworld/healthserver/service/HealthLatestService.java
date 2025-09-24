package com.example.helloworld.healthserver.service;


import com.example.helloworld.healthserver.dto.response.HealthLatestResponse;
import com.example.helloworld.healthserver.entity.HealthData;
import com.example.helloworld.healthserver.entity.StepsData;
import com.example.helloworld.healthserver.persistence.HealthDataRepository;
import com.example.helloworld.healthserver.persistence.StepsDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class HealthLatestService {

    private final HealthDataRepository healthRepo;
    private final StepsDataRepository stepsRepo;

    @Transactional(readOnly = true)
    public HealthLatestResponse getLatest(Long coupleId) {
        if (coupleId == null || coupleId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid couple_id");
        }

        StepsData s = stepsRepo.findFirstByCoupleIdOrderByDateDesc(coupleId);
        HealthData h = healthRepo.findFirstByCoupleIdOrderByDateDesc(coupleId);

        HealthLatestResponse.StepItem stepItem = (s == null) ? null :
                new HealthLatestResponse.StepItem(
                        s.getStepsId(), s.getDate(), s.getSteps(), s.getLatitude(), s.getLongitude()
                );

        HealthLatestResponse.HrItem hrItem = (h == null) ? null :
                new HealthLatestResponse.HrItem(
                        h.getHealthId(), h.getDate(), h.getHeartrate(), h.getStress()
                );

        return new HealthLatestResponse(stepItem, hrItem);
    }
}