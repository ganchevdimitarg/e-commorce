package com.concordeu.mail.controller;

import com.concordeu.mail.annotation.ValidationRequest;
import com.concordeu.mail.dto.MailDto;
import com.concordeu.mail.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1/mail")
@RequiredArgsConstructor
@Slf4j
public class MailController {
    private final EmailService emailService;

    @Operation(summary = "Sent Email", description = "Sent email without attachment",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PostMapping("/send-email")
    @ValidationRequest
    public String sendMail(@RequestBody MailDto mailDto) {
        return emailService.sendSimpleMail(mailDto);
    }

    @Operation(summary = "Sent Email With Attachment", description = "Sent email with attachment",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PostMapping("/sendMailWithAttachment")
    @ValidationRequest
    public String sendMailWithAttachment(@RequestBody MailDto mailDto) {
        return emailService.sendMailWithAttachment(mailDto);
    }
	@PostMapping("/sendMailHTML")
	@ValidationRequest
	public String sendMailWithHTML(@RequestBody MailDto mailDto) {
		return emailService.sendMailWithHTML(mailDto);
	}


}
