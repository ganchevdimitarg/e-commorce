package com.concordeu.catalog.comment;

import lombok.*;

import javax.validation.constraints.Size;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class CommentDto {
    @Size(min = 3, max = 15)
    private String title;
    @Size(min = 10, max = 150)
    private String text;
    private double star;
    private String author;
}
