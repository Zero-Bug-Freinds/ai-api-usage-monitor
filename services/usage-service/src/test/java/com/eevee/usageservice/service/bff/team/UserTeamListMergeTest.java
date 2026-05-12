package com.eevee.usageservice.service.bff.team;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserTeamListMergeTest {

    @Test
    void union_keepsPrimaryOrder_andAddsOnlyNewIdsFromFallback() {
        TeamSummaryClientItem a = new TeamSummaryClientItem("1", "Alpha", (String) null);
        TeamSummaryClientItem b = new TeamSummaryClientItem("2", "Beta", (String) null);
        TeamSummaryClientItem dupOfA = new TeamSummaryClientItem("1", "Other", (String) null);

        List<TeamSummaryClientItem> merged = UserTeamListMerge.unionByTeamId(
                List.of(a),
                List.of(b, dupOfA)
        );

        assertThat(merged).extracting(TeamSummaryClientItem::id).containsExactly("1", "2");
        assertThat(merged.getFirst().name()).isEqualTo("Alpha");
    }

    @Test
    void union_emptyFallback_returnsPrimary() {
        TeamSummaryClientItem a = new TeamSummaryClientItem("9", "Solo", (String) null);
        assertThat(UserTeamListMerge.unionByTeamId(List.of(a), List.of())).containsExactly(a);
    }
}
