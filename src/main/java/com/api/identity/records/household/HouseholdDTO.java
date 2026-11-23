package com.api.identity.records.household;

import java.util.List;

public record HouseholdDTO(Long id, String name, boolean canManage, List<HouseholdMemberDTO> members) {
}
