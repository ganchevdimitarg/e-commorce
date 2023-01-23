package com.concordeu.notification.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.concordeu.notification.annotation.ValidationRequest;
import com.concordeu.notification.domain.Notification;
import com.concordeu.notification.dto.NotificationDto;
import com.concordeu.notification.service.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/notification")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {
	private final NotificationService notificationService;

	@Operation(summary = "View all notifications", description = "View all notifications",
			   security = @SecurityRequirement(name = "security_auth"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Success", content = {
					@Content(mediaType = "application/json") }),
			@ApiResponse(responseCode = "401", description = "Unauthenticated"),
			@ApiResponse(responseCode = "403", description = "Unauthorized"),
			@ApiResponse(responseCode = "500", description = "Server Error")
	})
	@GetMapping("/")
	@ValidationRequest
	@PreAuthorize("hasAuthority('SCOPE_admin:read')")
	public List<Notification> getAllNotifications() {
		return notificationService.getAll();
	}

	@Operation(summary = "View notifications for user id", description = "View notifications for user id",
			   security = @SecurityRequirement(name = "security_auth"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Success", content = {
					@Content(mediaType = "application/json") }),
			@ApiResponse(responseCode = "401", description = "Unauthenticated"),
			@ApiResponse(responseCode = "403", description = "Unauthorized"),
			@ApiResponse(responseCode = "500", description = "Server Error")
	})
	@GetMapping("/{user-id}")
	@ValidationRequest
	public List<Notification> getNotificationsForUserId(@PathVariable("user-id") final String userId) {
		return notificationService.getNotificationsForUser(userId);
	}

	@Operation(summary = "Create notification", description = "Create notification",
			   security = @SecurityRequirement(name = "security_auth"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Success", content = {
					@Content(mediaType = "application/json") }),
			@ApiResponse(responseCode = "401", description = "Unauthenticated"),
			@ApiResponse(responseCode = "403", description = "Unauthorized"),
			@ApiResponse(responseCode = "500", description = "Server Error")
	})
	@PostMapping("/")
	@ValidationRequest
	@PreAuthorize("hasAuthority('SCOPE_notification.write')")
	public String sendMail(@RequestBody NotificationDto notificationDto) {
		return notificationService.createNotification(notificationDto);
	}

	@Operation(summary = "Change notification status", description = "Change notification status",
			   security = @SecurityRequirement(name = "security_auth"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Success", content = {
					@Content(mediaType = "application/json") }),
			@ApiResponse(responseCode = "401", description = "Unauthenticated"),
			@ApiResponse(responseCode = "403", description = "Unauthorized"),
			@ApiResponse(responseCode = "500", description = "Server Error")
	})
	@PatchMapping("/{notification-id}")
	@ValidationRequest
	public String changeStatusOfNotification(@PathVariable("notification-id") final String notificationId) {
		return notificationService.updateStatusOfNotification(notificationId);
	}

	@Operation(summary = "Change notification status", description = "Change notification status",
			   security = @SecurityRequirement(name = "security_auth"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Success", content = {
					@Content(mediaType = "application/json") }),
			@ApiResponse(responseCode = "401", description = "Unauthenticated"),
			@ApiResponse(responseCode = "403", description = "Unauthorized"),
			@ApiResponse(responseCode = "500", description = "Server Error")
	})
	@DeleteMapping("/{notification-id}")
	@ValidationRequest
	public String deleteNotification(@PathVariable("notification-id") final String notificationId) {
		return notificationService.deleteNotification(notificationId);
	}

}
