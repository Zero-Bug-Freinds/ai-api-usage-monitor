package com.eevee.billingservice.service;

import com.eevee.billingservice.api.dto.TeamMonthRollupRequest;
import com.eevee.billingservice.api.dto.TeamMonthRollupResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ExpenditureTeamRollupServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private ExpenditureTeamRollupService service;

    @Test
    void rollup_emptyUserIds_returnsZero() {
        TeamMonthRollupResponse out = service.rollup(new TeamMonthRollupRequest(List.of(), LocalDate.of(2026, 4, 1)));
        assertThat(out.totalCostUsd()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(out.byUser()).isEmpty();
    }

    @Test
    void rollup_rejectsNonFirstOfMonth() {
        assertThatThrownBy(() -> service.rollup(new TeamMonthRollupRequest(List.of("a"), LocalDate.of(2026, 4, 15))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
