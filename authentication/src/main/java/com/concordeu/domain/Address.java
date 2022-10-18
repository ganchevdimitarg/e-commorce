package com.concordeu.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Address {
    private String id;
    private String city;
    private String street;
    private String postCode;
}
