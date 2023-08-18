package com.concordeu.catalog.product;


import com.concordeu.catalog.dto.ProductCSVRecord;
import com.concordeu.catalog.service.product.ProductCsvService;
import com.concordeu.catalog.service.product.ProductCsvServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ProductCsvServiceImplTest {

    ProductCsvService service = new ProductCsvServiceImpl();

    @Test
    void convertCSV() throws FileNotFoundException {

        File file = ResourceUtils.getFile("classpath:csvdata/bags.csv");

        List<ProductCSVRecord> recs = service.convertCSV(file);

        System.out.println(recs.size());

        assertThat(recs.size()).isGreaterThan(0);
    }
}
