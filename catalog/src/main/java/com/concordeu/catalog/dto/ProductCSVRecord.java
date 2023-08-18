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
    private String category;
    @CsvBindByName
    private String subcategory;
    @CsvBindByName
    private String name;
    @CsvBindByName
    private BigDecimal currentPrice;
    @CsvBindByName
    private BigDecimal rawPrice;
    @CsvBindByName
    private String currency;
    @CsvBindByName
    private Long discount;
    @CsvBindByName
    private Long likesCount;
    @CsvBindByName
    private Boolean isNew;
    @CsvBindByName
    private String brand;
    @CsvBindByName
    private String brandUrl;
    @CsvBindByName
    private String codCountry;
    @CsvBindByName(column = "variation_0_color")
    private String variationColor0;
    @CsvBindByName(column = "variation_1_color")
    private String variationColor1;
    @CsvBindByName(column = "variation_0_thumbnail")
    private String variationThumbnail0;
    @CsvBindByName(column = "variation_1_thumbnail")
    private String variationThumbnail1;
    @CsvBindByName(column = "variation_0_image")
    private String variationImage0;
    @CsvBindByName(column = "variation_1_image")
    private String variationImage1;
    @CsvBindByName(column = "image_url")
    private String imageUrl;
    @CsvBindByName
    private String url;
    @CsvBindByName
    private String model;
}
