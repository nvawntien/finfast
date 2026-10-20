package com.finfast.common.money.json;

import com.finfast.common.error.GlobalExceptionHandler;
import com.finfast.common.money.VndAmount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VndAmountJsonTests.FixtureController.class)
@Import({GlobalExceptionHandler.class, VndAmountJsonTests.FixtureController.class})
class VndAmountJsonTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper mapper;

    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "200000", "9223372036854775807"})
    void onlyMoneyUsesDecimalStringsWhileOrdinaryLongsRemainNumbers(String decimal) throws Exception {
        String payload = "{\"amountVnd\":\"" + decimal + "\",\"count\":7}";
        mvc.perform(post("/test-only/money").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()).andExpect(content().json(payload, JsonCompareMode.STRICT));
        var tree = mapper.readTree(mapper.writeValueAsString(new Input(VndAmount.parse(decimal), 7)));
        assertThat(tree.get("amountVnd").isString()).isTrue();
        assertThat(tree.get("amountVnd").asString()).isEqualTo(decimal);
        assertThat(tree.get("count").isIntegralNumber()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"200000", "1.0", "1e3", "-1", "true", "{}", "[]", "null",
            "\"\"", "\"-1\"", "\"+1\"", "\"01\"", "\"1.0\"", "\"1e3\"", "\" 100\"", "\"100 \"",
            "\"9223372036854775808\"", "\"private-test-canary\""})
    void malformedOrNonStringMoneyProducesOnlySafeNonfinal400(String jsonValue) throws Exception {
        mvc.perform(post("/test-only/money").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountVnd\":" + jsonValue + ",\"count\":7}"))
                .andExpect(status().isBadRequest()).andExpect(content().json("""
                        {"code":"INVALID_REQUEST","message":"The request is invalid."}
                        """, JsonCompareMode.STRICT));
    }

    @Test
    void requiredMoneyCannotBeOmitted() throws Exception {
        mvc.perform(post("/test-only/money").contentType(MediaType.APPLICATION_JSON).content("{\"count\":7}"))
                .andExpect(status().isBadRequest()).andExpect(content().json("""
                        {"code":"INVALID_REQUEST","message":"The request is invalid."}
                        """, JsonCompareMode.STRICT));
    }

    @RestController
    static class FixtureController {
        @PostMapping("/test-only/money")
        Input echo(@Valid @RequestBody Input input) {
            return input;
        }
    }

    record Input(@NotNull VndAmount amountVnd, long count) {
    }
}
