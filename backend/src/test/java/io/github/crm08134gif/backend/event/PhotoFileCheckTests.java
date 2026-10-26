package io.github.crm08134gif.backend.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

// 사진 파일과 photos.csv 줄을 맞춰 보는 검사. DB 없이 돈다
class PhotoFileCheckTests {

    @Test
    void passesWhenRowsAndFilesMatch() {
        assertThatCode(() -> EventLoader.checkPhotoFiles(Set.of("a.jpg"), Set.of("a.jpg")))
            .doesNotThrowAnyException();
    }

    @Test
    void rejectsFileWithoutRow() {
        assertThatThrownBy(() -> EventLoader.checkPhotoFiles(Set.of(), Set.of("a.jpg")))
            .hasMessageContaining("photos.csv에 없는 사진 파일");
    }

    @Test
    void rejectsRowWithoutFile() {
        assertThatThrownBy(() -> EventLoader.checkPhotoFiles(Set.of("a.jpg"), Set.of()))
            .hasMessageContaining("사진 파일이 없다");
    }
}
