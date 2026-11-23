package com.api.identity.unit.services.household

import com.api.identity.entities.Household
import com.api.identity.entities.HouseholdMember
import com.api.identity.entities.User
import com.api.identity.exceptions.BusinessException
import com.api.identity.exceptions.EntityAlreadyExistsException
import com.api.identity.exceptions.EntityNotFoundException
import com.api.identity.exceptions.PermissionDeniedException
import com.api.identity.repositories.HouseholdMemberRepository
import com.api.identity.repositories.HouseholdRepository
import com.api.identity.repositories.UserRepository
import com.api.identity.services.household.HouseholdService
import spock.lang.Specification

class HouseholdServiceTest extends Specification {

    HouseholdRepository householdRepository = Mock(HouseholdRepository)
    HouseholdMemberRepository memberRepository = Mock(HouseholdMemberRepository)
    UserRepository userRepository = Mock(UserRepository)

    HouseholdService service = new HouseholdService(householdRepository, memberRepository, userRepository)

    Household household = Household.builder().id(1L).name("Casa").build()
    List<HouseholdMember> members = []

    def setup() {
        memberRepository.findByHouseholdIdOrderByLevelAscIdAsc(1L) >> { members }
    }

    private HouseholdMember member(long userId, String email, int level) {
        def user = User.builder().id(userId).email(email).givenName("G$userId").familyName("F$userId").build()
        def m = HouseholdMember.builder().id(userId).household(household).user(user).level(level).build()
        members << m
        memberRepository.findByUserEmail(email) >> Optional.of(m)
        memberRepository.findByUserId(userId) >> Optional.of(m)
        m
    }

    def "get - throws EntityNotFoundException when the caller has no household"() {
        given:
        memberRepository.findByUserEmail("a@x.com") >> Optional.empty()

        when:
        service.get("a@x.com")

        then:
        thrown(EntityNotFoundException)
    }

    def "get - flags supervised only for members with a higher level number"() {
        given:
        member(1, "mom@x.com", 1)
        member(2, "dad@x.com", 0)
        member(3, "kid@x.com", 2)
        member(4, "kid2@x.com", 1)

        when:
        def dto = service.get("mom@x.com")
        def byId = dto.members().collectEntries { [(it.userId()): it] }

        then:
        dto.canManage()
        byId[1L].self() && !byId[1L].supervised()
        !byId[2L].supervised()
        byId[3L].supervised()
        !byId[4L].supervised()
    }

    def "get - canManage flags for level 1 caller: only members with level >= 2"() {
        given:
        member(1, "mom@x.com", 1)
        member(2, "dad@x.com", 0)
        member(3, "kid@x.com", 2)
        member(4, "teen@x.com", 3)
        member(5, "sib@x.com", 1)

        when:
        def byId = service.get("mom@x.com").members().collectEntries { [(it.userId()): it] }

        then:
        !byId[1L].canManage()
        !byId[2L].canManage()
        byId[3L].canManage()
        byId[4L].canManage()
        !byId[5L].canManage()
    }

    def "get - level 0 caller manages everyone except level 0 members"() {
        given:
        member(1, "dad@x.com", 0)
        member(2, "mom@x.com", 1)
        member(3, "kid@x.com", 2)

        when:
        def dto = service.get("dad@x.com")
        def byId = dto.members().collectEntries { [(it.userId()): it] }

        then:
        dto.canManage()
        !byId[1L].canManage()
        byId[2L].canManage()
        byId[3L].canManage()
    }

    def "get - level 2 caller manages nobody, not even level 3"() {
        given:
        member(1, "dad@x.com", 0)
        member(2, "kid@x.com", 2)
        member(3, "teen@x.com", 3)

        when:
        def dto = service.get("kid@x.com")

        then:
        !dto.canManage()
        dto.members().every { !it.canManage() }
    }

    def "create - caller becomes level 0"() {
        given:
        def user = User.builder().id(1L).email("dad@x.com").build()
        userRepository.findByEmail("dad@x.com") >> Optional.of(user)
        memberRepository.existsByUserId(1L) >> false
        householdRepository.save(_) >> { Household h -> h.id = 1L; h }
        memberRepository.save(_) >> { HouseholdMember m -> members << m; m }

        when:
        def dto = service.create("dad@x.com", "Casa")

        then:
        dto.canManage()
        dto.members()*.level() == [0]
    }

    def "create - one household per user (conflict)"() {
        given:
        userRepository.findByEmail("dad@x.com") >> Optional.of(User.builder().id(1L).build())
        memberRepository.existsByUserId(1L) >> true

        when:
        service.create("dad@x.com", "Casa")

        then:
        thrown(EntityAlreadyExistsException)
    }

    def "addMember - level 0 caller adds a spouse at level 1"() {
        given:
        member(1, "dad@x.com", 0)
        userRepository.findByEmail("mom@x.com") >> Optional.of(User.builder().id(2L).email("mom@x.com").build())
        memberRepository.existsByUserId(2L) >> false
        memberRepository.save(_) >> { HouseholdMember m -> members << m; m }

        when:
        def dto = service.addMember("dad@x.com", "mom@x.com", 1)

        then:
        dto.members()*.level() == [0, 1]
    }

    def "addMember - level 1 caller adds levels 2 and 3"() {
        given:
        member(1, "mom@x.com", 1)
        userRepository.findByEmail("kid@x.com") >> Optional.of(User.builder().id(3L).email("kid@x.com").build())
        memberRepository.existsByUserId(3L) >> false
        memberRepository.save(_) >> { HouseholdMember m -> members << m; m }

        when:
        def dto = service.addMember("mom@x.com", "kid@x.com", level)

        then:
        dto.members()*.level() == [1, level]

        where:
        level << [2, 3]
    }

    def "addMember - level 1 caller cannot assign level #level"() {
        given:
        member(1, "mom@x.com", 1)

        when:
        service.addMember("mom@x.com", "other@x.com", level)

        then:
        thrown(BusinessException)

        where:
        level << [1, 0]
    }

    def "addMember - rejects a second level 0 from a level 0 caller"() {
        given:
        member(1, "dad@x.com", 0)

        when:
        service.addMember("dad@x.com", "other@x.com", 0)

        then:
        thrown(BusinessException)
    }

    def "addMember - rejects negative level"() {
        given:
        member(1, "dad@x.com", 0)

        when:
        service.addMember("dad@x.com", "other@x.com", -1)

        then:
        thrown(BusinessException)
    }

    def "addMember - denied when caller level is above MANAGE_MAX_LEVEL"() {
        given:
        member(1, "kid@x.com", 2)

        when:
        service.addMember("kid@x.com", "other@x.com", 3)

        then:
        thrown(PermissionDeniedException)
    }

    def "addMember - 404 for unknown email"() {
        given:
        member(1, "dad@x.com", 0)
        userRepository.findByEmail("ghost@x.com") >> Optional.empty()

        when:
        service.addMember("dad@x.com", "ghost@x.com", 1)

        then:
        thrown(EntityNotFoundException)
    }

    def "addMember - 409 when the user already belongs to a household"() {
        given:
        member(1, "dad@x.com", 0)
        userRepository.findByEmail("kid@x.com") >> Optional.of(User.builder().id(3L).email("kid@x.com").build())
        memberRepository.existsByUserId(3L) >> true

        when:
        service.addMember("dad@x.com", "kid@x.com", 2)

        then:
        thrown(EntityAlreadyExistsException)
    }

    def "changeLevel - level 0 caller promotes a child to level 1"() {
        given:
        member(1, "dad@x.com", 0)
        def kid = member(2, "kid@x.com", 2)

        when:
        def dto = service.changeLevel("dad@x.com", 2L, 1)

        then:
        kid.level == 1
        dto.members()*.level() == [0, 1]
    }

    def "changeLevel - level 1 caller re-levels level #from to 3"() {
        given:
        member(1, "mom@x.com", 1)
        def kid = member(2, "kid@x.com", from)

        when:
        service.changeLevel("mom@x.com", 2L, 3)

        then:
        kid.level == 3

        where:
        from << [2, 3]
    }

    def "changeLevel - level 1 caller cannot assign level #level"() {
        given:
        member(1, "mom@x.com", 1)
        member(2, "kid@x.com", 2)

        when:
        service.changeLevel("mom@x.com", 2L, level)

        then:
        thrown(BusinessException)

        where:
        level << [1, 0]
    }

    def "changeLevel - level 1 caller cannot manage level #targetLevel"() {
        given:
        member(1, "mom@x.com", 1)
        member(2, "other@x.com", targetLevel)

        when:
        service.changeLevel("mom@x.com", 2L, 3)

        then:
        thrown(PermissionDeniedException)

        where:
        targetLevel << [0, 1]
    }

    def "changeLevel - level 0 members are untouchable, even by another level 0"() {
        given:
        member(1, "dad@x.com", 0)
        member(2, "mom@x.com", 0)

        when:
        service.changeLevel("dad@x.com", 2L, 1)

        then:
        thrown(PermissionDeniedException)
    }

    def "changeLevel - level 2 cannot manage level 3"() {
        given:
        member(1, "kid@x.com", 2)
        member(2, "teen@x.com", 3)

        when:
        service.changeLevel("kid@x.com", 2L, 4)

        then:
        thrown(PermissionDeniedException)
    }

    def "changeLevel - 404 when the target is not in the caller's household"() {
        given:
        member(1, "dad@x.com", 0)
        def other = HouseholdMember.builder().id(9L).household(Household.builder().id(2L).build())
                .user(User.builder().id(9L).build()).level(1).build()
        memberRepository.findByUserId(9L) >> Optional.of(other)

        when:
        service.changeLevel("dad@x.com", 9L, 2)

        then:
        thrown(EntityNotFoundException)
    }

    def "removeMember - level 1 caller removes a child"() {
        given:
        member(1, "mom@x.com", 1)
        def kid = member(2, "kid@x.com", 2)

        when:
        def dto = service.removeMember("mom@x.com", 2L)

        then:
        1 * memberRepository.delete(kid) >> { members.remove(kid) }
        dto.members()*.level() == [1]
    }

    def "removeMember - level 0 member cannot be removed"() {
        given:
        member(1, "dad@x.com", 0)
        member(2, "mom@x.com", 0)

        when:
        service.removeMember("dad@x.com", 2L)

        then:
        thrown(PermissionDeniedException)
        0 * memberRepository.delete(_)
    }

    def "removeMember - level 1 caller cannot remove level 0 or another level 1"() {
        given:
        member(1, "mom@x.com", 1)
        member(2, "other@x.com", targetLevel)

        when:
        service.removeMember("mom@x.com", 2L)

        then:
        thrown(PermissionDeniedException)

        where:
        targetLevel << [0, 1]
    }
}
