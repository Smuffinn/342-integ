package com.tabungar.oauthcontacts.Controller;

import com.tabungar.oauthcontacts.Service.GoogleContactsService;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contacts")
public class GoogleContactsController {

    private final GoogleContactsService googleContactsService;

    public GoogleContactsController(GoogleContactsService googleContactsService) {
        this.googleContactsService = googleContactsService;
    }

    // READ FUNCTIONALITY
    @GetMapping
    public ResponseEntity<String> getAllContacts(@AuthenticationPrincipal OAuth2User principal) {
        try {
            String contacts = googleContactsService.getContacts(principal.getName());
            return ResponseEntity.ok(contacts);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error fetching contacts: " + e.getMessage());
        }
    }

    // CREATE FUNCTIONALITY
    @PostMapping
    public ResponseEntity<String> createContact(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody String contactJson) {
        try {
            String result = googleContactsService.createContacts(principal.getName(), contactJson);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error creating contact: " + e.getMessage());
        }
    }

    // UPDATE FUNCTIONALITY
    @PatchMapping("/{resourceName}")
    public ResponseEntity<String> updateContact(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable String resourceName, 
            @RequestBody String contactJson) {
        try {
            String updatedContact = googleContactsService.updateContact(resourceName, contactJson);
            return ResponseEntity.ok(updatedContact);
        } catch (IOException e) {
            String errorMessage = "Error updating contact: " + e.getMessage();
            if (e.getCause() != null) {
                errorMessage += " | Cause: " + e.getCause().getMessage();
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorMessage);
        }
    }

    // DELETE FUNCTIONALITY
    @DeleteMapping("/{resourceName}")
    public ResponseEntity<String> deleteContact(@PathVariable String resourceName) {
        try {
            googleContactsService.deleteContact(resourceName);
            return ResponseEntity.ok("Contact deleted successfully");
        } catch (IOException e) {
            String errorMessage = "Error deleting contact: " + e.getMessage();
            if (e.getCause() != null) {
                errorMessage += " | Cause: " + e.getCause().getMessage();
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorMessage);
        }
    }
}
