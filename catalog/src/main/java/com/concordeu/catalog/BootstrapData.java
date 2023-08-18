package com.concordeu.catalog;

import com.concordeu.catalog.dto.ProductCSVRecord;
import com.concordeu.catalog.entities.Category;
import com.concordeu.catalog.entities.Product;
import com.concordeu.catalog.repositories.CategoryRepository;
import com.concordeu.catalog.repositories.ProductRepository;
import com.concordeu.catalog.service.product.ProductCsvService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BootstrapData implements CommandLineRunner {
    public static final String ACCESSORIES = "accessories";
    public static final String BAGS = "bags";
    public static final String BEAUTY = "beauty";
    public static final String JEWELRY = "jewelry";
    public static final String KIDS = "kids";
    public static final String MEN = "men";
    public static final String SHOES = "shoes";
    public static final String WOMEN = "women";

    @Getter
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductCsvService productCsvService;

    @Transactional
    @Override
    public void run(String... args) throws Exception {
        loadCategories();
        loadCsvProductData(ACCESSORIES);
        loadCsvProductData(BAGS);
        loadCsvProductData(BEAUTY);
        loadCsvProductData(JEWELRY);
        loadCsvProductData(KIDS);
        loadCsvProductData(MEN);
        loadCsvProductData(SHOES);
        loadCsvProductData(WOMEN);
    }

    private void loadCategories() {
        if (categoryRepository.count() == 0) {
            categoryRepository.saveAll(List.of(
                    Category.builder().name(ACCESSORIES).build(),
                    Category.builder().name(BAGS).build(),
                    Category.builder().name(BEAUTY).build(),
                    Category.builder().name(JEWELRY).build(),
                    Category.builder().name(KIDS).build(),
                    Category.builder().name(MEN).build(),
                    Category.builder().name(SHOES).build(),
                    Category.builder().name(WOMEN).build())
            );
        }
    }

    private void loadCsvProductData(String categoryName) throws FileNotFoundException {
        if (productRepository.count() == 0) {
            File productsFile = ResourceUtils.getFile("classpath:csvdata/" + categoryName + ".csv");

            List<ProductCSVRecord> products = getProductList(productsFile);

            Category category = getCategory(categoryName);

            initCsvRecord(products, category);
        }
    }

    private Category getCategory(String category) {
        return categoryRepository.findByName(category).get();
    }

    private List<ProductCSVRecord> getProductList(File accessoriesFile) {
        return productCsvService.convertCSV(accessoriesFile).stream().limit(500).toList();
    }

    private void initCsvRecord(List<ProductCSVRecord> records, Category category) {
        records.forEach(record -> {
            Product product = Product.builder()
                    .name(record.getName().length() > 99 ? record.getName().substring(0, 98) : record.getName().trim())
                    .description("Lorem Ipsum is simply dummy text of the printing")
                    .category(category)
                    .price(record.getCurrentPrice() == null ? BigDecimal.ZERO : record.getCurrentPrice())
                    .inStock(true)
                    .build();
            if (productRepository.findByName(product.getName()).isEmpty()) {
                productRepository.save(product);
            }
        });
    }

}