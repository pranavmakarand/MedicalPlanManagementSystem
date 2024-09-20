package com.example.med.service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;


@Service
public class AuthorizationService {
	
	public boolean verifier(String token) {
	    try {
	        return verify(token.substring(7));
	    } catch (Exception e) {
	        System.out.println("Validation failed: " + e);
	        return false;
	    }
	}

	protected ResponseEntity<String> getCall(String url) throws RestClientException {
	    RestTemplate restTemplate = new RestTemplate();
	    return restTemplate.getForEntity(url, String.class);
	}

	public boolean verify(String token) {
	    try {
	        String url = "https://oauth2.googleapis.com/tokeninfo?access_token=" + token;
	        ResponseEntity<String> response = getCall(url);
	        return response.getStatusCode() == HttpStatus.OK;
	    } catch (RestClientException e) {
	        System.out.println("Error while verifying token: " + e);
	        return false;
	    }
	}

}
