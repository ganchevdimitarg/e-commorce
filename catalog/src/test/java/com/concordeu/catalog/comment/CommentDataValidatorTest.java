package com.concordeu.catalog.comment;

import com.concordeu.catalog.dto.CommentDto;
import com.concordeu.catalog.validator.CommentDataValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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
        CommentDto commentDto = CommentDto.builder()
                .title("tttt")
                .text("ttttttttttt")
                .build();

        assertThat(testService.validateData(commentDto)).isTrue();
    }

    @Test
    void validateDataShouldThrowExceptionIfTitleIsEmpty() {
        CommentDto commentDto = CommentDto.builder()
                .title("")
                .text("ttttttttttt")
                .build();

        assertThatThrownBy(() -> testService.validateData(commentDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The title is not correct!");
    }

    @Test
    void validateDataShouldThrowExceptionIfTitleLengthIsLessThanThree() {
        CommentDto commentDto = CommentDto.builder()
                .title("tt")
                .text("ttttttttttt")
                .build();

        assertThatThrownBy(() -> testService.validateData(commentDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The title is not correct!");
    }

    @Test
    void validateDataShouldThrowExceptionIfTitleLengthIsGreaterThanFifteen() {
        CommentDto commentDto = CommentDto.builder()
                .title("tttttttttttttttttttttttttttttttt")
                .text("ttttttttttt")
                .build();

        assertThatThrownBy(() -> testService.validateData(commentDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The title is not correct!");
    }

    @Test
    void validateDataShouldThrowExceptionIfTextLengthIsLessThanTen() {
        CommentDto commentDto = CommentDto.builder()
                .title("tttt")
                .text("ttttttt")
                .build();

        assertThatThrownBy(() -> testService.validateData(commentDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The text is not correct!");
    }

    @Test
    void validateDataShouldThrowExceptionIfTextLengthIsGreaterThanOneHundredFifty() {
        CommentDto commentDto = CommentDto.builder()
                .title("tttt")
                .text("tttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttt")
                .build();

        assertThatThrownBy(() -> testService.validateData(commentDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The text is not correct!");
    }
}