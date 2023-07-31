package com.concordeu.catalog.service.comment;

import com.concordeu.catalog.dto.CommentDTO;
import com.concordeu.catalog.mapper.CommentMapper;
import com.concordeu.catalog.repositories.CommentRepository;
import com.concordeu.catalog.repositories.ProductRepository;
import com.concordeu.catalog.entities.Comment;
import com.concordeu.catalog.entities.Product;
import com.concordeu.catalog.validator.CommentDataValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final ProductRepository productRepository;
    private final CommentDataValidator commentDataValidator;
    private final CommentMapper mapper;

    @Override
    public CommentDTO createComment(CommentDTO commentDTO, String productName) {
        commentDataValidator.validateData(commentDTO);

        Product product = productRepository
                .findByName(productName)
                .orElseThrow(() -> {
                    log.warn("No such product: " + productName);
                    return new IllegalArgumentException("No such product: " + productName);
                });

        Comment comment = mapper.mapCommentDTOToComment(commentDTO);
        comment.setProduct(product);

        commentRepository.saveAndFlush(comment);
        log.info("The comment " + comment.getTitle() + " is save successful");

        return mapper.mapCommentToCommentDTO(comment);
    }

    @Override
    public Page<CommentDTO> findAllByProductNameByPage(String productName, int page, int size) {
        if (productName.isEmpty()) {
            log.warn("No such product: " + productName);
            throw new IllegalArgumentException("No such product: " + productName);
        }

        Product product = productRepository.findByName(productName).orElseThrow(() -> {
            log.warn("No such product: " + productName);
            throw new IllegalArgumentException("No such product: " + productName);
        });

        Page<CommentDTO> comments = commentRepository
                .findAllByProductIdByPage(product.getId(), PageRequest.of(page, size))
                .map(this::convertComment);

        log.info("Successful get comments by product: " + productName);

         return comments;
    }

    @Override
    public Page<CommentDTO> findAllByAuthorByPage(String author, int page, int size) {
        if (author.isEmpty()) {
            log.warn("No such author: " + author);
            throw new IllegalArgumentException("No such author: " + author);
        }
        Page<CommentDTO> comments = commentRepository
                .findAllByAuthorByPage(author, PageRequest.of(page, size))
                .map(this::convertComment);
        log.info("Successful get comments by author: " + author);

        return comments;
    }

    @Override
    public double getAvgStars(String productName) {
        List<Comment> comments = commentRepository.findAllByProductName(productName);
        double sum = 0.0;
        for (Comment comment : comments) {
            sum += comment.getStar();
        }

        return sum / comments.size();
    }

    public CommentDTO convertComment(Comment comment) {
        return mapper.mapCommentToCommentDTO(comment);
    }

}

