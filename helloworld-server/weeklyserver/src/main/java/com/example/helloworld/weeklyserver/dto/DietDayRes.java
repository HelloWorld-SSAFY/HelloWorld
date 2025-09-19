package com.example.helloworld.weeklyserver.dto;




public record DietDayRes(
        int dayInWeek, int dayNo, String food, String detail, String imgUrl
) {
}
