package com.finfast;

import com.finfast.accounts.internal.adapter.outbound.persistence.JdbcAccountRepository;
import com.finfast.administration.internal.adapter.inbound.http.AdminInspectionController;
import com.finfast.administration.internal.application.port.inbound.AdminInspectionUseCase;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.finfast.accounts.internal.adapter.outbound.persistence.JdbcAccountStatusRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
// Preserve the Step 2 checks without connecting to any external database.
@ImportAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class})
class FinfastApplicationTests {

    @MockitoBean
    JdbcAccountRepository jdbcAccountRepository;

    // This context intentionally excludes DataSource/Flyway; status mutation is not under test here.
    @MockitoBean
    JdbcAccountStatusRepository jdbcAccountStatusRepository;

    @Test
    void incompleteAdminInspectionIsNotRegisteredByProductionScanning(@Autowired ApplicationContext context,
            @Autowired MockMvc mvc,
            @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings) throws Exception {
        assertThat(context.getBeansOfType(AdminInspectionController.class)).isEmpty();
        assertThat(context.getBeansOfType(AdminInspectionUseCase.class)).isEmpty();
        for (String path : java.util.List.of("/api/v1/admin/accounts", "/api/v1/admin/financial-transactions",
                "/api/v1/admin/audit-events")) {
            assertThat(mappings.getHandlerMethods().keySet())
                    .noneMatch(mapping -> mapping.getPatternValues().contains(path));
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
    }

    @Test
    void contextLoads(@Autowired ApplicationContext context) {
        assertThat(context).isNotNull();
    }

    @Test
    void healthEndpointReturnsUp(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", JsonCompareMode.STRICT));
    }

    @Test
    void prometheusEndpointIsNotExposedInOrdinaryMode(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isNotFound());
    }
}
