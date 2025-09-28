package com.example.helloworld.weeklyserver.controller;


import com.example.helloworld.weeklyserver.dto.WeeklyDietsRes;
import com.example.helloworld.weeklyserver.dto.WeeklyInfoRes;
import com.example.helloworld.weeklyserver.dto.WeeklyWorkoutsRes;
import com.example.helloworld.weeklyserver.service.WeeklyQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/weekly")
@RequiredArgsConstructor
@Tag(name = "Weekly", description = "주차별 정보/운동/식단 API")
public class WeeklyController {

    private final WeeklyQueryService svc;


    @Operation(summary="주차 한줄 정보", description="요청한 주차의 대표 한줄 정보를 반환합니다.")
    @GetMapping(value = "/{weekNo}/info", produces = MediaType.APPLICATION_JSON_VALUE)
    public WeeklyInfoRes getInfo(
            @PathVariable @Parameter(description="주차(예: 1)") int weekNo) {
        return svc.getInfo(weekNo);
    }


    @GetMapping(value = "/{weekNo}/workouts", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="주차 운동 추천", description="텍스트/영상 링크가 혼합될 수 있습니다.")
    public WeeklyWorkoutsRes getWorkouts(
            @PathVariable int weekNo) {
        return svc.getWorkouts(weekNo, true);
    }


    @GetMapping(value = "/{weekNo}/diets", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="주차 식단(1~7일)", description="기본 1~7일, from/to로 범위 조절 가능")
    public WeeklyDietsRes getDiets(
            @PathVariable int weekNo,
            @RequestParam(required=false, defaultValue="1") int from,
            @RequestParam(required=false, defaultValue="7") int to) {
        return svc.getDiets(weekNo, from, to);
    }
}
