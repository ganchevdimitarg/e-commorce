package com.concordeu.controller;

import com.concordeu.dto.AuthUserDto;
import com.concordeu.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

import java.security.Principal;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping("/user/api/v1")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final AuthService authService;

    @GetMapping("/get")
    public String getString(Principal principal){
        return String.format("You did it USER: %s -> \n%s!!!", principal.getName(), principal);
    }

    @PostMapping(value = "/register",
            consumes = APPLICATION_JSON_VALUE,
            headers = "Accept=application/json")
    public AuthUserDto registerUser(
            @Valid @RequestBody AuthUserDto requestDto,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            bindingResult.getAllErrors().forEach(e -> log.error(e.getDefaultMessage()));
            throw new IllegalArgumentException("Incorrect data");
        }

        return authService.createUser(requestDto);
    }

   /* @PostMapping("/create-comment/{productName}")
    @PreAuthorize("hasRole('ROLE_USER')")
    public CommentDto createComment(@RequestBody CommentRequestDto requestDto, @PathVariable String productName) {
        CommentDto commentDto = mapper.mapCommentRequestDtoToCommentDto(requestDto);
        validator.validateData(commentDto);
        return commentService.createComment(commentDto, productName);
    }*/


}
