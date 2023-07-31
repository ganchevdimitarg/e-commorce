package com.concordeu.catalog.mapper;

import com.concordeu.catalog.dto.CommentDTO;
import com.concordeu.catalog.entities.Comment;
import org.mapstruct.Mapper;

import java.util.Set;

@Mapper
public interface CommentMapper {
    Comment mapCommentDTOToComment(CommentDTO commentDTO);
    CommentDTO mapCommentToCommentDTO(Comment comment);
    Set<Comment> mapCommentDTOToComment(Set<CommentDTO> commentDTO);
    Set<CommentDTO> mapCommentToCommentDTO(Set<Comment> comments);
}
