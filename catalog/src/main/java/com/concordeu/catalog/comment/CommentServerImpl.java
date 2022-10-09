package com.concordeu.catalog.comment;

import com.concordeu.catalog.product.Product;
import com.concordeu.catalog.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentServerImpl implements CommentServer {

    private final CommentRepository commentRepository;
    private final ProductRepository productRepository;
    private final CommentDataValidator commentDataValidator;

    @Override
    public Optional<CommentDto> createComment(CommentDto commentDto, String productName) {
        return Optional.empty();
    }

    @Override
    public List<Comment> findAllByProduct(Product product) {
        return null;
    }

    @Override
    public List<Comment> findAllByAuthor(String author) {
        return null;
    }
}

