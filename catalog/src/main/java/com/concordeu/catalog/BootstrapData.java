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
        loadCsvProductData();
    }

    private void loadCategories() {
        categoryRepository.saveAllAndFlush(List.of(
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

    private void loadCsvProductData() throws FileNotFoundException {
        if (productRepository.count() == 0) {
            File accessoriesFile = ResourceUtils.getFile("classpath:csvdata/accessories.csv");
            File bagsFile = ResourceUtils.getFile("classpath:csvdata/bags.csv");
            File beautyFile = ResourceUtils.getFile("classpath:csvdata/beauty.csv");
            File jewelryFile = ResourceUtils.getFile("classpath:csvdata/jewelry.csv");
            File kidsFile = ResourceUtils.getFile("classpath:csvdata/kids.csv");
            File menFile = ResourceUtils.getFile("classpath:csvdata/men.csv");
            File shoesFile = ResourceUtils.getFile("classpath:csvdata/shoes.csv");
            File womenFile = ResourceUtils.getFile("classpath:csvdata/women.csv");

            List<ProductCSVRecord> accessoriesProducts = getProductList(accessoriesFile);
            List<ProductCSVRecord> bagsProducts = getProductList(bagsFile);
            List<ProductCSVRecord> beautyProducts = getProductList(beautyFile);
            List<ProductCSVRecord> jewelryProducts = getProductList(jewelryFile);
            List<ProductCSVRecord> kidsProducts = getProductList(kidsFile);
            List<ProductCSVRecord> menProducts = getProductList(menFile);
            List<ProductCSVRecord> shoesProducts = getProductList(shoesFile);
            List<ProductCSVRecord> womenProducts = getProductList(womenFile);

            Category accessories = getCategory(ACCESSORIES);
            Category bags = getCategory(BAGS);
            Category beauty = getCategory(BEAUTY);
            Category jewelry = getCategory(JEWELRY);
            Category kids = getCategory(KIDS);
            Category men = getCategory(MEN);
            Category shoes = getCategory(SHOES);
            Category women = getCategory(WOMEN);

            initCsvRecord(accessoriesProducts, accessories);
            initCsvRecord(bagsProducts, bags);
            initCsvRecord(beautyProducts, beauty);
            initCsvRecord(jewelryProducts, jewelry);
            initCsvRecord(kidsProducts, kids);
            initCsvRecord(menProducts, men);
            initCsvRecord(shoesProducts, shoes);
            initCsvRecord(womenProducts, women);

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
                category.setProducts(product);
                productRepository.saveAndFlush(product);
            }
        });
    }

}