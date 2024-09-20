package com.example.med.utility;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.validation.ValidationException;

import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

@Service
public class JsonValidator {
	
	public void validateJsonInput(JSONObject input) throws IOException {
		InputStream inputStream = getClass().getResourceAsStream("/JsonSchemaFinal.json");
	    BufferedReader streamReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
	    StringBuilder responseStrBuilder = new StringBuilder();

	    String inputStr;
	    while ((inputStr = streamReader.readLine()) != null)
	        responseStrBuilder.append(inputStr);

	    JSONObject jsonObject = new JSONObject(responseStrBuilder.toString());
	    
        Schema schema = SchemaLoader.load(jsonObject);
        
        try {
            schema.validate(input);
        } catch (Exception e) {
        	throw new ValidationException();
		}
	}
}
