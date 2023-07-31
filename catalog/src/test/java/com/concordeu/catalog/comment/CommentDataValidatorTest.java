package com.concordeu.catalog.comment;

import com.concordeu.catalog.dto.CommentDTO;
import com.concordeu.catalog.validator.CommentDataValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@Tag("unit")
class CommentDataValidatorTest {
    CommentDataValidator testService;

    @BeforeEach
    void setUp() {
        testService = new CommentDataValidator();
    }

    @Test
    void validateDataShouldReturnTrue() {
        CommentDTO commentDTO = CommentDTO.builder()
                .title("tttt")
                .text("ttttttttttt")
                .star(Double.MIN_VALUE)
                .build();

        assertThat(testService.validateData(commentDTO)).isTrue();
    }

    @Test
    void validateDataShouldThrowExceptionIfTitleIsEmpty() {
        CommentDTO commentDTO = CommentDTO.builder()
                .title("")
                .text("tttttttttttt")
                .build();

        assertThatThrownBy(() -> testService.validateData(commentDTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The title is not correct!");
    }

    @Test
    void validateDataShouldThrowExceptionIfTitleLengthIsLessThanThree() {
        CommentDTO commentDTO = CommentDTO.builder()
                .title("tt")
                .text("ttttttttttt")
                .star(Double.MIN_VALUE)
                .build();

        assertThatThrownBy(() -> testService.validateData(commentDTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The title is not correct!");
    }

    @Test
    void validateDataShouldThrowExceptionIfTitleLengthIsGreaterThanFifteen() {
        CommentDTO commentDTO = CommentDTO.builder()
                .title("tttttttttttttttttttttttttttttttttttt")
                .text("ttttttttttt")
                .star(Double.MIN_VALUE)
                .build();
        assertThatThrownBy(() -> testService.validateData(commentDTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The title is not correct!");
    }

    @Test
    void validateDataShouldThrowExceptionIfTextLengthIsLessThanTen() {
        CommentDTO commentDTO = CommentDTO.builder()
                .title("tttt")
                .text("ttttttt")
                .star(Double.MIN_VALUE)
                .build();

        assertThatThrownBy(() -> testService.validateData(commentDTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The text is not correct!");
    }

    @Test
    void validateDataShouldThrowExceptionIfTextLengthIsGreaterThanOneHundredFifty() {
        CommentDTO commentDTO = CommentDTO.builder()
                .title("tttt")
                .text("tttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttt")
                .star(Double.MIN_VALUE)
                .build();
        assertThatThrownBy(() -> testService.validateData(commentDTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The text is not correct!");
    }
}