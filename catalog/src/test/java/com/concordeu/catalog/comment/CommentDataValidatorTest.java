package com.concordeu.catalog.comment;

import com.concordeu.catalog.dto.comment.CommentResponseDto;
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
        CommentResponseDto commentResponseDto = new CommentResponseDto("tttt", "ttttttttttt", 0, "", null);

        assertThat(testService.validateData(commentResponseDto)).isTrue();
    }

    @Test
    void validateDataShouldThrowExceptionIfTitleIsEmpty() {
        CommentResponseDto commentResponseDto = new CommentResponseDto("", "ttttttttttt", 0, "", null);

        assertThatThrownBy(() -> testService.validateData(commentResponseDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The title is not correct!");
    }

    @Test
    void validateDataShouldThrowExceptionIfTitleLengthIsLessThanThree() {
        CommentResponseDto commentResponseDto = new CommentResponseDto("tt", "ttttttttttt", 0, "", null);

        assertThatThrownBy(() -> testService.validateData(commentResponseDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The title is not correct!");
    }

    @Test
    void validateDataShouldThrowExceptionIfTitleLengthIsGreaterThanFifteen() {
        CommentResponseDto commentResponseDto = new CommentResponseDto("tttttttttttttttttttttttttttttttttttt", "ttttttttttt", 0, "", null);

        assertThatThrownBy(() -> testService.validateData(commentResponseDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The title is not correct!");
    }

    @Test
    void validateDataShouldThrowExceptionIfTextLengthIsLessThanTen() {
        CommentResponseDto commentResponseDto = new CommentResponseDto("tttt", "ttttttt", 0, "", null);

        assertThatThrownBy(() -> testService.validateData(commentResponseDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The text is not correct!");
    }

    @Test
    void validateDataShouldThrowExceptionIfTextLengthIsGreaterThanOneHundredFifty() {
        CommentResponseDto commentResponseDto =
                new CommentResponseDto("tttt", "tttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttt",
                        0, "", null);

        assertThatThrownBy(() -> testService.validateData(commentResponseDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The text is not correct!");
    }
}