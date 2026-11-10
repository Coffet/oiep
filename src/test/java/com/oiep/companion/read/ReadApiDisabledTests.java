package com.oiep.companion.read;

import com.oiep.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With the default configuration the read API must be completely absent: no controller, no
 * service, no fallback JSON handler, no database table and no SQLite trigger. This is what
 * keeps the component removable and harmless on installs that never enable it.
 */
class ReadApiDisabledTests extends IntegrationTestSupport {

    @Test
    void readApiBeansAreNotRegistered() {
        assertThat(applicationContext.getBeansOfType(ReadApiController.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(ReadApiService.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(ReadApiAuth.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(ReadApiEventStore.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(ReadApiSchemaInitializer.class)).isEmpty();
    }

    @Test
    void experiencesEndpointIsAbsentEvenWithABearerToken() throws Exception {
        mockMvc.perform(get(ReadApiController.EXPERIENCES_PATH)
                        .header("Authorization", "Bearer some-token-that-would-be-right"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownApiPathsDoNotGetTheComponentJsonFallback() throws Exception {
        mockMvc.perform(get("/api/v1/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void noComponentDatabaseObjectsAreCreated() {
        assertThat(componentDatabaseObjects()).isEmpty();
    }

    private java.util.List<String> componentDatabaseObjects() {
        return jdbcTemplate.queryForList(
                "SELECT name FROM sqlite_master WHERE name LIKE 'companion_%'", String.class);
    }
}
