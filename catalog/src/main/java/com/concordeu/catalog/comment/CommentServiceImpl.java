package com.concordeu.catalog.comment;

import com.concordeu.catalog.ModelMapper;
import com.concordeu.catalog.product.Product;
import com.concordeu.catalog.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final ProductRepository productRepository;
    private final CommentDataValidator commentDataValidator;
    private final ModelMapper modelMapper;

    @Override
    public CommentDto createComment(CommentDto commentDto, String productName) {
        commentDataValidator.validateData(commentDto);

        Product product = productRepository
                .findByName(productName)
                .orElseThrow(() -> {
                    log.error("No such product: " + productName);
                    return new IllegalArgumentException("No such product: " + productName);
                });

        Comment comment = modelMapper.mapDtoToComment(commentDto);
        comment.setProduct(product);

        commentRepository.saveAndFlush(comment);
        log.info("The comment " + comment.getTitle() + " is save successful");

        return modelMapper.mapCommentToDto(comment);
    }

    @Override
    public List<CommentDto> findAllByProductName(String productName) {
        if (productName.isEmpty()) {
            log.error("No such product: " + productName);
            throw new IllegalArgumentException("No such product: " + productName);
        }

        Product product = productRepository.findByName(productName).orElseThrow(() -> {
            log.error("No such product: " + productName);
            throw new IllegalArgumentException("No such product: " + productName);
        });

        List<Comment> allByProduct = commentRepository.findAllByProduct(product);
        List<CommentDto> commentDtos = modelMapper.mapCommentsToDtos(allByProduct);

        return commentDtos;
    }

    @Override
    public List<CommentDto> findAllByAuthor(String author) {
        if (author.isEmpty()) {
            log.error("No such author: " + author);
            throw new IllegalArgumentException("No such author: " + author);
        }

        return modelMapper.mapCommentsToDtos(commentRepository.findAllByAuthor(author));
    }

    @Override
    public double getAvgStars(String productName) {
        List<CommentDto> comments = findAllByProductName(productName);
        double sum = 0.0;
        for (CommentDto comment : comments) {
            sum += comment.getStar();
        }

        return sum / comments.size();
    }


}

