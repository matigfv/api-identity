package com.api.identity.records.household;

import jakarta.validation.constraints.NotNull;

public record HouseholdChangeLevelRequest(@NotNull(message = "El nivel es requerido") Integer level) {
}
