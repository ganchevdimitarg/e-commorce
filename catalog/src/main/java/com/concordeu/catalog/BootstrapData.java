package com.concordeu.catalog;

import com.concordeu.catalog.dto.ProductCSVRecord;
import com.concordeu.catalog.entities.Category;
import com.concordeu.catalog.entities.Product;
import com.concordeu.catalog.repositories.CategoryRepository;
import com.concordeu.catalog.repositories.ProductRepository;
import com.concordeu.catalog.service.product.ProductCsvService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BootstrapData implements CommandLineRunner {
    public static final String TV_AUDIO_CAMERAS = "tv, audio & cameras";
    public static final String KIDS = "kids' fashion";
    public static final String SPORTS_FITNESS_OUTDOORS = "sports & fitness";

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductCsvService productCsvService;

    @Transactional
    @Override
    public void run(String... args) throws Exception {
        if (categoryRepository.count() == 0) {
            categoryRepository.saveAll(List.of(
                    Category.builder().name(TV_AUDIO_CAMERAS).build(),
                    Category.builder().name(SPORTS_FITNESS_OUTDOORS).build(),
                    Category.builder().name(KIDS).build())
            );
            productRepository.saveAllAndFlush(loadCsvProductData());
        }
    }

    private List<Product> loadCsvProductData() throws FileNotFoundException {
        File productsFile = ResourceUtils.getFile("classpath:csvdata/Products.csv");

        List<ProductCSVRecord> products = getProductList(productsFile);

        return initCsvRecord(products);
    }

    private List<ProductCSVRecord> getProductList(File accessoriesFile) {
        return productCsvService.convertCSV(accessoriesFile).stream().toList();
    }

    private List<Product> initCsvRecord(List<ProductCSVRecord> records) {
        return records.stream().map(record -> Product.builder()
                .name(record.getName().trim())
                .description("Lorem Ipsum is simply dummy text of the printing")
                .characteristics("Lorem Ipsum is simply")
                .category(categoryRepository.findByName(record.getMainCategory()).get())
                .price(record.getActualPrice().isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(Double.parseDouble(record.getActualPrice().replace(",", ""))))
                .inStock(true)
                .build())
                .collect(Collectors.toList());
    }
}