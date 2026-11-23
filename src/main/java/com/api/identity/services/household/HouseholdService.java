package com.api.identity.services.household;

import com.api.identity.entities.Household;
import com.api.identity.entities.HouseholdMember;
import com.api.identity.entities.User;
import com.api.identity.exceptions.BusinessException;
import com.api.identity.exceptions.EntityAlreadyExistsException;
import com.api.identity.exceptions.EntityNotFoundException;
import com.api.identity.exceptions.PermissionDeniedException;
import com.api.identity.records.household.HouseholdDTO;
import com.api.identity.records.household.HouseholdMemberDTO;
import com.api.identity.repositories.HouseholdMemberRepository;
import com.api.identity.repositories.HouseholdRepository;
import com.api.identity.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Household hierarchy rules (single place). Level 0 is the most powerful; a higher number means less power.
 * A caller manages a target only if caller.level <= MANAGE_MAX_LEVEL and caller.level < target.level,
 * and any level it assigns must be > caller.level.
 */
@Service
@RequiredArgsConstructor
public class HouseholdService {

    static final int MANAGE_MAX_LEVEL = 1;

    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository memberRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public HouseholdDTO get(String callerEmail) {
        return toDto(caller(callerEmail));
    }

    @Transactional
    public HouseholdDTO create(String callerEmail, String name) {
        User user = findUser(callerEmail);
        if (memberRepository.existsByUserId(user.getId())) {
            throw new EntityAlreadyExistsException("El usuario ya pertenece a un hogar");
        }
        Household household = householdRepository.save(Household.builder().name(name).build());
        HouseholdMember me = memberRepository.save(
                HouseholdMember.builder().household(household).user(user).level(0).build());
        return toDto(me);
    }

    @Transactional
    public HouseholdDTO addMember(String callerEmail, String email, int level) {
        HouseholdMember caller = caller(callerEmail);
        requireCanManageMembers(caller);
        requireAssignable(caller, level);
        User user = findUser(email);
        if (memberRepository.existsByUserId(user.getId())) {
            throw new EntityAlreadyExistsException("El usuario ya pertenece a un hogar");
        }
        memberRepository.save(
                HouseholdMember.builder().household(caller.getHousehold()).user(user).level(level).build());
        return toDto(caller);
    }

    @Transactional
    public HouseholdDTO changeLevel(String callerEmail, Long userId, int level) {
        HouseholdMember caller = caller(callerEmail);
        HouseholdMember target = target(caller, userId);
        requireCanManage(caller, target);
        requireAssignable(caller, level);
        target.setLevel(level);
        return toDto(caller);
    }

    @Transactional
    public HouseholdDTO removeMember(String callerEmail, Long userId) {
        HouseholdMember caller = caller(callerEmail);
        HouseholdMember target = target(caller, userId);
        requireCanManage(caller, target);
        memberRepository.delete(target);
        return toDto(caller);
    }

    private HouseholdMember caller(String email) {
        return memberRepository.findByUserEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("El usuario no pertenece a un hogar"));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + email));
    }

    private HouseholdMember target(HouseholdMember caller, Long userId) {
        return memberRepository.findByUserId(userId)
                .filter(m -> m.getHousehold().getId().equals(caller.getHousehold().getId()))
                .orElseThrow(() -> new EntityNotFoundException("El miembro no pertenece al hogar"));
    }

    private static boolean canManageMembers(HouseholdMember caller) {
        return caller.getLevel() <= MANAGE_MAX_LEVEL;
    }

    private static boolean canManage(HouseholdMember caller, HouseholdMember target) {
        return canManageMembers(caller) && caller.getLevel() < target.getLevel();
    }

    private static void requireCanManageMembers(HouseholdMember caller) {
        if (!canManageMembers(caller)) {
            throw new PermissionDeniedException("No tenes permiso para administrar el hogar");
        }
    }

    private static void requireCanManage(HouseholdMember caller, HouseholdMember target) {
        if (!canManage(caller, target)) {
            throw new PermissionDeniedException("No tenes permiso para administrar a este miembro");
        }
    }

    private static void requireAssignable(HouseholdMember caller, int level) {
        if (level < 0 || level <= caller.getLevel()) {
            throw new BusinessException("Nivel invalido: debe ser mayor al tuyo");
        }
    }

    private HouseholdDTO toDto(HouseholdMember caller) {
        Household household = caller.getHousehold();
        var members = memberRepository.findByHouseholdIdOrderByLevelAscIdAsc(household.getId()).stream()
                .map(m -> new HouseholdMemberDTO(
                        m.getUser().getId(),
                        m.getUser().getEmail(),
                        m.getUser().getGivenName(),
                        m.getUser().getFamilyName(),
                        m.getLevel(),
                        m.getUser().getId().equals(caller.getUser().getId()),
                        caller.getLevel() < m.getLevel(),
                        canManage(caller, m)))
                .toList();
        return new HouseholdDTO(household.getId(), household.getName(), canManageMembers(caller), members);
    }
}
