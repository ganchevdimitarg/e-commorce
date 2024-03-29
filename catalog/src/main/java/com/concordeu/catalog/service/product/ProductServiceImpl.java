package com.concordeu.catalog.service.product;

import com.concordeu.catalog.dao.CategoryDao;
import com.concordeu.catalog.dao.ProductDao;
import com.concordeu.catalog.domain.Category;
import com.concordeu.catalog.domain.Product;
import com.concordeu.catalog.dto.product.ItemRequestDto;
import com.concordeu.catalog.dto.product.ProductResponseDto;
import com.concordeu.catalog.mapper.MapStructMapper;
import com.concordeu.catalog.validator.ProductDataValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductDao productDao;
    private final CategoryDao categoryDao;
    private final ProductDataValidator validator;
    private final MapStructMapper mapper;

    @Override
    public ProductResponseDto createProduct(ProductResponseDto productResponseDto, String categoryName) {
        validator.validateData(productResponseDto, categoryName);

        Category category = categoryDao
                .findByName(categoryName)
                .orElseThrow(() -> {
                    log.warn("No such category: " + categoryName);
                    throw new IllegalArgumentException("No such category: " + categoryName);
                });

        if (productDao.findByName(productResponseDto.name()).isPresent()) {
            log.warn("Product with the name: " + productResponseDto.name() + " already exists.");
            throw new IllegalArgumentException("Product with the name: " + productResponseDto.name() + " already exist.");
        }

        Product product = mapper.mapProductResponseDtoToProduct(productResponseDto);
        product.setCategory(category);
        product.setInStock(true);

        log.info("The product " + product.getName() + " is save successful");
        productDao.saveAndFlush(product);

        return mapper.mapProductToProductResponseDto(product);
    }

    @Override
    public Page<ProductResponseDto> getProductsByPage(int page, int size) {
        Page<ProductResponseDto> products = productDao
                .findAll(PageRequest.of(page, size))
                .map(this::convertProduct);
        log.info("Successful get products: " + products.getSize());

        return products;
    }

    @Override
    public Page<ProductResponseDto> getProductsByCategoryByPage(int page, int size, String categoryName) {
        Category category = categoryDao
                .findByName(categoryName)
                .orElseThrow(() -> new IllegalArgumentException("No such category: " + categoryName));

        Page<ProductResponseDto> products = productDao
                .findAllByCategoryIdByPage(category.getId(), PageRequest.of(page, size))
                .map(this::convertProduct);
        log.info("Successful get products by category: " + categoryName);

        return products;
    }

    @Override
    public ProductResponseDto getProductByName(String name) {
        Assert.notNull(name, "Name is empty");
        Product product = productDao.findByName(name).orElseThrow(() -> new IllegalArgumentException("No such product"));
        return mapper.mapProductToProductResponseDto(product);
    }

    @Override
    public ProductResponseDto getProductById(String id) {
        Assert.notNull(id, "Id is empty");
        Product product = productDao.findById(id).orElseThrow(() -> new IllegalArgumentException("No such product"));
        return mapper.mapProductToProductResponseDto(product);
    }


    @Override
    public void updateProduct(ProductResponseDto productResponseDto, String productName) {
        validator.validateData(productResponseDto, productName);

        checkExistenceProduct(productName);

        productDao.update(productName,
                productResponseDto.description(),
                productResponseDto.price(),
                productResponseDto.characteristics(),
                productResponseDto.inStock());
        log.info("The updates were successful on product: " + productName);
    }

    @Override
    public void deleteProduct(String productName) {
        checkExistenceProduct(productName);
        productDao.deleteByName(productName);
    }

    @Override
    public List<ProductResponseDto> getProductsById(ItemRequestDto product) {
        return product.items().stream().map(this::getProductById).collect(Collectors.toList());
    }


    private void checkExistenceProduct(String productName) {
        if (productDao.findByName(productName).isEmpty()) {
            log.warn("Product with the name: " + productName + " does not exist.");
            throw new IllegalArgumentException("Product with the name: " + productName + " does not exist.");
        }
    }

    public ProductResponseDto convertProduct(Product product){
        return new ProductResponseDto(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.isInStock(),
                product.getCharacteristics(),
                mapper.mapCategoryToCategoryResponseDto(product.getCategory()),
                mapper.mapCommentsToCommentResponseDtos(product.getComments()));
    }
}
