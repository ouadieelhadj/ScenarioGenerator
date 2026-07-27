package com.staging.sg.swam.lis.common.model;
import jakarta.validation.constraints.NotBlank;
public record RepresentationRequest(@NotBlank String createdBy, @NotBlank String justification) {}
