package com.api.identity.repositories;

import com.api.identity.entities.HouseholdMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, Long> {
    Optional<HouseholdMember> findByUserEmail(String email);

    Optional<HouseholdMember> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    List<HouseholdMember> findByHouseholdIdOrderByLevelAscIdAsc(Long householdId);
}
