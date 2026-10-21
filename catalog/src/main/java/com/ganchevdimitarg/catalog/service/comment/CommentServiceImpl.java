package com.ganchevdimitarg.catalog.service.comment;

import com.ganchevdimitarg.catalog.cache.ProductCache;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final ProductRepository productRepository;
    private final MapStructMapper mapper;
    private final MeterRegistry meterRegistry;
    private final ProductCache productCacheEvictor;

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
        // The product is managed, so the recalculated rating is written by dirty checking
        // in this same transaction as the comment insert.
        refreshProductRating(product);
        productCacheEvictor.evict(product.getId());
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
        return productRepository.findByName(productName)
                .map(Product::getAvgStars)
                .orElse(0.0);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('SCOPE_catalog.write','ROLE_ADMIN')")
    public void deleteComment(UUID commentId, String requester, boolean admin) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment", commentId));
        if (!admin && !requester.equals(comment.getAuthor())) {
            throw new AccessDeniedException(
                    "Only the author or an administrator may delete this comment");
        }
        commentRepository.delete(comment);
        // The soft-delete SQL must run before the aggregate query, otherwise the deleted
        // comment would still count towards the product's rating.
        commentRepository.flush();
        // A soft-deleted product is hidden by @SQLRestriction, so the association can be null.
        Product product = comment.getProduct();
        if (product != null) {
            refreshProductRating(product);
            productCacheEvictor.evict(product.getId());
        }
        meterRegistry.counter("catalog.comment.deleted").increment();
        log.info("The comment {} is deleted successfully", commentId);
    }

    private void refreshProductRating(Product product) {
        CommentRepository.RatingStats stats = commentRepository.findRatingStatsByProductId(product.getId());
        product.setRatingCount(stats == null ? 0 : (int) stats.getRatingCount());
        product.setAvgStars(stats == null || stats.getAverageStars() == null
                ? 0.0 : stats.getAverageStars());
    }

    public CommentResponseDto convertComment(Comment comment) {
        return mapper.mapCommentToCommentResponseDto(comment);
    }

}
