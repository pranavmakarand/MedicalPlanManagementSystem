package com.example.med.dao;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import javax.validation.Valid;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.ObjectMapper;

import redis.clients.jedis.Jedis;

@Repository
public class MedicalPlanDao {
	
	static Jedis jedis = new Jedis("redis://localhost:6379");
	
	public boolean checkForDuplicatePlans(String key) {
		if (!jedis.exists(key)) {
			return false;
		}
		return true;
	}
	
	public static void savePlan(JSONObject object) {

		depthFirstKeyRetrieval(object);
	}
	
	public void saveEtag(String parentKey, String eTag) {
		jedis.hset(parentKey, "eTag", eTag);
	}
	
	public String getEtag(String key) {
		
		return jedis.hget(key, "eTag");
	}
	
	private static String depthFirstKeyRetrieval(JSONObject jsonObject) {
		
		String objectKey = jsonObject.get("objectType") + "_" + jsonObject.get("objectId"); //we created an id  : plan_508
		 
		for (String key : jsonObject.keySet()) {
			if (jsonObject.get(key) instanceof JSONObject) {
				String childKey = depthFirstKeyRetrieval((JSONObject) jsonObject.get(key)); //member_cost_share_501
				//we need to add in the set of the original key
				jedis.sadd(objectKey + "_" + key , childKey);
			} else if (jsonObject.get(key) instanceof JSONArray) {
				JSONArray arr = (JSONArray) jsonObject.get(key);
				for (int i = 0; i < arr.length(); i++) {
					JSONObject objectJson = arr.getJSONObject(i);		
					String childKey = depthFirstKeyRetrieval(objectJson);
					jedis.sadd(objectKey + "_" + key , childKey);
				}
			} else {
				jedis.hset(objectKey, key, jsonObject.get(key).toString());
				
			}
		}
		
		return objectKey;
	}

	public static void deletePlan(String key) {

		Set<String> keys = jedis.keys("*");

		for (String keyy : keys) {
			if (key.equals(keyy) || keyy.contains(key)) {	
				if (jedis.type(keyy).equals("hash")) {
					jedis.del(keyy);
				} else if (jedis.type(keyy).equals("set")) {
					Set<String> set = jedis.smembers(keyy);
					for (String members : set) {
						deletePlan(members);
					}
					jedis.del(keyy);
				}
			}
		}
	}
	
	public void updatePlan(String key) {

		Set<String> keys = jedis.keys("*");

		for (String keyy : keys) {
			if (key.equals(keyy) || keyy.contains(key)) {	
				if (jedis.type(keyy).equals("hash")) {
					jedis.del(keyy);
				} else if (jedis.type(keyy).equals("set")) {
					Set<String> set = jedis.smembers(keyy);
					for (String members : set) {
						deletePlan(members);
					}
					jedis.del(keyy);
				}
			}
		}
	}
	
	public static JSONObject getPlan(String key) {
		
		JSONObject object = new JSONObject();
		
//		JSONObject object2 =  new JSONObject(jedis.get(key));
		
//		System.out.println(object2);
		
		Set<String> keys = jedis.keys("*");
		for (String keyy : keys) {
			if (key.equals(keyy) || keyy.contains(key)) {
				if (jedis.type(keyy).equals("hash")) {
					Map<String, String> map = jedis.hgetAll(keyy);
					for (Map.Entry<String, String> entry : map.entrySet()) {
						if (entry.getKey().equals("eTag")) {
							continue;
						}
						if (entry.getValue().matches("-?\\d+")) {
							object.put(entry.getKey(), Integer.parseInt(entry.getValue()));
						} else {
							object.put(entry.getKey(), entry.getValue());
						}
					}
				} else if (jedis.type(keyy).equals("set")) {
					Set<String> set = jedis.smembers(keyy);
					if (keyy.equals("plan_12xvxc345ssdsds-508_linkedPlanServices")) {
//						List<JSONObject> lister = new ArrayList<JSONObject>();
						JSONObject returnedObject = null;
						JSONArray array = new JSONArray();
						for (String members : set) {
							returnedObject = getPlan(members);
							array.put(returnedObject);
						}
						String[] childkeyArr = keyy.split("_");
						JSONArray newArray =  new JSONArray();
						for (int i = array.length()-1 ; i >= 0; i--) {
							newArray.put(array.get(i));
						}

						object.put(childkeyArr[childkeyArr.length-1], newArray);
					} else {
						JSONObject returnedObject = null;
						for (String members : set) {
							returnedObject = getPlan(members);
						}
						String[] childkeyArr = keyy.split("_");
						object.put(childkeyArr[childkeyArr.length-1], returnedObject);
					}
				}
			}
		}

		return object;
	}

	public static void patchPlan(String ID, @Valid String modifiedPlan) {
		//delete the original plan completly
	    //update the new plan
		
		//we need to know what all needs to be updated
		JSONObject jsonObjectNew = new JSONObject(modifiedPlan);
		
		HashMap<String, HashSet<Map.Entry<String, Object>>> objectKeysValues = new HashMap<String, HashSet<Map.Entry<String, Object>>>();

		HashSet<Entry<String, Object>> keyValueSet = new HashSet<Map.Entry<String, Object>>();
		
		Iterator<String> setter = jsonObjectNew.keys(); // "planCostShares"

		String objectKey = (String) jsonObjectNew.get("objectType") + "_" + (String) jsonObjectNew.get("objectId");
		
		while (setter.hasNext()) {

			String key = setter.next();

			if (jsonObjectNew.get(key) instanceof JSONObject) {
				// 2 keys are involved in this situation
				// let us create the main key

//		    	String plantCostShareObjectKey = objectKey + "_" + key; //main key not needed in this case.

				Iterator<String> plantCostShareObjectIterator = ((JSONObject) jsonObjectNew.get(key)).keys(); // "planCostShares"

				JSONObject plantCostShareObject = (JSONObject) jsonObjectNew.get(key);

				String memberCostShareObjectKey = plantCostShareObject.get("objectType") + "_"
						+ (String) plantCostShareObject.get("objectId");

				HashMap<String, HashSet<Map.Entry<String, Object>>> plantCostShareObjectKeysValues = new HashMap<String, HashSet<Map.Entry<String, Object>>>();

				HashSet<Map.Entry<String, Object>> planCostSharekeyValueSet = new HashSet<Map.Entry<String, Object>>();

				while (plantCostShareObjectIterator.hasNext()) {

					String plantCostShareObjectKeys = plantCostShareObjectIterator.next();

					Map.Entry<String, Object> newEntry = new AbstractMap.SimpleEntry<String, Object>(
						plantCostShareObjectKeys, plantCostShareObject.get(plantCostShareObjectKeys));

					planCostSharekeyValueSet.add(newEntry);
					plantCostShareObjectKeysValues.put(memberCostShareObjectKey, planCostSharekeyValueSet);
				}

				updateTheParentNode("JSONObject", plantCostShareObjectKeysValues, memberCostShareObjectKey, null);
				// Do something with jsonObject here
			} else if (jsonObjectNew.get(key) instanceof JSONArray) {

				List<HashMap<String, HashSet<Map.Entry<String, String>>>> listOflinkedPlanServiceObjectKeysValues = new ArrayList<HashMap<String, HashSet<Map.Entry<String, String>>>>();

				JSONArray linkedPlanServicesArray = ((JSONArray) jsonObjectNew.get(key)); // "planCostShares"

				Iterator<Object> linkedPlanServicesIterator = linkedPlanServicesArray.iterator();

				while (linkedPlanServicesIterator.hasNext()) {

					JSONObject linkedPlanServiceObject = (JSONObject) linkedPlanServicesIterator.next();
					System.out.println(linkedPlanServiceObject);
					String linkedPlanServiceObjectKey = linkedPlanServiceObject.get("objectType") + "_"
							+ (String) linkedPlanServiceObject.get("objectId"); // planservice_508

					HashMap<String, HashSet<Map.Entry<String, String>>> linkedPlanServiceObjectKeysValues = new HashMap<String, HashSet<Map.Entry<String, String>>>();

					HashSet<Map.Entry<String, String>> linkedPlanServicekeyValueSet = new HashSet<Map.Entry<String, String>>();

					Iterator<String> planServicesIterator = linkedPlanServiceObject.keys(); // "planCostShares"

					while (planServicesIterator.hasNext()) { // we are iterating k-v pairs of the linkedserviceplan
																// object
						String planServiceObjectKeys = planServicesIterator.next();

						Map.Entry<String, String> newEntry = new AbstractMap.SimpleEntry<String, String>(
							planServiceObjectKeys,
							(String) linkedPlanServiceObject.get(planServiceObjectKeys).toString());

						linkedPlanServicekeyValueSet.add(newEntry);
						linkedPlanServiceObjectKeysValues.put(linkedPlanServiceObjectKey, linkedPlanServicekeyValueSet);
					}
					listOflinkedPlanServiceObjectKeysValues.add(linkedPlanServiceObjectKeysValues);
				}
				updateTheParentNode("JSONArray", null, null, listOflinkedPlanServiceObjectKeysValues);
			} else {
				// basically it is key values
				// we need to create a key first that will help know what to update
				Entry<String, Object> newEntry = new AbstractMap.SimpleEntry<String, Object>(key,
					(String) jsonObjectNew.get(key));
				keyValueSet.add(newEntry);
				objectKeysValues.put(objectKey, keyValueSet);
				updateTheParentNode("HashValues", objectKeysValues, objectKey, null);
			}
		}
	}
	
	private static void updateTheParentNode(String typeOfData,
			HashMap<String, HashSet<Map.Entry<String, Object>>> objectKeysValues, String objectKey,
			List<HashMap<String, HashSet<Map.Entry<String, String>>>> listOflinkedPlanServiceObjectKeysValues) {

		if (typeOfData.equals("JSONObject")) {

			for (Map.Entry<String, HashSet<Map.Entry<String, Object>>> entry : objectKeysValues.entrySet()) {

				String keyToFind = entry.getKey(); // memberCostShare_501

				HashSet<Entry<String, Object>> valueSet = entry.getValue(); // {duplicate: 100, ..}
				Map<String, String> valuesFromRedis = jedis.hgetAll(keyToFind);

				for (Entry<String, Object> valueEntry : valueSet) {
					String getKey = valueEntry.getKey();
					if (valuesFromRedis.containsKey(getKey)) {
						jedis.hset(keyToFind, getKey, valueEntry.getValue().toString());
					}
				}
//				System.out.println("New " + valuesFromRedis);
			}

		} else if (typeOfData.equals("JSONArray")) { // list of all the objects

			// first get the main key from
			Set<String> set = jedis.smembers("plan_12xvxc345ssdsds-508_linkedPlanServices");

			Iterator<HashMap<String, HashSet<Entry<String, String>>>> iterator = listOflinkedPlanServiceObjectKeysValues
					.iterator(); // all the linkedobjects

			while (iterator.hasNext()) {

				HashMap<String, HashSet<Entry<String, String>>> linkedObjectMap = iterator.next(); // will get you the
																									// first object
				// {planservice_27283xvx9sdf-508=[planserviceCostShares={"deductible":"10","_org":"example.com","copay":"175","objectId":"1234512xvc1314sdfsd-506","objectType":"membercostshare"},
				// _org=example_1.com, objectId=27283xvx9sdf-508, linkedService={"name":"well
				// baby","_org":"example.com","objectId":"1234520xvc30sfs-505","objectType":"service"},
				// objectType=planservice]}

				System.out.println(linkedObjectMap);

				for (Map.Entry<String, HashSet<Entry<String, String>>> entrySet : linkedObjectMap.entrySet()) {

					String linkedObjectKey = entrySet.getKey(); // planservice_27283xvx9sdf-508

					HashSet<Entry<String, String>> linkedObjectValue = entrySet.getValue(); // [planserviceCostShares={"deductible":"10","_org":"example.com","copay":"175","objectId":"1234512xvc1314sdfsd-506","objectType":"membercostshare"},

					// _org=example_1.com, objectId=27283xvx9sdf-508, linkedService={"name":"well
					// baby","_org":"example.com","objectId":"1234520xvc30sfs-505","objectType":"service"},
					// objectType=planservice]

					for (Map.Entry<String, String> keyValues : linkedObjectValue) {

						String keyObject = keyValues.getKey();// planserviceCostShares
						String valueObject = keyValues.getValue();

						if (keyObject.equals("planserviceCostShares") || keyObject.equals("linkedService")) {

							JSONObject object1 = new JSONObject(valueObject);

							Map<String, Object> keysObject = object1.toMap();

							// we need to create a main key like membercostshare_1234512xvc1314sdfsd-506
							
							String planserviceCostSharesKey = object1.get("objectType") + "_" + object1.get("objectId"); // membercostshare_1234512xvc1314sdfsd-506

							for (Entry<String, Object> mapping : keysObject.entrySet()) {
								jedis.hset(planserviceCostSharesKey, mapping.getKey(),  mapping.getValue().toString());
							}

							jedis.sadd(linkedObjectKey + "_" + keyObject, planserviceCostSharesKey);

						} else {
							
							jedis.hset(linkedObjectKey, keyObject, valueObject.toString()); // planservice_508_planserviceCostShares
																					// -> plan,														// planservice_508_linkedService
						}
					}
					
					jedis.sadd("plan_12xvxc345ssdsds-508_linkedPlanServices", linkedObjectKey);
				}

				if (typeOfData.equals("JSONObject")) {

					for (Map.Entry<String, HashSet<Map.Entry<String, Object>>> entry : objectKeysValues.entrySet()) {

						String keyToFind = entry.getKey(); // memberCostShare_501

						HashSet<Entry<String, Object>> valueSet = entry.getValue(); // {duplicate: 100, ..}

						Map<String, String> valuesFromRedis = jedis.hgetAll(keyToFind);

						for (Entry<String, Object> valueEntry : valueSet) {
							String getKey = valueEntry.getKey();
							if (valuesFromRedis.containsKey(getKey)) {
								jedis.hset(keyToFind, getKey, valueEntry.getValue().toString());
							}
						}
					}
				}
			}

		} else {

			for (Map.Entry<String, HashSet<Map.Entry<String, Object>>> entry : objectKeysValues.entrySet()) {

				String keyToFind = entry.getKey(); // plan_508
				HashSet<Entry<String, Object>> valueSet = entry.getValue(); // value of the hash
				Map<String, String> valuesFromRedis = jedis.hgetAll(keyToFind);

				for (Entry<String, Object> valueEntry : valueSet) {
					String getKey = valueEntry.getKey();
					if (valuesFromRedis.containsKey(getKey)) {
						jedis.hset(objectKey, getKey, valueEntry.getValue().toString());
					}
				}
			}
		}
	}
	
	public static void main(String args[]) throws FileNotFoundException, IOException, ParseException {
		
		String filePath = "C:\\Users\\natap\\OneDrive\\Northeastern University Work\\Spring 2024\\INFO7255\\Test\\zipfilee\\src\\main\\resources\\random.json";
		
		String filePath1 = "C:\\Users\\natap\\OneDrive\\Northeastern University Work\\Spring 2024\\INFO7255\\Test\\zipfilee\\src\\main\\resources\\input2.json";

		JSONParser parser = new JSONParser();

		Object obj = parser.parse(new FileReader(filePath));
		Object obj1 = parser.parse(new FileReader(filePath1));
		
		ObjectMapper objectMapper = new ObjectMapper();

		String jsonString = objectMapper.writeValueAsString(obj);
		
		String jsonString1 = objectMapper.writeValueAsString(obj1);

		JSONObject jsonObjectOld = new JSONObject(jsonString);
		
		JSONObject jsonObjectNew = new JSONObject(jsonString1);
		
		savePlan(jsonObjectOld);
		
		String asd = getPlan("plan" + "_" + "12xvxc345ssdsds-508").toString(); // all good till here
		
		patchPlan("plan" + "_" + "12xvxc345ssdsds-508", jsonObjectNew.toString());
		
		String asd1 = getPlan("plan" + "_" + "12xvxc345ssdsds-508").toString();
		
		System.out.println("\n" + asd1);
		
//		deletePlan("plan" + "_" + "12xvxc345ssdsds-508");
	}
}
