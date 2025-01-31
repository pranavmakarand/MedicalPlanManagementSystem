package com.example.med.elasticsearch;

import org.apache.http.HttpHost;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.xcontent.XContentType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

@Service
public class IndexingListener {

	private final RestHighLevelClient client;
//	private final static RestHighLevelClient client1 = new RestHighLevelClient(
//			RestClient.builder(new HttpHost("localhost", 9200, "http")));;
	private static final String INDEX_NAME = "plan-index";
	private static LinkedHashMap<String, Map<String, Object>> MapOfDocuments = new LinkedHashMap<String, Map<String, Object>>();
	private static ArrayList<String> listOfKeys = new ArrayList<String>();

	public IndexingListener(RestHighLevelClient client) {
		this.client = client;
	}

	public void receiveMessage(Map<String, String> message) throws IOException {
		System.out.println("Message received: " + message);

		String operation = message.get("operation");
		String body = message.get("body");
		JSONObject jsonBody = new JSONObject(body);

		switch (operation) {
		case "SAVE": {
			postDocument(jsonBody);
			break;
		}
		case "DELETE": {
			deleteDocument(jsonBody);
			break;
		}
		}
	}

	private boolean indexExists() throws IOException {
		GetIndexRequest request = new GetIndexRequest(INDEX_NAME);
		return client.indices().exists(request, RequestOptions.DEFAULT);
	}

	private void postDocument(JSONObject plan) throws IOException {
		if (!indexExists()) {
			createElasticIndex();
		}

		MapOfDocuments = new LinkedHashMap<String, Map<String, Object>>();
		convertMapToDocumentIndex(plan, "", "plan");

		for (Map.Entry<String, Map<String, Object>> entry : MapOfDocuments.entrySet()) {
			String parentId = entry.getKey().split(":")[0];
			String objectId = entry.getKey().split(":")[1];
			IndexRequest request = new IndexRequest(INDEX_NAME);
			request.id(objectId);
			request.source(entry.getValue());
			request.routing(parentId);
			request.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
			IndexResponse indexResponse;
			try {
				indexResponse = client.index(request, RequestOptions.DEFAULT);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
//				System.out.println("response id: " + indexResponse.getId() + " parent id: " + parentId);
			}
		}
	}

	private void deleteDocument(JSONObject jsonObject) throws IOException {
		listOfKeys = new ArrayList<String>();
		convertToKeys(jsonObject);

		for (String key : listOfKeys) {
			DeleteRequest request = new DeleteRequest(INDEX_NAME, key);
			DeleteResponse deleteResponse;
			try {
				deleteResponse = client.delete(request, RequestOptions.DEFAULT);
				if (deleteResponse.getResult() == DocWriteResponse.Result.NOT_FOUND) {
					System.out.println("Document " + key + " Not Found!!");
				}

			} catch (Exception e) {

			} finally {

			}
		}
		
		System.out.println("hello");
	}

//	public static void main(String args[]) throws FileNotFoundException, IOException, ParseException {
//
//		String filePath = "/Users/pranavdhongade/Documents/AdvancedBigDataIndex/elasticSearchDemo/src/main/resources/random.json";
//
//		JSONParser parser = new JSONParser();
//
//		Object obj = parser.parse(new FileReader(filePath));
//
//		ObjectMapper objectMapper = new ObjectMapper();
//		ObjectMapper objectMapper1 = new ObjectMapper();
//
//		String jsonString = objectMapper.writeValueAsString(obj);
//
//		listOfKeys = new ArrayList<String>();
//		convertToKeys1(new JSONObject(jsonString));
//
//		for (String key : listOfKeys) {
//			DeleteRequest request = new DeleteRequest(INDEX_NAME, key);
//			DeleteResponse deleteResponse;
//			try {
//				deleteResponse = client1.delete(request, RequestOptions.DEFAULT);
//				if (deleteResponse.getResult() == DocWriteResponse.Result.NOT_FOUND) {
//					System.out.println("Document " + key + " Not Found!!");
//				}
//
//			} catch (Exception e) {
//
//			} finally {
//
//			}
////            DeleteResponse deleteResponse = client1.delete(
////                    request, RequestOptions.DEFAULT);
//
//		}
//	}

	private Map<String, Map<String, Object>> convertToKeys(JSONObject jsonObject) {

		Map<String, Map<String, Object>> map = new HashMap<String, Map<String, Object>>();
		Map<String, Object> valueMap = new HashMap<String, Object>();

		for (String key : jsonObject.keySet()) {
			String redisKey = jsonObject.get("objectId").toString();
			Object value = jsonObject.get(key);

			if (value instanceof JSONObject) {
				convertToKeys((JSONObject) value);
			} else if (value instanceof JSONArray) {
				convertToKeysList((JSONArray) value);
			} else {
				valueMap.put(key, value);
				map.put(redisKey, valueMap);
			}
		}

		listOfKeys.add(jsonObject.get("objectId").toString());
		return map;
	}

	private static Map<String, Map<String, Object>> convertToKeys1(JSONObject jsonObject) {

		Map<String, Map<String, Object>> map = new HashMap<String, Map<String, Object>>();
		Map<String, Object> valueMap = new HashMap<String, Object>();

		for (String key : jsonObject.keySet()) {
			String redisKey = jsonObject.get("objectId").toString();
			Object value = jsonObject.get(key);

			if (value instanceof JSONObject) {
				convertToKeys1((JSONObject) value);
			} else if (value instanceof JSONArray) {
				convertToKeysList1((JSONArray) value);
			} else {
				valueMap.put(key, value);
				map.put(redisKey, valueMap);
			}
		}

		listOfKeys.add(jsonObject.get("objectId").toString());
		return map;
	}

	private List<Object> convertToKeysList(JSONArray jsonArray) {
		List<Object> list = new ArrayList<Object>();
		for (Object value : jsonArray) {
			if (value instanceof JSONArray) {
				value = convertToKeysList((JSONArray) value);
			} else if (value instanceof JSONObject) {
				value = convertToKeys((JSONObject) value);
			}
			list.add(value);
		}
		return list;
	}

	private static List<Object> convertToKeysList1(JSONArray jsonArray) {
		List<Object> list = new ArrayList<Object>();
		for (Object value : jsonArray) {
			if (value instanceof JSONArray) {
				value = convertToKeysList1((JSONArray) value);
			} else if (value instanceof JSONObject) {
				value = convertToKeys1((JSONObject) value);
			}
			list.add(value);
		}
		return list;
	}

	private Map<String, Map<String, Object>> convertMapToDocumentIndex(JSONObject jsonObject, String parentId,
			String objectName) {

		Map<String, Map<String, Object>> map = new HashMap<String, Map<String, Object>>();
		Map<String, Object> valueMap = new HashMap<String, Object>();
		Iterator<String> iterator = jsonObject.keys();

		while (iterator.hasNext()) {
			String key = iterator.next();
			String redisKey = jsonObject.get("objectType") + ":" + parentId;
			Object value = jsonObject.get(key);

			if (value instanceof JSONObject) {

				convertMapToDocumentIndex((JSONObject) value, jsonObject.get("objectId").toString(), key);

			} else if (value instanceof JSONArray) {

				convertToList((JSONArray) value, jsonObject.get("objectId").toString(), key);

			} else {
				valueMap.put(key, value);
				map.put(redisKey, valueMap);
			}
		}

		Map<String, Object> temp = new HashMap<String, Object>();
		if (objectName == "plan") {
			valueMap.put("plan_join", objectName);
		} else {
			temp.put("name", objectName);
			temp.put("parent", parentId);
			valueMap.put("plan_join", temp);
		}

		String id = parentId + ":" + jsonObject.get("objectId").toString();
		System.out.println(valueMap);
		MapOfDocuments.put(id, valueMap);

		return map;
	}

	private List<Object> convertToList(JSONArray array, String parentId, String objectName) {
		List<Object> list = new ArrayList<Object>();
		for (int i = 0; i < array.length(); i++) {
			Object value = array.get(i);
			if (value instanceof JSONArray) {
				value = convertToList((JSONArray) value, parentId, objectName);
			} else if (value instanceof JSONObject) {
				value = convertMapToDocumentIndex((JSONObject) value, parentId, objectName);
			}
			list.add(value);
		}
		return list;
	}

	private void createElasticIndex() throws IOException {
		CreateIndexRequest request = new CreateIndexRequest(INDEX_NAME);
		request.settings(Settings.builder().put("index.number_of_shards", 1).put("index.number_of_replicas", 1));

		XContentBuilder mapping = getMapping();
		request.mapping(mapping);
		CreateIndexResponse createIndexResponse = client.indices().create(request, RequestOptions.DEFAULT);

		boolean acknowledged = createIndexResponse.isAcknowledged();
		System.out.println("Index Creation:" + acknowledged);
	}

//    private static String getMapping() {
//		String mapping= "{\r\n" + 
//				"	\"properties\": {\r\n" + 
//				"		\"_org\": {\r\n" + 
//				"			\"type\": \"text\"\r\n" + 
//				"		},\r\n" + 
//				"		\"objectId\": {\r\n" + 
//				"			\"type\": \"keyword\"\r\n" + 
//				"		},\r\n" + 
//				"		\"objectType\": {\r\n" + 
//				"			\"type\": \"text\"\r\n" + 
//				"		},\r\n" + 
//				"		\"planType\": {\r\n" + 
//				"			\"type\": \"text\"\r\n" + 
//				"		},\r\n" + 
//				"		\"creationDate\": {\r\n" + 
//				"			\"type\": \"date\",\r\n" + 
//				"			\"format\" : \"MM-dd-yyyy\"\r\n" + 
//				"		},\r\n" + 
//				"		\"planCostShares\": {\r\n" +
//                "			\"type\": \"nested\",\r\n" +
//                "			\"properties\": {\r\n" +
//				"				\"copay\": {\r\n" + 
//				"					\"type\": \"long\"\r\n" + 
//				"				},\r\n" + 
//				"				\"deductible\": {\r\n" + 
//				"					\"type\": \"long\"\r\n" + 
//				"				},\r\n" + 
//				"				\"_org\": {\r\n" + 
//				"					\"type\": \"text\"\r\n" + 
//				"				},\r\n" + 
//				"				\"objectId\": {\r\n" + 
//				"					\"type\": \"keyword\"\r\n" + 
//				"				},\r\n" + 
//				"				\"objectType\": {\r\n" + 
//				"					\"type\": \"text\"\r\n" + 
//				"				}\r\n" + 
//				"			}\r\n" + 
//				"		},\r\n" + 
//				"		\"linkedPlanServices\": {\r\n" + 
//				"			\"type\": \"nested\",\r\n" + 
//				"			\"properties\": {\r\n" + 
//				"				\"_org\": {\r\n" + 
//				"					\"type\": \"text\"\r\n" + 
//				"				},\r\n" + 
//				"				\"objectId\": {\r\n" + 
//				"					\"type\": \"keyword\"\r\n" + 
//				"				},\r\n" + 
//				"				\"objectType\": {\r\n" + 
//				"					\"type\": \"text\"\r\n" + 
//				"				},\r\n" + 
//				"				\"linkedService\": {\r\n" +
//                "                   \"type\": \"nested\",\r\n" +
//                "					\"properties\": {\r\n" +
//				"						\"name\": {\r\n" + 
//				"							\"type\": \"text\"\r\n" + 
//				"						},\r\n" + 
//				"						\"_org\": {\r\n" + 
//				"							\"type\": \"text\"\r\n" + 
//				"						},\r\n" + 
//				"						\"objectId\": {\r\n" + 
//				"							\"type\": \"keyword\"\r\n" + 
//				"						},\r\n" + 
//				"						\"objectType\": {\r\n" + 
//				"							\"type\": \"text\"\r\n" + 
//				"						}\r\n" + 
//				"					}\r\n" + 
//				"				},\r\n" + 
//				"				\"planserviceCostShares\": {\r\n" +
//                "                  \"type\": \"nested\",\r\n" +
//				"					\"properties\": {\r\n" + 
//				"						\"copay\": {\r\n" + 
//				"							\"type\": \"long\"\r\n" + 
//				"						},\r\n" + 
//				"						\"deductible\": {\r\n" + 
//				"							\"type\": \"long\"\r\n" + 
//				"						},\r\n" + 
//				"						\"_org\": {\r\n" + 
//				"							\"type\": \"text\"\r\n" + 
//				"						},\r\n" + 
//				"						\"objectId\": {\r\n" + 
//				"							\"type\": \"keyword\"\r\n" + 
//				"						},\r\n" + 
//				"						\"objectType\": {\r\n" + 
//				"							\"type\": \"text\"\r\n" + 
//				"						}\r\n" + 
//				"					}\r\n" + 
//				"				}\r\n" + 
//				"			}\r\n" + 
//				"		}\r\n" + 
//				"	}\r\n" + 
//				"}";
//		
//		return mapping;
//	}

	private XContentBuilder getMapping() throws IOException {

		XContentBuilder builder = XContentFactory.jsonBuilder();
		builder.startObject();
		{
			builder.startObject("properties");
			{
				builder.startObject("plan");
				{
					builder.startObject("properties");
					{
						builder.startObject("_org");
						{
							builder.field("type", "text");
						}
						builder.endObject();
						builder.startObject("objectId");
						{
							builder.field("type", "keyword");
						}
						builder.endObject();
						builder.startObject("objectType");
						{
							builder.field("type", "text");
						}
						builder.endObject();
						builder.startObject("planType");
						{
							builder.field("type", "text");
						}
						builder.endObject();
						builder.startObject("creationDate");
						{
							builder.field("type", "date");
							builder.field("format", "MM-dd-yyyy");
						}
						builder.endObject();
					}
					builder.endObject();
				}
				builder.endObject();
				builder.startObject("planCostShares");
				{
					builder.startObject("properties");
					{
						builder.startObject("copay");
						{
							builder.field("type", "long");
						}
						builder.endObject();
						builder.startObject("deductible");
						{
							builder.field("type", "long");
						}
						builder.endObject();
						builder.startObject("_org");
						{
							builder.field("type", "text");
						}
						builder.endObject();
						builder.startObject("objectId");
						{
							builder.field("type", "keyword");
						}
						builder.endObject();
						builder.startObject("objectType");
						{
							builder.field("type", "text");
						}
						builder.endObject();
					}
					builder.endObject();
				}
				builder.endObject();
				builder.startObject("linkedPlanServices");
				{
					// builder.field("type","nested");
					builder.startObject("properties");
					{
						builder.startObject("_org");
						{
							builder.field("type", "text");
						}
						builder.endObject();
						builder.startObject("objectId");
						{
							builder.field("type", "keyword");
						}
						builder.endObject();
						builder.startObject("objectType");
						{
							builder.field("type", "text");
						}
						builder.endObject();
					}
					builder.endObject();
				}
				builder.endObject();
				builder.startObject("linkedService");
				{
					builder.startObject("properties");
					{
						builder.startObject("_org");
						{
							builder.field("type", "text");
						}
						builder.endObject();
						builder.startObject("name");
						{
							builder.field("type", "text");
						}
						builder.endObject();
						builder.startObject("objectId");
						{
							builder.field("type", "keyword");
						}
						builder.endObject();
						builder.startObject("objectType");
						{
							builder.field("type", "text");
						}
						builder.endObject();
					}
					builder.endObject();
				}
				builder.endObject();
				builder.startObject("planserviceCostShares");
				{
					builder.startObject("properties");
					{
						builder.startObject("copay");
						{
							builder.field("type", "long");
						}
						builder.endObject();
						builder.startObject("deductible");
						{
							builder.field("type", "long");
						}
						builder.endObject();
						builder.startObject("_org");
						{
							builder.field("type", "text");
						}
						builder.endObject();
						builder.startObject("objectId");
						{
							builder.field("type", "keyword");
						}
						builder.endObject();
						builder.startObject("objectType");
						{
							builder.field("type", "text");
						}
						builder.endObject();
					}
					builder.endObject();
				}
				builder.endObject();
				builder.startObject("plan_join");
				{
					builder.field("type", "join");
					builder.field("eager_global_ordinals", "true");
					builder.startObject("relations");
					{
						builder.array("plan", "planCostShares", "linkedPlanServices");
						builder.array("linkedPlanServices", "linkedService", "planserviceCostShares");
					}
					builder.endObject();
				}
				builder.endObject();
			}
			builder.endObject();
		}
		builder.endObject();

		return builder;

	}
}