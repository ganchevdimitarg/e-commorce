package com.concordeu.catalog.comment;

import com.concordeu.catalog.product.Product;
import lombok.*;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
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
    private Product product;
}
