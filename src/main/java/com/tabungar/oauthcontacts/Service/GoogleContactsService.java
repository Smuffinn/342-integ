package com.tabungar.oauthcontacts.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.*;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;

import com.google.api.services.people.v1.PeopleService;
import com.google.api.services.people.v1.model.*;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import org.springframework.security.core.Authentication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

@Service    
public class GoogleContactsService {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final RestTemplate restTemplate;
    private static final String PEOPLE_API_BASE_URL = "https://people.googleapis.com/v1";
    private final ObjectMapper objectMapper;

    public GoogleContactsService(OAuth2AuthorizedClientService authorizedClientService) {
        this.authorizedClientService = authorizedClientService;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    private String getAccessToken(String userName) {    
        OAuth2AuthorizedClient client = authorizedClientService
                .loadAuthorizedClient("google", userName);
        if (client == null || client.getAccessToken() == null) {
            throw new RuntimeException("No valid access token found");
        }
        return client.getAccessToken().getTokenValue();
    }

    private String getAccessToken() {
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
            OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                    oauthToken.getAuthorizedClientRegistrationId(),
                    oauthToken.getName()
            );
            if (client != null && client.getAccessToken() != null) {
                return client.getAccessToken().getTokenValue();
            }
        }
        throw new RuntimeException("OAuth2 authentication failed!");
    }

    private PeopleService getPeopleService() {
        String accessToken = getAccessToken();
        GoogleCredential credential = new GoogleCredential().setAccessToken(accessToken);
        return new PeopleService.Builder(
            new NetHttpTransport(), 
            JacksonFactory.getDefaultInstance(), 
            credential)
            .setApplicationName("Google Contacts Integration")
            .build();
    }

    // READ FUNCTIONALITY
    public String getContacts(String userName) {
        String url = PEOPLE_API_BASE_URL + "/people/me/connections"
                   + "?personFields=names,emailAddresses,phoneNumbers,photos";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken(userName));
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url, 
                HttpMethod.GET, 
                request, 
                String.class
            );
            return response.getBody();
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Error fetching contacts: " + e.getResponseBodyAsString(), e);
        }
    }

    // CREATE FUNCTIONALITY
    public String createContacts(String userName, String contactJson) {
        String url = PEOPLE_API_BASE_URL + "/people:createContact";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(getAccessToken(userName));
        
        HttpEntity<String> request = new HttpEntity<>(contactJson, headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                String.class
            );
            return response.getBody();
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Error creating contact: " + e.getResponseBodyAsString(), e);
        }
    }

    // UPDATE FUNCTIONALITY
    public String updateContact(String resourceName, String contactJson) throws IOException {
        try {
            // Parse the contact JSON to extract fields we need to update
            JsonNode contactNode = objectMapper.readTree(contactJson);
            
            // Ensure resourceName starts with "people/"
            if (!resourceName.startsWith("people/")) {
                resourceName = "people/" + resourceName;
            }

            // First, get the existing contact
            PeopleService peopleService = getPeopleService();
            Person existingContact = peopleService.people()
                .get(resourceName)
                .setPersonFields("names,emailAddresses,phoneNumbers")
                .execute();
            
            // Update the contact with new values from contactJson
            // Names
            if (contactNode.has("names") && contactNode.get("names").isArray() && contactNode.get("names").size() > 0) {
                List<Name> names = new ArrayList<>();
                JsonNode namesNode = contactNode.get("names").get(0);
                if (namesNode.has("givenName")) {
                    Name name = new Name().setGivenName(namesNode.get("givenName").asText());
                    names.add(name);
                    existingContact.setNames(names);
                }
            }
            
            // Email addresses
            if (contactNode.has("emailAddresses") && contactNode.get("emailAddresses").isArray()) {
                List<EmailAddress> emailAddresses = new ArrayList<>();
                for (JsonNode emailNode : contactNode.get("emailAddresses")) {
                    if (emailNode.has("value")) {
                        EmailAddress email = new EmailAddress().setValue(emailNode.get("value").asText());
                        emailAddresses.add(email);
                    }
                }
                if (!emailAddresses.isEmpty()) {
                    existingContact.setEmailAddresses(emailAddresses);
                }
            }
            
            // Phone numbers
            if (contactNode.has("phoneNumbers") && contactNode.get("phoneNumbers").isArray()) {
                List<PhoneNumber> phoneNumbers = new ArrayList<>();
                for (JsonNode phoneNode : contactNode.get("phoneNumbers")) {
                    if (phoneNode.has("value")) {
                        PhoneNumber phone = new PhoneNumber().setValue(phoneNode.get("value").asText());
                        phoneNumbers.add(phone);
                    }
                }
                if (!phoneNumbers.isEmpty()) {
                    existingContact.setPhoneNumbers(phoneNumbers);
                }
            }
            
            // Create the update mask for the fields we're updating
            String updatePersonFields = "";
            if (contactNode.has("names")) updatePersonFields += "names,";
            if (contactNode.has("emailAddresses")) updatePersonFields += "emailAddresses,";
            if (contactNode.has("phoneNumbers")) updatePersonFields += "phoneNumbers,";
            // Remove trailing comma if present
            if (updatePersonFields.endsWith(",")) {
                updatePersonFields = updatePersonFields.substring(0, updatePersonFields.length() - 1);
            }
            
            // Use the PeopleService client to update the contact
            Person updatedContact = peopleService.people()
                .updateContact(existingContact.getResourceName(), existingContact)
                .setUpdatePersonFields(updatePersonFields)
                .execute();

            // Convert the updated contact back to JSON
            return objectMapper.writeValueAsString(updatedContact);
        } catch (Exception e) {
            throw new IOException("Error updating contact: " + e.getMessage(), e);
        }
    }

    // DELETE FUNCTIONALITY
    public void deleteContact(String resourceName) throws IOException {
        // Remove any 'people/' prefix if it exists as it's already part of the API URL
        String cleanResourceName = resourceName.replace("people/", "");
        String url = PEOPLE_API_BASE_URL + "/people/" + cleanResourceName + ":deleteContact";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.DELETE,
                request,
                String.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IOException("Failed to delete contact. Status: " + response.getStatusCode() + 
                    ", Response: " + response.getBody());
            }
        } catch (HttpClientErrorException e) {
            String errorDetails = String.format(
                "Failed to delete contact. Status: %s, Response: %s",
                e.getStatusCode(),
                e.getResponseBodyAsString()
            );
            throw new IOException(errorDetails, e);
        } catch (Exception e) {
            throw new IOException("Unexpected error while deleting contact: " + e.getMessage(), e);
        }
    }
}
