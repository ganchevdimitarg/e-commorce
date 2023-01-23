package com.concordeu.mail.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.concordeu.mail.annotation.ValidationRequest;
import com.concordeu.mail.dto.MailDto;
import com.concordeu.mail.dto.MailListDto;
import com.concordeu.mail.service.MailListService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/mail-list")
@RequiredArgsConstructor
@Slf4j
public class MailListController {

	private final MailListService mailListService;


	@Operation(summary = "Save mail list user", description = "Create new mail list user",
			   security = @SecurityRequirement(name = "security_auth"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
			@ApiResponse(responseCode = "500", description = "Server Error")
	})
	@PostMapping("/")
	@ValidationRequest
	public String saveMailList(@RequestBody MailListDto mailListDto) {
		return mailListService.save(mailListDto);
	}

	@Operation(summary = "Update mail list", description = "Update the mail list user preferences",
			   security = @SecurityRequirement(name = "security_auth"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
			@ApiResponse(responseCode = "500", description = "Server Error")
	})
	@PutMapping("/")
	@ValidationRequest
	public String updateMailList(@RequestBody MailListDto mailListDto) {
		return mailListService.update(mailListDto);
	}

	@Operation(summary = "Delete mail list", description = "Delete user from the mail list",
			   security = @SecurityRequirement(name = "security_auth"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
			@ApiResponse(responseCode = "500", description = "Server Error")
	})

	@DeleteMapping("/{userId}")
	@ValidationRequest
	public String updateMailList(@PathVariable final String userId) {
		return mailListService.delete(userId);
	}



}
