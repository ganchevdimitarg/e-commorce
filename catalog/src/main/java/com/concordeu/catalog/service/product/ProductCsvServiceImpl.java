package com.concordeu.catalog.service.product;

import com.concordeu.catalog.dto.ProductCSVRecord;
import com.opencsv.bean.CsvToBeanBuilder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.List;

@Service
public class ProductCsvServiceImpl implements ProductCsvService {
    @Override
    public List<ProductCSVRecord> convertCSV(File csvFile) {
        try {
            return new CsvToBeanBuilder<ProductCSVRecord>(new FileReader(csvFile))
                    .withType(ProductCSVRecord.class)
                    .build().parse();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
