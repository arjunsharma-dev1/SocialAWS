package com.socialaws.socialaws;


import com.amazonaws.auth.policy.resources.SQSQueueResource;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("posts")
public class PostController {

    public static final String DYNAMODB_TWEET_TABLE_NAME = "Tweet";
    private final AmazonDynamoDB amazonDynamoDB = AmazonDynamoDBClientBuilder.defaultClient();

    private AmazonSQSAsync amazonSQSAsync = AmazonSQSAsyncClientBuilder.defaultClient();

    @Autowired
    private Environment environment;

    @GetMapping
    public ResponseEntity<List<Map<String, String>>> getPosts() {

        var scanRequest = new ScanRequest(DYNAMODB_TWEET_TABLE_NAME);
        var scanResult = amazonDynamoDB.scan(scanRequest);
        var items = scanResult.getItems();

        return ResponseEntity.ok(items.parallelStream().map(scanResultToMap).toList());
    }

    private final Function<Map<String, AttributeValue>, Map<String, String>> scanResultToMap = (item) -> {
        return item.entrySet()
                .parallelStream()
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                (entry) -> entry.getValue().getS(),
                                (first, second) -> second
                        ));
    };


    @PostMapping
    public ResponseEntity<String> addPost(@RequestBody JsonNode postBody) throws JsonProcessingException {
        var objectMapper = new ObjectMapper();
        var postContent = postBody.path("text");

        var node = objectMapper.createObjectNode();
        node.put("text", postContent);

        var queueId = environment.getProperty("AWS_SOCIALAWS_SQS");

        var queueURL = amazonSQSAsync.getQueueUrl(queueId).getQueueUrl();

        var messageResponse = amazonSQSAsync.sendMessage(new SendMessageRequest(queueURL, objectMapper.writeValueAsString(node)));
        return ResponseEntity.ok(messageResponse.getMessageId());
    }

}
