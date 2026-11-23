package com.api.identity.controllers;

import com.api.identity.records.household.HouseholdAddMemberRequest;
import com.api.identity.records.household.HouseholdChangeLevelRequest;
import com.api.identity.records.household.HouseholdCreateRequest;
import com.api.identity.records.household.HouseholdDTO;
import com.api.identity.security.SecurityUtils;
import com.api.identity.services.household.HouseholdService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/households")
@Tag(name = "Households", description = "Jerarquia del hogar")
@RequiredArgsConstructor
@Validated
public class HouseholdController {

    private final HouseholdService householdService;

    @Operation(summary = "Hogar del usuario autenticado (404 si no tiene)")
    @GetMapping("/me")
    public HouseholdDTO me() {
        return householdService.get(SecurityUtils.currentEmail());
    }

    @Operation(summary = "Crea un hogar; el usuario queda en nivel 0")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HouseholdDTO create(@Valid @RequestBody HouseholdCreateRequest request) {
        return householdService.create(SecurityUtils.currentEmail(), request.name());
    }

    @Operation(summary = "Agrega un usuario existente al hogar por email")
    @PostMapping("/members")
    @ResponseStatus(HttpStatus.CREATED)
    public HouseholdDTO addMember(@Valid @RequestBody HouseholdAddMemberRequest request) {
        return householdService.addMember(SecurityUtils.currentEmail(), request.email(), request.level());
    }

    @Operation(summary = "Cambia el nivel de un miembro")
    @PatchMapping("/members/{userId}/level")
    public HouseholdDTO changeLevel(@PathVariable Long userId,
                                    @Valid @RequestBody HouseholdChangeLevelRequest request) {
        return householdService.changeLevel(SecurityUtils.currentEmail(), userId, request.level());
    }

    @Operation(summary = "Quita un miembro del hogar")
    @DeleteMapping("/members/{userId}")
    public HouseholdDTO removeMember(@PathVariable Long userId) {
        return householdService.removeMember(SecurityUtils.currentEmail(), userId);
    }
}
