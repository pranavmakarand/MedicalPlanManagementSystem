package com.example.med.controller;

import org.json.JSONObject;

import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.med.MedApplication;
import com.example.med.service.AuthorizationService;
//import com.example.med.filter.MyUserDetailsService;
//import com.example.med.service.JwtUtilsService;
import com.example.med.service.MedicalPlanServices;
import com.example.med.utility.JsonValidator;


import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.ValidationException;

@RestController
@RequestMapping(path = "/v1")
public class MedicalPlanController {


	@Autowired
	JsonValidator validator;

	@Autowired
	MedicalPlanServices services;
	
    @Autowired
    private AuthorizationService authorizationService;
    
    private static final Logger logger = LoggerFactory.getLogger(MedicalPlanController.class);

//	@Autowired
//	private MyUserDetailsService userDetailsService;
//
//	@Autowired
//	private JwtUtilsService jwtUtilsService;
	
    private final RabbitTemplate template;
    
    @Autowired
    public MedicalPlanController(RabbitTemplate template) {
    	this.template = template;   	
    }

//	@PostMapping(path = "/authenticate", produces = "application/json")
//	public ResponseEntity<Object> generateAuthenticationToken(@RequestBody String authRequest) throws Exception {
//
//		System.out.println("In");
//
//		JSONObject jsonObject = new JSONObject(authRequest);
//
//		final String username = userDetailsService.loadUserByUsername((String) jsonObject.get("username"));
//
//		final String password = (String) jsonObject.get("password");
//
//		if (username == null || !userDetailsService.validatePassword(password)) {
//			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
//				.body(new JSONObject().put("Error", "User Authentication failed").toString());
//		}
//
//		final String token = jwtUtilsService.generateToken(username);
//
//		return ResponseEntity.status(HttpStatus.OK).body(new AuthResponse(token));
//	}

//	@PostMapping("/test")
//	public ResponseEntity<String> validToken(HttpServletRequest request) {
//
//		String authorizationHeader = request.getHeader("Authorization");
//
//		String token = null;
//
//		if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
//			token = authorizationHeader.substring(7);
//		}
//
//		try {
//
//			jwtUtilsService.validate(token);
//
//		} catch (Exception e) {
//			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
//				.body(new JSONObject().put("Error", "Token Validation failed").toString());
//		}
//
//		return ResponseEntity.ok().body("Token Verified");
//	}

	@PostMapping(path = "/plan", produces = "application/json")
	public ResponseEntity<Object> postPlan(@Valid @RequestBody(required = false) String inputPlan,
			HttpServletRequest request, HttpServletResponse response) throws IOException, ParseException, GeneralSecurityException {

		String authorizationHeader = request.getHeader("Authorization");
		if (authorizationHeader == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new JSONObject().put("error", "Please pass the Access Token").toString());
		}

		if (!authorizationService.verifier(authorizationHeader)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new JSONObject().put("error", "Invalid Access Token").toString());
		}
		
		

		if (inputPlan == null || inputPlan.isBlank()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new JSONObject().put("Error", "Body is empty please provide an appropriate JSON").toString());
		}

		JSONObject jsonObject = new JSONObject(inputPlan);

		try {
			validator.validateJsonInput(jsonObject);
		} catch (ValidationException ex) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new JSONObject()
				.put("Error", "Invalid JSON Schema!!, please enter a valid Json Schema").toString());
		}

		String key = jsonObject.get("objectType").toString() + "_" + jsonObject.get("objectId").toString();

		if (services.checkForDuplicateKey(key)) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new JSONObject().put("Message", "Plan Already Exists").toString());
		}

		services.savePlan(jsonObject);

		String getPlan = services.getPlan(key);

		String etag = services.generateEtag(getPlan);

		services.saveEtag(key, etag);
		
		response.addHeader("etag", etag);
		
		
        Map<String, String> message = new HashMap<String, String>();
        message.put("operation", "SAVE");
        message.put("body", inputPlan);

        System.out.println("Sending message: " + message);
        
        template.convertAndSend(MedApplication.queueName, message);

		return ResponseEntity.ok().body(getPlan);
	}

	@GetMapping(path = "/{type}/{ID}", produces = "application/json")
	public ResponseEntity<Object> getPlan(@PathVariable String ID, @PathVariable String type,
			@RequestHeader HttpHeaders headers, HttpServletRequest request) throws IOException {

		String authorizationHeader = request.getHeader("Authorization");
		if (authorizationHeader == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new JSONObject().put("error", "Please pass the Access Token").toString());
		}

		if (!authorizationService.verifier(authorizationHeader)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new JSONObject().put("error", "Invalid Access Token").toString());
		}
		
		

		if (!services.checkForDuplicateKey("plan" + "_" + ID)) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new JSONObject().put("Error", "No Key with Input ID found").toString());
		}

		if (type.equals("plan")) {

			String eTagFromRedis = services.getEtag(type + "_" + ID);
			String eTagFromHeader = headers.getFirst("If-None-Match");
			if (eTagFromRedis.equals(eTagFromHeader)) {
				return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(eTagFromRedis).build();
			}
		}

		String retrievedPlan = services.getPlan(type + "_" + ID);

		return ResponseEntity.ok().body(retrievedPlan);
	}

	@DeleteMapping(path = "/plan/{ID}", produces = "application/json")
	public ResponseEntity<Object> deletePlan(@PathVariable String ID, HttpServletRequest request) throws IOException {

		String authorizationHeader = request.getHeader("Authorization");
		if (authorizationHeader == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new JSONObject().put("error", "Please pass the Access Token").toString());
		}

		if (!authorizationService.verifier(authorizationHeader)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new JSONObject().put("error", "Invalid Access Token").toString());
		}

		if (!services.checkForDuplicateKey("plan" + "_" + ID)) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new JSONObject().put("Error", "No Key with Input ID found").toString());
		}
		
		
		
		
        String plan = services.getPlan("plan" + "_" + ID);
        
		String generateEtag = services.generateEtag(plan);

		System.out.println(generateEtag);

		String ifMatch = request.getHeader("If-Match");

		if (!ifMatch.equals(generateEtag) || ifMatch.isEmpty() || ifMatch == null) {
			return new ResponseEntity<Object>("{\"message\": \"No Data Found\" }", HttpStatus.PRECONDITION_FAILED);
		}
        
		services.deletePlan("plan" + "_" + ID);
		
        Map<String, String> message = new HashMap<String, String>();
        message.put("operation", "DELETE");
        message.put("body",  plan);

        System.out.println("Sending message: " + message);
        template.convertAndSend(MedApplication.queueName, message);

		return ResponseEntity.noContent().build();
	}

	@PatchMapping(path = "/plan/{ID}", produces = "application/json")
	public ResponseEntity<Object> patchPlan(@Valid @RequestBody(required = true) String patchPlan,
			@PathVariable String ID, @RequestHeader HttpHeaders headers, HttpServletRequest request, HttpServletResponse response)
			throws IOException {

		String authorizationHeader = request.getHeader("Authorization");
		if (authorizationHeader == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new JSONObject().put("error", "Please pass the Access Token").toString());
		}

		if (!authorizationService.verifier(authorizationHeader)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new JSONObject().put("error", "Invalid Access Token").toString());
		}
		
		

		String internalID = "plan" + "_" + ID;
		
		String value = services.getPlan(internalID);

		if (patchPlan == null || patchPlan.isBlank()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new JSONObject().put("Error", "Body is empty please provide an appropriate JSON").toString());
		}

		String generateEtag = services.generateEtag(value);

		System.out.println(generateEtag);

		String ifMatch = headers.getFirst("If-Match");

		if (!ifMatch.equals(generateEtag) || ifMatch.isEmpty() || ifMatch == null) {
			return new ResponseEntity<Object>("{\"message\": \"No Data Found\" }", HttpStatus.PRECONDITION_FAILED);
		}

		if (!services.checkForDuplicateKey("plan" + "_" + ID)) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new JSONObject().put("Error", "No Key with Input ID found").toString());
		}

		services.patchPlan("plan" + "_" + ID, patchPlan);

		String retrievedPlan = services.getPlan("plan" + "_" + ID);

		String etag = services.generateEtag(retrievedPlan);

		services.saveEtag("plan" + "_" + ID, etag);
		
		response.addHeader("etag", etag);
		
        Map<String, String> message1 = new HashMap<String, String>();
        message1.put("operation", "SAVE");
        message1.put("body", patchPlan);

        System.out.println("Sending message: " + message1);
        
        template.convertAndSend(MedApplication.queueName, message1);

		return ResponseEntity.ok().body(retrievedPlan);
	}

	@PutMapping(path = "/plan/{ID}", produces = "application/json")
	public ResponseEntity<Object> updatePlan(@Valid @RequestBody(required = true) String modifiedPlan,
		@PathVariable String ID, @RequestHeader HttpHeaders headers, HttpServletRequest request, HttpServletResponse response)
		throws IOException {

		String authorizationHeader = request.getHeader("Authorization");
		if (authorizationHeader == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new JSONObject().put("error", "Please pass the Access Token").toString());
		}

		if (!authorizationService.verifier(authorizationHeader)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new JSONObject().put("error", "Invalid Access Token").toString());
		}
		
		

		String internalID = "plan" + "_" + ID;
		
		String value = services.getPlan(internalID);

		if (modifiedPlan == null || modifiedPlan.isBlank()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(new JSONObject().put("Error", "Body is empty please provide an appropriate JSON").toString());
		}

		String generateEtag = services.generateEtag(value);

		System.out.println(generateEtag);

		String ifMatch = headers.getFirst("If-Match");

		if (!ifMatch.equals(generateEtag) || ifMatch.isEmpty() || ifMatch == null) {
			return new ResponseEntity<Object>("{\"message\": \"No Data Found\" }", HttpStatus.PRECONDITION_FAILED);
		}
		
        String plan = services.getPlan("plan" + "_" + ID);

		services.deletePlan("plan" + "_" + ID);
		
//        Map<String, String> message = new HashMap<String, String>();
//        message.put("operation", "DELETE");
//        message.put("body",  plan);
//
//        System.out.println("Sending message: " + message);
//        template.convertAndSend(DemoApplication.queueName, message);

		JSONObject jsonObject = new JSONObject(modifiedPlan);

		try {
			validator.validateJsonInput(jsonObject);
		} catch (ValidationException ex) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject()
				.put("Error", "Invalid JSON Schema!!, please enter a valid Json Schema").toString());
		}

		String key = jsonObject.get("objectType").toString() + "_" + jsonObject.get("objectId").toString();

		services.savePlan(jsonObject);

		String getPlan = services.getPlan(key);

		String etag = services.generateEtag(getPlan);

		services.saveEtag(key, etag);
		
		response.addHeader("etag", etag);
		
//        Map<String, String> message1 = new HashMap<String, String>();
//        message1.put("operation", "SAVE");
//        message1.put("body", getPlan);
//
//        System.out.println("Sending message: " + message1);
//        
//        template.convertAndSend(DemoApplication.queueName, message1);

		return ResponseEntity.ok().body(modifiedPlan);
	}
}