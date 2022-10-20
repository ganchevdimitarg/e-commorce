package com.concordeu.catalog.validator;

import com.concordeu.client.catalog.comment.CommentResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CommentDataValidator {
    public boolean validateData(CommentResponseDto commentResponseDto) {
        return isValidTitle(commentResponseDto.title())
                && isValidText(commentResponseDto.text());
    }

    private boolean isValidText(String text) {
        if (text.isEmpty()
                || text.trim().length() < 10
                || text.trim().length() > 150){
            log.error("The text is not correct!");
            throw new IllegalArgumentException("The text is not correct!");
        }
        return true;
    }

    private boolean isValidTitle(String title) {
        if (title.isEmpty()
                || title.trim().length() < 3
                || title.trim().length() > 15){
            log.error("The title is not correct!");
            throw new IllegalArgumentException("The title is not correct!");
        }
        return true;
    }
}
