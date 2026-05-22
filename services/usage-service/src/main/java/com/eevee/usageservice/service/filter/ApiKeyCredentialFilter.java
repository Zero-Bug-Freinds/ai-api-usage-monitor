package com.eevee.usageservice.service.filter;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolved API key filter for usage log queries: one or more legacy {@code key_id}s plus optional fingerprint.
 */
public record ApiKeyCredentialFilter(List<String> apiKeyIds, String fingerprint) {

    public static ApiKeyCredentialFilter unrestricted() {
        return new ApiKeyCredentialFilter(List.of(), null);
    }

    public static ApiKeyCredentialFilter forCredential(Collection<String> keyIds, String fingerprint) {
        Set<String> ids = new LinkedHashSet<>();
        if (keyIds != null) {
            for (String id : keyIds) {
                if (StringUtils.hasText(id)) {
                    ids.add(id.trim());
                }
            }
        }
        String fp = StringUtils.hasText(fingerprint) ? fingerprint.trim() : null;
        if (ids.isEmpty() && fp == null) {
            return unrestricted();
        }
        return new ApiKeyCredentialFilter(List.copyOf(ids), fp);
    }

    public boolean isRestricted() {
        return !apiKeyIds.isEmpty() || StringUtils.hasText(fingerprint);
    }

    /**
     * SQL fragment and positional bind values for {@code usage_recorded_log} credential scoping.
     */
    public SqlCredentialClause toLogSqlClause() {
        if (!isRestricted()) {
            return SqlCredentialClause.none();
        }
        if (apiKeyIds.size() == 1 && !StringUtils.hasText(fingerprint)) {
            String id = apiKeyIds.getFirst();
            return new SqlCredentialClause(
                    " AND (api_key_id = ? OR team_api_key_id = ?)",
                    List.of(id, id)
            );
        }
        StringBuilder sql = new StringBuilder(" AND (");
        List<Object> params = new ArrayList<>();
        boolean first = true;
        for (String id : apiKeyIds) {
            if (!first) {
                sql.append(" OR ");
            }
            sql.append("(api_key_id = ? OR team_api_key_id = ?)");
            params.add(id);
            params.add(id);
            first = false;
        }
        if (StringUtils.hasText(fingerprint)) {
            if (!first) {
                sql.append(" OR ");
            }
            sql.append("api_key_fingerprint = ?");
            params.add(fingerprint.trim());
        }
        sql.append(")");
        return new SqlCredentialClause(sql.toString(), params);
    }

    public record SqlCredentialClause(String sql, List<Object> bindValues) {
        public static SqlCredentialClause none() {
            return new SqlCredentialClause("", List.of());
        }
    }
}
