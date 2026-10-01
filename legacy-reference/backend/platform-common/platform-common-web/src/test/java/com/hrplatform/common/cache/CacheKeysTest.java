package com.hrplatform.common.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheKeysTest {

    @Test
    void todoKeyIncludesUserAndScopeDigest() {
        assertThat(CacheKeys.todo(7L, "department:12"))
                .isEqualTo("hr:todo:user:7:"
                        + "419ea9053c9df9ee9ce641d2b33b3a851e68b346e777bd55f379b330ca232ec4");
    }

    @Test
    void blankTodoScopeUsesDeterministicNormalizedDigest() {
        assertThat(CacheKeys.todo(7L, null))
                .isEqualTo("hr:todo:user:7:"
                        + "140bedbf9c3f6d56a9846d2ba7088798683f4da0c248231336e6a05679e4fdfe");
        assertThat(CacheKeys.todo(7L, "   "))
                .isEqualTo(CacheKeys.todo(7L, null));
    }

    @Test
    void todoKeyRejectsNonPositiveUserIds() {
        assertThatThrownBy(() -> CacheKeys.todo(0L, "department:12"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CacheKeys.todo(-1L, "department:12"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
