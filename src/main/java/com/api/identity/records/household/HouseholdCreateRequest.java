package com.api.identity.records.household;

import jakarta.validation.constraints.NotBlank;

public record HouseholdCreateRequest(@NotBlank(message = "El nombre es requerido") String name) {
}
