package com.concordeu.catalog.service.product;

import com.concordeu.catalog.dto.ProductCSVRecord;

import java.io.File;
import java.util.List;

public interface ProductCsvService {
    List<ProductCSVRecord> convertCSV(File csvFile);
}
