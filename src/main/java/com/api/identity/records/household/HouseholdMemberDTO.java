package com.api.identity.records.household;

public record HouseholdMemberDTO(
        Long userId,
        String email,
        String givenName,
        String familyName,
        int level,
        boolean self,
        boolean supervised,
        boolean canManage) {
}
