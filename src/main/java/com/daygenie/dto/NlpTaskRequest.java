package com.daygenie.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NlpTaskRequest {

    @NotBlank(message = "Natural language input is required")
    private String rawInput;
}