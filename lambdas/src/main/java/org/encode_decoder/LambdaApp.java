package org.encode_decoder;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awscdk.services.events.targets.SqsQueue;

import java.util.Map;
import java.util.UUID;

/**
 * Hello world!
 *
 */
public class LambdaApp implements RequestHandler<SQSEvent, String>
{
    private AmazonDynamoDB dynamoDB = AmazonDynamoDBClientBuilder.defaultClient();

    @Override
    public String handleRequest(SQSEvent sqsEvent, Context context) {
        var objectMapper = new ObjectMapper();
        var records = sqsEvent.getRecords();

        for (var record: records) {
            var recordBody = record.getBody();
            JsonNode post = null;
            try {
                post = objectMapper.readTree(recordBody);
            } catch (JsonProcessingException e) {
                System.out.println(e.getMessage());
                continue;
            }

            var postText = post.path("text").asText("");

            var tweet = Map.of(
                    "id0", new AttributeValue(UUID.randomUUID().toString()),
                    "text", new AttributeValue(postText)
            );

            PutItemRequest addTweetRequest = new PutItemRequest()
                    .withTableName("Tweet")
                    .withItem(tweet);

            dynamoDB.putItem(addTweetRequest);
        }
        return "";
    }
}
