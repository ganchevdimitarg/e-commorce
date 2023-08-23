package com.concordeu.catalog.dto;

import com.opencsv.bean.CsvBindByName;
import lombok.*;

import java.math.BigDecimal;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Builder
public class ProductCSVRecord {
    @CsvBindByName
    private String name;
    @CsvBindByName(column = "main_category")
    private String mainCategory;
    @CsvBindByName(column = "sub_category")
    private String subCategory;
    @CsvBindByName
    private String image;
    @CsvBindByName
    private String link;
    @CsvBindByName
    private String ratings;
    @CsvBindByName(column = "no_of_ratings")
    private String noOfRatings;
    @CsvBindByName(column = "discount_price")
    private String discountPrice;
    @CsvBindByName(column = "actual_price")
    private String actualPrice;
}
