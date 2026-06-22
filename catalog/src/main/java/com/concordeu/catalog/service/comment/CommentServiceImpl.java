package com.concordeu.catalog.service.comment;

import com.concordeu.catalog.repository.CommentRepository;
import com.concordeu.catalog.repository.ProductRepository;
import com.concordeu.catalog.domain.Comment;
import com.concordeu.catalog.domain.Product;
import com.concordeu.catalog.dto.comment.CommentResponseDto;
import com.concordeu.catalog.exception.NotFoundException;
import com.concordeu.catalog.exception.ValidationException;
import com.concordeu.catalog.mapper.MapStructMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final ProductRepository productRepository;
    private final MapStructMapper mapper;

    @Override
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    public CommentResponseDto createComment(CommentResponseDto commentResponseDto, String productName) {
        Product product = productRepository
                .findByName(productName)
                .orElseThrow(() -> {
                    logMessage(productName);
                    return new NotFoundException("Product", productName);
                });

        Comment comment = mapper.mapCommentResponseDtoToComment(commentResponseDto);
        comment.setProduct(product);

        commentRepository.saveAndFlush(comment);
        log.info("The comment {} is save successful", comment.getTitle());

        return mapper.mapCommentToCommentResponseDto(comment);
    }

    @Override
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public Page<CommentResponseDto> findAllByProductNameByPage(String productName, int page, int size) {
        if (productName.isEmpty()) {
            logMessage(productName);
            throw new ValidationException("Product name is empty");
        }

        Product product = productRepository.findByName(productName).orElseThrow(() -> {
            logMessage(productName);
            return new NotFoundException("Product", productName);
        });

        Page<CommentResponseDto> comments = commentRepository
                .findAllByProductIdByPage(product.getId(), PageRequest.of(page, size))
                .map(this::convertComment);

        log.info("Successful get comments by product: {}", productName);

         return comments;
    }

    private static void logMessage(String productName) {
        log.warn("No such product: {}", productName);
    }

    @Override
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public Page<CommentResponseDto> findAllByAuthorByPage(String author, int page, int size) {
        if (author.isEmpty()) {
            log.warn("No such author: {}", author);
            throw new ValidationException("No such author: " + author);
        }
        Page<CommentResponseDto> comments = commentRepository
                .findAllByAuthorByPage(author, PageRequest.of(page, size))
                .map(this::convertComment);
        log.info("Successful get comments by author: {}", author);

        return comments;
    }

    @Override
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public double getAvgStars(String productName) {
        List<Comment> comments = commentRepository.findAllByProductName(productName);
        double sum = 0.0;
        for (Comment comment : comments) {
            sum += comment.getStar();
        }

        return sum / comments.size();
    }

    public CommentResponseDto convertComment(Comment comment) {
        return new CommentResponseDto(
                comment.getTitle(),
                comment.getText(),
                comment.getStar(),
                comment.getAuthor(),
                mapper.mapProductToProductRequestDto(comment.getProduct()));
    }

}

