package com.fitcore.gym.service;

import com.fitcore.gym.domain.MembershipStatus;
import com.fitcore.gym.domain.Role;
import com.fitcore.gym.entity.ActiveMembership;
import com.fitcore.gym.entity.MembershipPlan;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.repository.ActiveMembershipRepository;
import com.fitcore.gym.repository.MembershipPlanRepository;
import com.fitcore.gym.repository.UserRepository;
import com.fitcore.gym.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MembershipRegressionTest.TimeFixture.class)
class MembershipRegressionTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
    private static final String HASH = new BCryptPasswordEncoder().encode("Isolated membership test password");

    @TestConfiguration
    static class TimeFixture {
        @Bean @Primary Clock membershipTestClock() {
            return Clock.fixed(Instant.parse("2026-10-20T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired MembershipPlanRepository plans;
    @Autowired ActiveMembershipRepository memberships;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    private User member;
    private User other;
    private User admin;
    private MembershipPlan basic;
    private MembershipPlan premium;

    @BeforeEach void fixtures() {
        member = user("member", Role.MEMBER);
        other = user("other", Role.MEMBER);
        admin = user("administrator", Role.ADMIN);
        basic = plan("Basic fixture", "29.00", 1);
        premium = plan("Premium fixture", "79.00", 3);
    }

    @Test void newPurchaseUsesOneCalendarMonthAndServerTime() throws Exception {
        purchase(member, basic).andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value("2026-10-20"))
                .andExpect(jsonPath("$.endDate").value("2026-11-20"))
                .andExpect(jsonPath("$.remainingDays").value(31))
                .andExpect(jsonPath("$.serverDate").value("2026-10-20"))
                .andExpect(jsonPath("$.timeZone").value("UTC"));
        assertThat(memberships.findByUser(member)).hasSize(1);
    }

    @Test void duplicatePurchaseIsConflictAndDoesNotWrite() throws Exception {
        purchase(member, basic).andExpect(status().isOk());
        var before = rows();
        purchase(member, basic).andExpect(status().isConflict());
        assertThat(rows()).isEqualTo(before);
    }

    @Test void purchaseCannotBypassUpgradeWithAnotherPlan() throws Exception {
        membership(member, basic, TODAY.minusDays(5), TODAY.plusDays(25), MembershipStatus.ACTIVE);
        var before = rows();
        purchase(member, premium).andExpect(status().isConflict());
        assertThat(rows()).isEqualTo(before);
    }

    @Test void activeRenewalExtendsExistingEndAndPreservesOtherHistory() throws Exception {
        var history = membership(member, basic, TODAY.minusMonths(3), TODAY.minusMonths(2), MembershipStatus.EXPIRED);
        var current = membership(member, basic, TODAY.minusDays(10), LocalDate.of(2026, 10, 31), MembershipStatus.ACTIVE);
        renew(member, current, "2026-10-31").andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(current.getId()))
                .andExpect(jsonPath("$.startDate").value(current.getStartDate().toString()))
                .andExpect(jsonPath("$.endDate").value("2026-11-30"));
        assertThat(memberships.findByUser(member)).hasSize(2);
        assertThat(memberships.findById(history.getId()).orElseThrow().getEndDate()).isEqualTo(history.getEndDate());
    }

    @Test void expiredRenewalStartsTodayAndKeepsOriginalPeriod() throws Exception {
        var old = membership(member, basic, TODAY.minusMonths(2), TODAY.minusDays(1), MembershipStatus.ACTIVE);
        renew(member, old, old.getEndDate().toString()).andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.endDate").value(TODAY.plusMonths(1).toString()));
        var periods = memberships.findByUser(member);
        assertThat(periods).hasSize(2);
        assertThat(memberships.findById(old.getId()).orElseThrow().getEndDate()).isEqualTo(TODAY.minusDays(1));
    }

    @Test void renewalOnExclusiveEndDateReactivatesToday() throws Exception {
        var old = membership(member, basic, TODAY.minusMonths(1), TODAY, MembershipStatus.ACTIVE);
        current().andExpect(status().isNoContent());
        renew(member, old, TODAY.toString()).andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value(TODAY.toString()));
        assertThat(memberships.findByUser(member)).hasSize(2);
    }

    @Test void repeatedActiveRenewalFromSameStateIsRejected() throws Exception {
        var current = membership(member, basic, TODAY, TODAY.plusMonths(1), MembershipStatus.ACTIVE);
        String expected = current.getEndDate().toString();
        renew(member, current, expected).andExpect(status().isOk());
        var before = rows();
        renew(member, current, expected).andExpect(status().isConflict());
        assertThat(rows()).isEqualTo(before);
    }

    @Test void repeatedExpiredRenewalCreatesOnlyOneNewPeriod() throws Exception {
        var old = membership(member, basic, TODAY.minusMonths(2), TODAY.minusMonths(1), MembershipStatus.EXPIRED);
        renew(member, old, old.getEndDate().toString()).andExpect(status().isOk());
        var before = rows();
        renew(member, old, old.getEndDate().toString()).andExpect(status().isConflict());
        assertThat(rows()).isEqualTo(before);
    }

    @Test void upgradeRetainsExpiryAndOriginalHistoricalRecord() throws Exception {
        var old = membership(member, basic, TODAY.minusDays(10), TODAY.plusDays(20), MembershipStatus.ACTIVE);
        upgrade(member, premium, old).andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.id").value(premium.getId()))
                .andExpect(jsonPath("$.endDate").value(old.getEndDate().toString()))
                .andExpect(jsonPath("$.startDate").value(TODAY.toString()));
        assertThat(memberships.findByUser(member)).hasSize(2);
        var historical = memberships.findById(old.getId()).orElseThrow();
        assertThat(historical.getStatus()).isEqualTo(MembershipStatus.CANCELLED);
        assertThat(historical.getPlan().getId()).isEqualTo(basic.getId());
        assertThat(historical.getStartDate()).isEqualTo(TODAY.minusDays(10));
        assertThat(historical.getEndDate()).isEqualTo(TODAY.plusDays(20));
        current().andExpect(status().isOk()).andExpect(jsonPath("$.plan.id").value(premium.getId()));
    }

    @Test void upgradeUsesPackagePriceRatherThanMonthlyRate() throws Exception {
        var old = membership(member, basic, TODAY, TODAY.plusMonths(1), MembershipStatus.ACTIVE);
        // Premium costs more per package, but less per month: 79 / 3 < 29 / 1.
        upgrade(member, premium, old).andExpect(status().isOk());
    }

    @Test void downgradeAndSamePlanAreRejectedWithoutChangingHistory() throws Exception {
        var old = membership(member, premium, TODAY, TODAY.plusMonths(3), MembershipStatus.ACTIVE);
        var before = rows();
        upgrade(member, basic, old).andExpect(status().isConflict());
        upgrade(member, premium, old).andExpect(status().isConflict());
        assertThat(rows()).isEqualTo(before);
    }

    @Test void expiredMembershipCannotBeUpgraded() throws Exception {
        var old = membership(member, basic, TODAY.minusMonths(1), TODAY, MembershipStatus.ACTIVE);
        var before = rows();
        upgrade(member, premium, old).andExpect(status().isConflict());
        assertThat(rows()).isEqualTo(before);
    }

    @Test void repeatedUpgradeDoesNotCreateAnotherActivePeriod() throws Exception {
        var old = membership(member, basic, TODAY, TODAY.plusMonths(1), MembershipStatus.ACTIVE);
        upgrade(member, premium, old).andExpect(status().isOk());
        var before = rows();
        upgrade(member, premium, old).andExpect(status().isConflict());
        assertThat(rows()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0})
    void endedMembershipsAreNotCurrentAndHistoryShowsExpired(int endOffset) throws Exception {
        membership(member, basic, TODAY.minusMonths(1), TODAY.plusDays(endOffset), MembershipStatus.ACTIVE);
        current().andExpect(status().isNoContent());
        mvc.perform(get("/api/memberships/user/" + member.getId()).header("Authorization", token(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("EXPIRED"));
        mvc.perform(get("/api/dashboard/" + member.getId()).header("Authorization", token(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dashboardCards[0].value").value("No Plan"));
    }

    @Test void membershipIsCurrentOnStartAndDayBeforeEnd() throws Exception {
        membership(member, basic, TODAY, TODAY.plusDays(1), MembershipStatus.ACTIVE);
        current().andExpect(status().isOk()).andExpect(jsonPath("$.remainingDays").value(1));
    }

    @Test void futureOrCancelledMembershipDoesNotGrantCurrentBenefits() throws Exception {
        membership(member, basic, TODAY.plusDays(1), TODAY.plusMonths(1), MembershipStatus.ACTIVE);
        membership(member, premium, TODAY, TODAY.plusMonths(3), MembershipStatus.CANCELLED);
        current().andExpect(status().isNoContent());
    }

    @Test void laterCancelledRecordDoesNotHideAnEarlierCurrentMembership() throws Exception {
        var active = membership(member, basic, TODAY.minusDays(3), TODAY.plusMonths(1), MembershipStatus.ACTIVE);
        membership(member, premium, TODAY, TODAY.plusMonths(3), MembershipStatus.CANCELLED);
        current().andExpect(status().isOk()).andExpect(jsonPath("$.id").value(active.getId()));
    }

    @Test void legacyDuplicateCurrentMembershipsRequireReviewWithoutAutomaticCleanup() throws Exception {
        membership(member, basic, TODAY.minusDays(2), TODAY.plusMonths(1), MembershipStatus.ACTIVE);
        membership(member, premium, TODAY.minusDays(1), TODAY.plusMonths(3), MembershipStatus.ACTIVE);
        var before = rows();
        purchase(member, basic).andExpect(status().isConflict());
        current().andExpect(status().isConflict());
        assertThat(rows()).isEqualTo(before);
    }

    @Test void invalidPlanDurationCannotCreateAnInvalidPeriod() throws Exception {
        basic.setDurationMonths(0); plans.saveAndFlush(basic);
        purchase(member, basic).andExpect(status().isBadRequest());
        assertThat(memberships.findByUser(member)).isEmpty();
    }

    @Test void cancelledHistoryCannotBeRenewed() throws Exception {
        var old = membership(member, basic, TODAY.minusMonths(1), TODAY.plusDays(3), MembershipStatus.CANCELLED);
        renew(member, old, old.getEndDate().toString()).andExpect(status().isConflict());
        assertThat(memberships.findByUser(member)).hasSize(1);
    }

    @Test void renewalCannotUseAnotherMembersMembershipId() throws Exception {
        var old = membership(other, basic, TODAY, TODAY.plusMonths(1), MembershipStatus.ACTIVE);
        var before = rows();
        renew(member, old, old.getEndDate().toString()).andExpect(status().isNotFound());
        assertThat(rows()).isEqualTo(before);
    }

    @ParameterizedTest @EnumSource(value = Role.class, names = {"MEMBER", "TRAINER"})
    void nonOwnersCannotRenewOrUpgrade(Role role) throws Exception {
        var caller = user("caller-" + role, role);
        var old = membership(other, basic, TODAY, TODAY.plusMonths(1), MembershipStatus.ACTIVE);
        var before = rows();
        mvc.perform(post(renewPath(other, old)).header("Authorization", token(caller))
                .contentType(MediaType.APPLICATION_JSON).content(expected(old.getEndDate().toString())))
                .andExpect(status().isForbidden());
        mvc.perform(post(upgradePath(other, premium)).header("Authorization", token(caller))
                .contentType(MediaType.APPLICATION_JSON).content(upgradeBody(old))).andExpect(status().isForbidden());
        assertThat(rows()).isEqualTo(before);
    }

    @Test void unauthenticatedAndDisabledTokensCannotUseNewOperations() throws Exception {
        var old = membership(member, basic, TODAY, TODAY.plusMonths(1), MembershipStatus.ACTIVE);
        String stale = token(member);
        member.setEnabled(false); users.saveAndFlush(member);
        for (String auth : List.of("", stale)) {
            mvc.perform(post(renewPath(member, old)).header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON).content(expected(old.getEndDate().toString())))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post(upgradePath(member, premium)).header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON).content(upgradeBody(old)))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(memberships.findByUser(member)).hasSize(1);
    }

    @Test void privateAdministratorCanRenewAndUpgradeForMember() throws Exception {
        var old = membership(member, basic, TODAY, TODAY.plusMonths(1), MembershipStatus.ACTIVE);
        mvc.perform(post(renewPath(member, old)).header("Authorization", token(admin))
                .contentType(MediaType.APPLICATION_JSON).content(expected(old.getEndDate().toString())))
                .andExpect(status().isOk());
        var extended = memberships.findById(old.getId()).orElseThrow();
        mvc.perform(post(upgradePath(member, premium)).header("Authorization", token(admin))
                .contentType(MediaType.APPLICATION_JSON).content(upgradeBody(extended)))
                .andExpect(status().isOk());
    }

    @Test void missingExpectedStateIsValidationError() throws Exception {
        var old = membership(member, basic, TODAY, TODAY.plusMonths(1), MembershipStatus.ACTIVE);
        mvc.perform(post(renewPath(member, old)).header("Authorization", token(member))
                .contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isBadRequest());
        mvc.perform(post(upgradePath(member, premium)).header("Authorization", token(member))
                .contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isBadRequest());
    }

    @Test void adminCountsExcludeExpiredAndFutureMemberships() throws Exception {
        membership(member, basic, TODAY.minusMonths(1), TODAY, MembershipStatus.ACTIVE);
        membership(other, premium, TODAY.plusDays(1), TODAY.plusMonths(3), MembershipStatus.ACTIVE);
        mvc.perform(get("/api/admin/dashboard").header("Authorization", token(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.adminCards[1].value").value("0"));
    }

    @Test void preservedDuplicateMembershipsDoNotBlockAdministratorReview() throws Exception {
        membership(member, basic, TODAY.minusDays(2), TODAY.plusMonths(1), MembershipStatus.ACTIVE);
        membership(member, premium, TODAY.minusDays(1), TODAY.plusMonths(3), MembershipStatus.ACTIVE);
        var before = rows();
        mvc.perform(get("/api/admin/users").header("Authorization", token(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].membership").value(org.hamcrest.Matchers.hasItem("Review required")));
        mvc.perform(get("/api/admin/dashboard").header("Authorization", token(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.adminCards[1].value").value("0"));
        current().andExpect(status().isConflict());
        assertThat(rows()).isEqualTo(before);
    }

    private User user(String name, Role role) {
        return users.saveAndFlush(User.builder().fullName("Membership " + name).email(name + "@membership.test")
                .passwordHash(HASH).role(role).enabled(true).build());
    }
    private MembershipPlan plan(String name, String price, int months) {
        return plans.saveAndFlush(MembershipPlan.builder().name(name).price(new BigDecimal(price))
                .durationMonths(months).features(List.of("Gym access")).recommended(false).build());
    }
    private ActiveMembership membership(User owner, MembershipPlan plan, LocalDate start, LocalDate end, MembershipStatus status) {
        return memberships.saveAndFlush(ActiveMembership.builder().user(owner).plan(plan).startDate(start).endDate(end).status(status).build());
    }
    private String token(User user) { return "Bearer " + jwt.generateToken(user); }
    private ResultActions current() throws Exception {
        return mvc.perform(get("/api/memberships/user/" + member.getId() + "/active").header("Authorization", token(member)));
    }
    private ResultActions purchase(User owner, MembershipPlan plan) throws Exception {
        return mvc.perform(post("/api/memberships/subscribe/" + owner.getId() + "/" + plan.getId()).header("Authorization", token(owner)));
    }
    private ResultActions renew(User owner, ActiveMembership membership, String end) throws Exception {
        return mvc.perform(post(renewPath(owner, membership)).header("Authorization", token(owner))
                .contentType(MediaType.APPLICATION_JSON).content(expected(end)));
    }
    private ResultActions upgrade(User owner, MembershipPlan plan, ActiveMembership membership) throws Exception {
        return mvc.perform(post(upgradePath(owner, plan)).header("Authorization", token(owner))
                .contentType(MediaType.APPLICATION_JSON).content(upgradeBody(membership)));
    }
    private String renewPath(User owner, ActiveMembership m) { return "/api/memberships/renew/" + owner.getId() + "/" + m.getId(); }
    private String upgradePath(User owner, MembershipPlan plan) { return "/api/memberships/upgrade/" + owner.getId() + "/" + plan.getId(); }
    private String expected(String end) { return "{\"expectedEndDate\":\"" + end + "\"}"; }
    private String upgradeBody(ActiveMembership old) { return "{\"membershipId\":" + old.getId() + ",\"expectedEndDate\":\"" + old.getEndDate() + "\"}"; }
    private List<java.util.Map<String, Object>> rows() {
        // MockMvc shares this test transaction; expose committed-intent JPA changes before comparing SQL snapshots.
        entityManager.flush();
        return jdbc.queryForList("select * from active_memberships order by id");
    }
}
