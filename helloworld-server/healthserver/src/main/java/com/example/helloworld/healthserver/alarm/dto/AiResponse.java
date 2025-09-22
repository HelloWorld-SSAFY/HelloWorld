package com.example.helloworld.healthserver.alarm.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AiResponse {
    private boolean ok;
    private Boolean anomaly;
    private String risk_level;
    private String mode; // normal|restrict|cooldown|emergency
    private List<String> reasons;
    private Map<String,Object> recommendation;
    private Boolean new_session;
    private Integer cooldown_min;
    private Map<String,Object> cooldown;
    private Map<String,Object> action;
    private List<Map<String,Object>> safe_templates;
}
