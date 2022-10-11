package com.concordeu.catalog.comment;

import com.concordeu.catalog.ModelMapper;
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
    private final ModelMapper modelMapper;

    @Override
    public CommentDto createComment(CommentDto commentDto, String productName) {
        commentDataValidator.isValidComment(commentDto);

        Product product = productRepository.findByName(productName)
                .orElseThrow(() -> new IllegalArgumentException("No such user: " + productName));

        Comment comment = modelMapper.mapDtoToComment(commentDto);
        comment.setProduct(product);

        commentRepository.saveAndFlush(comment);
        log.info("The comment "+ comment.getTitle() + " is save successful");

        return modelMapper.mapCommentToDto(comment);
    }

    @Override
    public List<CommentDto> findAllByProductName(String productName) {
        return null;
    }

    @Override
    public List<CommentDto> findAllByAuthor(String author) {
        return null;
    }
}

