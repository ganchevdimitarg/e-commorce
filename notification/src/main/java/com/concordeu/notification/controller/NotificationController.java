package com.concordeu.notification.controller;

import com.concordeu.notification.annotation.ValidationInputRequest;
import com.concordeu.notification.dto.NotificationDto;
import com.concordeu.notification.service.EmailService;
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
@RequestMapping("/api/v1/notification")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {
    private final EmailService emailService;

    @Operation(summary = "Sent Email", description = "Sent email without attachment",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PostMapping("/sendMail")
    @ValidationInputRequest
    public String sendMail(@RequestBody NotificationDto notificationDto) {
        return emailService.sendSimpleMail(notificationDto);
    }

    @Operation(summary = "Sent Email With Attachment", description = "Sent email with attachment",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PostMapping("/sendMailWithAttachment")
    @ValidationInputRequest
    public String sendMailWithAttachment(@RequestBody NotificationDto notificationDto) {
        return emailService.sendMailWithAttachment(notificationDto);
    }
}
