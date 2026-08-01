package com.staging.sg.acquiring.service;

import com.staging.sg.common.ecommerce.EcommerceNetworkRoute;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EcommerceRouteResolverTest {
    @Test
    void resolvesLocalIssuingFromTheServerPosBinTable() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet row = mock(ResultSet.class);
        when(row.getString("bin_from")).thenReturn("532196");
        when(row.getString("bin_to")).thenReturn("532196");
        when(row.getString("interface_code")).thenReturn("00000");
        when(jdbc.query(anyString(),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Object>>any()))
                .thenAnswer(call -> List.of(call.<org.springframework.jdbc.core.RowMapper<?>>getArgument(1)
                        .mapRow(row, 0)));

        EcommerceNetworkRoute route = new EcommerceRouteResolver(jdbc).resolve(
                "5321969999999999", EcommerceNetworkRoute.AUTO);

        assertThat(route).isEqualTo(EcommerceNetworkRoute.LOCAL_ISSUING);
    }

    @Test
    void rejectsAClientRouteThatContradictsTheBinTable() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet row = mock(ResultSet.class);
        when(row.getString("bin_from")).thenReturn("532196");
        when(row.getString("bin_to")).thenReturn("532196");
        when(row.getString("interface_code")).thenReturn("00000");
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenAnswer(call -> List.of(call.<org.springframework.jdbc.core.RowMapper<?>>getArgument(1)
                        .mapRow(row, 0)));

        assertThatThrownBy(() -> new EcommerceRouteResolver(jdbc).resolve(
                "5321969999999999", EcommerceNetworkRoute.SWAM))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authoritative BIN route");
    }
}
