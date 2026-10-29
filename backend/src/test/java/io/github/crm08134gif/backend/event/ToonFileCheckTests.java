package io.github.crm08134gif.backend.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

// 사건이 쓰는 웹툰 그림과 data/toons/의 파일을 맞춰 보는 검사. DB 없이 돈다
class ToonFileCheckTests {

    @Test
    void passesWhenUsedAndFilesMatch() {
        assertThatCode(() -> EventLoader.checkToonFiles(Set.of("a.jpg"), Set.of("a.jpg")))
            .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingFile() {
        assertThatThrownBy(() -> EventLoader.checkToonFiles(Set.of("a.jpg"), Set.of()))
            .hasMessageContaining("data/toons/에 없다");
    }

    @Test
    void rejectsUnusedFile() {
        assertThatThrownBy(() -> EventLoader.checkToonFiles(Set.of(), Set.of("a.jpg")))
            .hasMessageContaining("쓰지 않는 웹툰 그림");
    }
}
