package com.crimson.wifimonitor.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RootCauseFindingResponse {
    String observedPattern;
    String evidence;
    String possibleExplanation;
    String confidence;
    List<String> recommendedSteps;
}
