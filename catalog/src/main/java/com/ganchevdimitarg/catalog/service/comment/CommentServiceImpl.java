package com.ganchevdimitarg.catalog.service.comment;

import com.ganchevdimitarg.catalog.repository.CommentRepository;
import com.ganchevdimitarg.catalog.repository.ProductRepository;
import com.ganchevdimitarg.catalog.domain.Comment;
import com.ganchevdimitarg.catalog.domain.Product;
import com.ganchevdimitarg.catalog.dto.comment.CommentResponseDto;
import com.ganchevdimitarg.catalog.dto.comment.CreateCommentCommand;
import com.ganchevdimitarg.catalog.exception.NotFoundException;
import com.ganchevdimitarg.catalog.mapper.MapStructMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final ProductRepository productRepository;
    private final MapStructMapper mapper;
    private final MeterRegistry meterRegistry;

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    public CommentResponseDto createComment(CreateCommentCommand command) {
        Product product = productRepository.findByName(command.productName())
                .orElseThrow(() -> {
                    logMessage(command.productName());
                    return new NotFoundException("Product", command.productName());
                });
        Comment comment = mapper.mapCreateCommentCommandToComment(command);
        comment.setProduct(product);
        commentRepository.saveAndFlush(comment);
        meterRegistry.counter("catalog.comment.created").increment();
        log.info("The comment {} is saved successfully", comment.getTitle());
        return mapper.mapCommentToCommentResponseDto(comment);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public Page<CommentResponseDto> findAllByProductNameByPage(String productName, Pageable pageable) {
        Product product = productRepository.findByName(productName).orElseThrow(() -> {
            logMessage(productName);
            return new NotFoundException("Product", productName);
        });

        Page<CommentResponseDto> comments = commentRepository
                .findAllByProductIdByPage(product.getId(), pageable)
                .map(this::convertComment);

        log.info("Successful get comments by product: {}", productName);

         return comments;
    }

    private static void logMessage(String productName) {
        log.warn("No such product: {}", productName);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public Page<CommentResponseDto> findAllByAuthorByPage(String author, Pageable pageable) {
        Page<CommentResponseDto> comments = commentRepository
                .findAllByAuthorByPage(author, pageable)
                .map(this::convertComment);
        log.info("Successful get comments by author: {}", author);

        return comments;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public double getAvgStars(String productName) {
        List<Comment> comments = commentRepository.findAllByProductName(productName);
        if (comments.isEmpty()) {
            return 0.0;
        }
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
