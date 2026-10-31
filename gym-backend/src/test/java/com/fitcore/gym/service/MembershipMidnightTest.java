package com.fitcore.gym.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitcore.gym.domain.MembershipStatus;
import com.fitcore.gym.domain.Role;
import com.fitcore.gym.entity.ActiveMembership;
import com.fitcore.gym.entity.MembershipPlan;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.fixture.FixtureActiveMembershipRepository;
import com.fitcore.gym.fixture.FixtureMembershipPlanRepository;
import com.fitcore.gym.fixture.FixtureUserRepository;
import com.fitcore.gym.security.JwtService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@org.junit.jupiter.api.extension.ExtendWith(com.fitcore.gym.tenancy.RegressionGym.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MembershipMidnightTest.AdvancingTime.class)
class MembershipMidnightTest {
    @TestConfiguration
    static class AdvancingTime {
        @Bean
        @Primary
        Clock expiryBoundaryClock() {
            return new Clock() {
                final AtomicInteger calls = new AtomicInteger();

                public ZoneId getZone() {
                    return ZoneOffset.UTC;
                }

                public Clock withZone(ZoneId zone) {
                    return this;
                }

                public Instant instant() {
                    return Instant.parse(
                            calls.getAndIncrement() == 0
                                    ? "2030-06-03T23:59:59Z"
                                    : "2030-06-04T00:00:01Z");
                }
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired FixtureUserRepository users;
    @Autowired FixtureMembershipPlanRepository plans;
    @Autowired FixtureActiveMembershipRepository memberships;

    @Test
    void upgradeCrossingMidnightNeverCreatesAnEmptyPeriod() throws Exception {
        User member =
                users.saveAndFlush(
                        User.builder()
                                .gymId(com.fitcore.gym.tenancy.RegressionGym.ID)
                                .fullName("Midnight fixture")
                                .email("midnight@phase2.test")
                                .role(Role.MEMBER)
                                .enabled(true)
                                .passwordHash(
                                        new BCryptPasswordEncoder()
                                                .encode("Local midnight fixture"))
                                .build());
        MembershipPlan before =
                plans.saveAndFlush(
                        MembershipPlan.builder()
                                .gymId(com.fitcore.gym.tenancy.RegressionGym.ID)
                                .name("Midnight Basic")
                                .price(new BigDecimal("29"))
                                .durationMonths(1)
                                .features(List.of())
                                .build());
        MembershipPlan next =
                plans.saveAndFlush(
                        MembershipPlan.builder()
                                .gymId(com.fitcore.gym.tenancy.RegressionGym.ID)
                                .name("Midnight Premium")
                                .price(new BigDecimal("79"))
                                .durationMonths(3)
                                .features(List.of())
                                .build());
        ActiveMembership current =
                memberships.saveAndFlush(
                        ActiveMembership.builder()
                                .gymId(com.fitcore.gym.tenancy.RegressionGym.ID)
                                .user(member)
                                .plan(before)
                                .startDate(LocalDate.of(2030, 6, 1))
                                .endDate(LocalDate.of(2030, 6, 4))
                                .status(MembershipStatus.ACTIVE)
                                .build());
        mvc.perform(
                        post("/api/memberships/upgrade/" + member.getId() + "/" + next.getId())
                                .header("Authorization", "Bearer " + jwt.generateToken(member))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"membershipId\":"
                                                + current.getId()
                                                + ",\"expectedEndDate\":\"2030-06-04\"}"))
                .andExpect(status().isOk());
        ActiveMembership upgraded =
                memberships.findByUser(member).stream()
                        .filter(item -> item.getPlan().getId().equals(next.getId()))
                        .findFirst()
                        .orElseThrow();
        assertThat(upgraded.getStartDate()).isEqualTo(LocalDate.of(2030, 6, 3));
        assertThat(upgraded.getStartDate()).isBefore(upgraded.getEndDate());
        assertThat(upgraded.getEndDate()).isEqualTo(LocalDate.of(2030, 6, 4));
    }
}
