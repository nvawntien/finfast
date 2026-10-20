package com.finfast.common.error;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ApplicationExceptionTests {

    @ParameterizedTest
    @EnumSource(ErrorCode.Category.class)
    void preservesCatalogFieldsAndCauseWithoutUsingItsPrivateMessage(ErrorCode.Category category) {
        SQLException cause = new SQLException("SELECT password_hash; private-test-canary");
        var exception = new ApplicationException(code("FIXTURE_ERROR", "A safe description.", category), cause);
        assertThat(exception.code()).isEqualTo("FIXTURE_ERROR");
        assertThat(exception.publicMessage()).isEqualTo("A safe description.");
        assertThat(exception.getMessage()).isEqualTo("A safe description.");
        assertThat(exception.category()).isEqualTo(category);
        assertThat(exception).hasCause(cause);
        assertThat(exception.getStackTrace()).isNotEmpty();
    }

    @Test
    void causeIsOptional() {
        assertThat(new ApplicationException(code("FIXTURE_ERROR", "Safe.", ErrorCode.Category.CONFLICT)))
                .hasNoCause();
    }

    @Test
    void rejectsInvalidCatalogDefinitions() {
        assertThatNullPointerException().isThrownBy(() -> new ApplicationException(null));
        assertThatNullPointerException().isThrownBy(() -> new ApplicationException(code("CODE", "Safe.", null)));
        for (String invalid : new String[] {null, "", " \t\n"}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new ApplicationException(
                    code(invalid, "Safe.", ErrorCode.Category.INVALID_REQUEST)));
            assertThatIllegalArgumentException().isThrownBy(() -> new ApplicationException(
                    code("CODE", invalid, ErrorCode.Category.INVALID_REQUEST)));
        }
    }

    private static ErrorCode code(String code, String message, ErrorCode.Category category) {
        return new ErrorCode() {
            public String code() { return code; }
            public String publicMessage() { return message; }
            public Category category() { return category; }
        };
    }
}
