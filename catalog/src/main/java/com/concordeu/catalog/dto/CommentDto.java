package com.concordeu.catalog.dto;

import com.concordeu.catalog.domain.Comment;
import com.concordeu.catalog.domain.Product;
import lombok.*;
import org.springframework.data.domain.Page;

import javax.validation.constraints.Size;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
@ToString
public class CommentDto {
    @Size(min = 3, max = 15)
    private String title;
    @Size(min = 10, max = 150)
    private String text;
    private double star;
    private String author;
    private Product product;

    public static CommentDto convertComment(Comment comment) {
        return CommentDto.builder()
                .title(comment.getTitle())
                .text(comment.getText())
                .author(comment.getAuthor())
                .star(comment.getStar())
                .build();
    }
}
