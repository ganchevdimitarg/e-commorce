package com.concordeu.catalog.dto;

import lombok.*;

import javax.validation.constraints.Size;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class CommentRequestDto {
    private String title;
    private String text;
    private double star;
    private String author;
}
