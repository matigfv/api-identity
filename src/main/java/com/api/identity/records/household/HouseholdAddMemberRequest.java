package com.api.identity.records.household;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record HouseholdAddMemberRequest(
        @NotBlank(message = "El email es requerido") @Email(message = "Formato de email inválido") String email,
        @NotNull(message = "El nivel es requerido") Integer level) {
}
