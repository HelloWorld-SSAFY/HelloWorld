package com.example.helloworld.calendar_diary_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;


@EnableFeignClients
@SpringBootApplication
public class CalendarDiaryServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(CalendarDiaryServerApplication.class, args);
	}

}
