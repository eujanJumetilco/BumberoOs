package com.groupnine.bumberoos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** An instruction from the BFP Incident Commander, e.g. "Establish water supply at the corner hydrant". */
public record CommanderOrderRequest(
        @NotBlank @Size(max = 1000) String instruction
) {}
