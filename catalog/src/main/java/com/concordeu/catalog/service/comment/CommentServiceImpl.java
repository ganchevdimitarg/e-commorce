package com.concordeu.catalog.service.comment;

import com.concordeu.catalog.MapStructMapper;
import com.concordeu.catalog.validator.CommentDataValidator;
import com.concordeu.catalog.dto.CommentDto;
import com.concordeu.catalog.dao.CommentDao;
import com.concordeu.catalog.domain.Comment;
import com.concordeu.catalog.domain.Product;
import com.concordeu.catalog.dao.ProductDao;
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

    private final CommentDao commentDao;
    private final ProductDao productDao;
    private final CommentDataValidator commentDataValidator;
    private final MapStructMapper mapStructMapper;

    @Override
    public CommentDto createComment(CommentDto commentDto, String productName) {
        commentDataValidator.validateData(commentDto);

        Product product = productDao
                .findByName(productName)
                .orElseThrow(() -> {
                    log.error("No such product: " + productName);
                    return new IllegalArgumentException("No such product: " + productName);
                });

        Comment comment = mapStructMapper.mapDtoToComment(commentDto);
        comment.setProduct(product);

        commentDao.saveAndFlush(comment);
        log.info("The comment " + comment.getTitle() + " is save successful");

        return mapStructMapper.mapCommentToDto(comment);
    }

    @Override
    public Page<CommentDto> findAllByProductNameByPage(String productName, int page, int size) {
        if (productName.isEmpty()) {
            log.error("No such product: " + productName);
            throw new IllegalArgumentException("No such product: " + productName);
        }

        Product product = productDao.findByName(productName).orElseThrow(() -> {
            log.error("No such product: " + productName);
            throw new IllegalArgumentException("No such product: " + productName);
        });

        Page<CommentDto> comments = commentDao
                .findAllByProductIdByPage(product.getId(), PageRequest.of(page, size))
                .map(CommentDto::convertComment);

        log.info("Successful get comments by product: " + productName);

         return comments;
    }

    @Override
    public Page<CommentDto> findAllByAuthorByPage(String author, int page, int size) {
        if (author.isEmpty()) {
            log.error("No such author: " + author);
            throw new IllegalArgumentException("No such author: " + author);
        }
        Page<CommentDto> comments = commentDao
                .findAllByAuthorByPage(author, PageRequest.of(page, size))
                .map(CommentDto::convertComment);
        log.info("Successful get comments by author: " + author);

        return comments;
    }

    @Override
    public double getAvgStars(String productName) {
        List<Comment> comments = commentDao.findAllByProductName(productName);
        double sum = 0.0;
        for (Comment comment : comments) {
            sum += comment.getStar();
        }

        return sum / comments.size();
    }


}

