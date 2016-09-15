package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.eucalyptus.tests.awssdk.N4j.*;

/**
 * Created by ethomas on 9/14/16.
 */
public class TestSQSCreateListDeleteQueue {

  @Test
  public void testCreateListDelete() throws Exception {
    getCloudInfoAndSqs();

    final int MAX_QUEUE_NAME_LENGTH_CHARS = getLocalConfigInt("MAX_QUEUE_NAME_LENGTH_CHARS");
    final int MAX_DELAY_SECONDS = getLocalConfigInt("MAX_DELAY_SECONDS");
    final int MAX_MAXIMUM_MESSAGE_SIZE = getLocalConfigInt("MAX_MAXIMUM_MESSAGE_SIZE");
    final int MAX_MESSAGE_RETENTION_PERIOD = getLocalConfigInt("MAX_MESSAGE_RETENTION_PERIOD");
    final int MAX_RECEIVE_MESSAGE_WAIT_TIME_SECONDS = getLocalConfigInt("MAX_RECEIVE_MESSAGE_WAIT_TIME_SECONDS");
    final int MAX_VISIBILITY_TIMEOUT = getLocalConfigInt("MAX_VISIBILITY_TIMEOUT");
    final int MAX_MAX_RECEIVE_COUNT = getLocalConfigInt("MAX_MAX_RECEIVE_COUNT");
    final String region = defaultIfNullOrJustWhitespace(getConfigProperty(LOCAL_EUCTL_FILE, "region.region_name"), "eucalyptus");


    String prefix = UUID.randomUUID().toString() + "-" + System.currentTimeMillis() + "-";
    try {
      // first make sure no queues with this prefix, but we need to get the queue url so we create a new queue we will delete

      String dummySuffix = "-dummy";
      String firstQueueName = prefix + dummySuffix;
      CreateQueueRequest createQueueRequest = new CreateQueueRequest();
      createQueueRequest.setQueueName(firstQueueName);
      String firstQueueUrl = sqs.createQueue(createQueueRequest).getQueueUrl();
      print("Creating queue to get URL prefix and account id");
      // first make sure we have a queue url with an account id and a queue name
      List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(firstQueueUrl).getPath()));
      assertThat(pathParts.size() == 2, "The queue URL path needs two 'suffix' values: account id, and queue name");
      assertThat(pathParts.get(1).equals(firstQueueName), "The queue URL path needs to end in the queue name");
      String accountId = pathParts.get(0);
      print("accountId="+accountId);
      print("Deleting queue " + firstQueueUrl);
      sqs.deleteQueue(firstQueueUrl);

      // now just in case this prefix matches one we already used, let's delete the queues that use it.  (very very unlikely)
      ListQueuesResult listQueuesResult = sqs.listQueues(prefix);
      if (listQueuesResult != null) {
        for (String queueUrl : listQueuesResult.getQueueUrls()) {
          sqs.deleteQueue(queueUrl);
        }
      }

      Map<String, String> createdQueuesMap = Maps.newLinkedHashMap();
      // first create a queue and check values of queue name
      createQueueWithNoAttributesTest(prefix, "queue1", MAX_QUEUE_NAME_LENGTH_CHARS, createdQueuesMap);


      // create several queues testing attributes
      createQueueWithSingleAttributeTest(prefix, "queue2", "DelaySeconds", 0, MAX_DELAY_SECONDS, createdQueuesMap);
      createQueueWithSingleAttributeTest(prefix, "queue3", "MaximumMessageSize", 1024, MAX_MAXIMUM_MESSAGE_SIZE, createdQueuesMap);
      createQueueWithSingleAttributeTest(prefix, "queue4", "MessageRetentionPeriod", 60, MAX_MESSAGE_RETENTION_PERIOD, createdQueuesMap);
      createQueueWithSingleAttributeTest(prefix, "queue5", "ReceiveMessageWaitTimeSeconds", 0, MAX_RECEIVE_MESSAGE_WAIT_TIME_SECONDS, createdQueuesMap);
      createQueueWithSingleAttributeTest(prefix, "queue6", "VisibilityTimeout", 0, MAX_VISIBILITY_TIMEOUT, createdQueuesMap);

      // create queue testing redrive policy
      createQueueWithRedrivePolicyTest(prefix, "queue7", 1, MAX_MAX_RECEIVE_COUNT, region, accountId, "queue1", createdQueuesMap);

      // create queue testing policy
      createQueueWithPolicyTest(prefix, "queue8", createdQueuesMap);

      // List Queues (see if they match)
      ListQueuesResult listQueuesResult2 = sqs.listQueues(prefix);
      Set<String> queueUrls = Sets.newHashSet(listQueuesResult2.getQueueUrls());

      assertThat(queueUrls.equals(Sets.newHashSet(createdQueuesMap.values())), "Queue urls match from list");
      for (String queueUrl : queueUrls) {
        sqs.deleteQueue(queueUrl);
      }

      // test list again (should return 0)
      assertThat(sqs.listQueues(prefix).getQueueUrls().size() == 0, "Queue urls should be gone after delete");


    } finally {
      ListQueuesResult listQueuesResult = sqs.listQueues(prefix);
      if (listQueuesResult != null) {
        for (String queueUrl : listQueuesResult.getQueueUrls()) {
          sqs.deleteQueue(queueUrl);
        }
      }
    }
  }

  private void createQueueWithPolicyTest(String prefix, String suffix, Map<String, String> createdQueuesMap) {
    // Try to create a queue with a bad policy
    String badPolicy = "{\"my\":\"policy\"}";

    String queueName = prefix + suffix;
    print("Trying to create queue " + queueName + " with a bad Policy (should fail)");
    CreateQueueRequest createQueueRequest1 = new CreateQueueRequest();
    createQueueRequest1.setQueueName(queueName);
    createQueueRequest1.setAttributes(ImmutableMap.of("Policy", badPolicy));
    try {
      sqs.createQueue(createQueueRequest1);
      assertThat(false, "Should fail creating a queue with a bad Policy");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with a bad Policy");
    }

    print("Trying to create queue " + queueName + " with a good Policy (should succeed)");
    String goodPolicy = "{\n" +
      "  \"Version\": \"2012-10-17\",\n" +
      "  \"Id\": \"Queue1_Policy_UUID\",\n" +
      "  \"Statement\": [\n" +
      "    {\n" +
      "      \"Sid\":\"Queue1_AllActions\",\n" +
      "      \"Effect\": \"Allow\",\n" +
      "      \"Principal\": {\n" +
      "        \"AWS\": [\"arn:aws:iam::111122223333:role/role1\",\"arn:aws:iam::111122223333:user/username1\"]\n" +
      "      },\n" +
      "      \"Action\": \"sqs:*\",\n" +
      "      \"Resource\": \"arn:aws:sqs:us-east-1:123456789012:queue1\"\n" +
      "    }\n" +
      "  ]\n" +
      "}\n";
    CreateQueueRequest createQueueRequest2 = new CreateQueueRequest();
    createQueueRequest2.setQueueName(queueName);
    createQueueRequest2.setAttributes(ImmutableMap.of("Policy", goodPolicy));
    String queueUrl = sqs.createQueue(createQueueRequest2).getQueueUrl();

    // Try idempotent case
    print("Trying to use idempotentcy in create queue " + queueName);
    CreateQueueRequest createQueueRequest3 = new CreateQueueRequest();
    createQueueRequest3.setQueueName(queueName);
    createQueueRequest3.setAttributes(ImmutableMap.of("Policy", goodPolicy));
    assertThat(queueUrl.equals(sqs.createQueue(createQueueRequest3).getQueueUrl()), "Create queue should be idempotent if no parameters have changed");

    String otherGoodPolicy = "{\n" +
      "  \"Version\": \"2012-10-17\",\n" +
      "  \"Id\": \"Queue1_Policy_UUID\",\n" +
      "  \"Statement\": [\n" +
      "    {\n" +
      "      \"Sid\":\"Queue1_AllActions\",\n" +
      "      \"Effect\": \"Allow\",\n" +
      "      \"Principal\": {\n" +
      "        \"AWS\": [\"arn:aws:iam::111122223333:role/role1\",\"arn:aws:iam::111122223333:user/username1\"]\n" +
      "      },\n" +
      "      \"Action\": \"sqs:*\",\n" +
      "      \"Resource\": \"arn:aws:sqs:us-west-1:123456789012:queue1\"\n" +
      "    }\n" +
      "  ]\n" +
      "}\n";
    print("Trying to create queue " + queueName + " with different Policy (should fail) ");
    CreateQueueRequest createQueueRequest4 = new CreateQueueRequest();
    createQueueRequest4.setQueueName(queueName);
    createQueueRequest4.setAttributes(ImmutableMap.of("Policy", otherGoodPolicy));
    try {
      sqs.createQueue(createQueueRequest4);
      assertThat(false, "Should fail creating a queue with with different Policy");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with different Policy");
    }
    createdQueuesMap.put(queueName, queueUrl);
  }

  private void failCreateQueueWithBadRedrivePolicyTest(String queueName, String redrivePolicy, String thatStr) {
    print("Trying to create queue " + queueName + " with attribute RedrivePolicy that " + thatStr);
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);
    createQueueRequest.setAttributes(ImmutableMap.of("RedrivePolicy", redrivePolicy));
    try {
      sqs.createQueue(createQueueRequest);
      assertThat(false, "Should fail creating a queue with attribute RedrivePolicy that " + thatStr);
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with attribute RedrivePolicy that " + thatStr);
    }
  }

  private void createQueueWithRedrivePolicyTest(String prefix, String suffix, int minMaxReceiveCount,
                                                int maxMaxReceiveCount, String region, String accountId, 
                                                String existingQueueSuffix, Map<String, String> createdQueuesMap) {


    // try to create a queue with a dead letter queue/redrive policy with bad json
    String queueName = prefix + suffix;
    failCreateQueueWithBadRedrivePolicyTest(queueName, "{bx!", "has bad json");

    // try to create a queue with a dead letter queue/redrive policy with json that is not an object
    failCreateQueueWithBadRedrivePolicyTest(queueName, "[1,2,3]", "is not an object");

    String existingQueueName = prefix + existingQueueSuffix;
    String legitRedriveQueueArn = "arn:aws:sqs:" + region + ":" + accountId + ":" + existingQueueName;

    String jsonWithoutMaxReceiveCount = "{\"deadLetterTargetArn\":\"" + legitRedriveQueueArn + "\"}";
    // try to create a queue with a dead letter queue/redrive policy that doesn't contain a maxReceiveCount
    failCreateQueueWithBadRedrivePolicyTest(queueName, jsonWithoutMaxReceiveCount, "doesn't contain a maxReceiveCount");

    String jsonWithoutDeadLetterTargetArn = "{\"maxReceiveCount\":\"" + minMaxReceiveCount + "\"}";
    // try to create a queue with a dead letter queue/redrive policy that doesn't contain a maxReceiveCount
    failCreateQueueWithBadRedrivePolicyTest(queueName, jsonWithoutDeadLetterTargetArn, "doesn't contain a deadLetterTargetArn");

    String jsonWithTooManyFields = "{\"bob\":\"5\",\"deadLetterTargetArn\":\"" + legitRedriveQueueArn + "\",\"maxReceiveCount\":\"" + minMaxReceiveCount + "\"}";
    // try to create a queue with a dead letter queue/redrive policy that contains too many fields
    failCreateQueueWithBadRedrivePolicyTest(queueName, jsonWithTooManyFields, "contains too many fields");

    String jsonWithTooLowMaxReceiveCount = "{\"deadLetterTargetArn\":\"" + legitRedriveQueueArn + "\",\"maxReceiveCount\":\"" + (minMaxReceiveCount - 1) + "\"}";
    // try to create a queue with a dead letter queue/redrive policy that has too low a value for maxReceiveCount
    failCreateQueueWithBadRedrivePolicyTest(queueName, jsonWithTooLowMaxReceiveCount, "has too low a value for maxReceiveCount");

    String jsonWithTooHighMaxReceiveCount = "{\"deadLetterTargetArn\":\"" + legitRedriveQueueArn + "\",\"maxReceiveCount\":\"" + (maxMaxReceiveCount + 1) + "\"}";
    // try to create a queue with a dead letter queue/redrive policy that has too high a value for maxReceiveCount
    failCreateQueueWithBadRedrivePolicyTest(queueName, jsonWithTooHighMaxReceiveCount, "has too high a value for maxReceiveCount");

    String jsonWithNonNumericMaxReceiveCount = "{\"deadLetterTargetArn\":\"" + legitRedriveQueueArn + "\",\"maxReceiveCount\":\"" + "X" + "\"}";
    // try to create a queue with a dead letter queue/redrive policy that has a non-numeric value for maxReceiveCount
    failCreateQueueWithBadRedrivePolicyTest(queueName, jsonWithNonNumericMaxReceiveCount, "has a non-numeric value for maxReceiveCount");

    // try to create a queue with a dead letter queue/redrive policy that has a redriveQueueArn in a different region
    String wrongRegionRedriveQueueArn = "arn:aws:sqs:" + region + "bogus" + ":" + accountId + ":" + existingQueueName;
    String jsonWithWrongRegionRedriveQueueArn = "{\"deadLetterTargetArn\":\"" + wrongRegionRedriveQueueArn + "\",\"maxReceiveCount\":\"" + minMaxReceiveCount  + "\"}";
    // try to create a queue with a dead letter queue/redrive policy that has a redriveQueueArn in a different region
    failCreateQueueWithBadRedrivePolicyTest(queueName, jsonWithWrongRegionRedriveQueueArn, "has a redriveQueueArn in a different region");

    // try to create a queue with a dead letter queue/redrive policy that has a redriveQueueArn in a different account
    String wrongAccountRedriveQueueArn = "arn:aws:sqs:" + region + ":" + "000000000000" + ":" + existingQueueName;
    String jsonWithWrongAccountRedriveQueueArn = "{\"deadLetterTargetArn\":\"" + wrongAccountRedriveQueueArn + "\",\"maxReceiveCount\":\"" + minMaxReceiveCount  + "\"}";
    // try to create a queue with a dead letter queue/redrive policy that has a redriveQueueArn in a different account
    failCreateQueueWithBadRedrivePolicyTest(queueName, jsonWithWrongAccountRedriveQueueArn, "has a redriveQueueArn in a different account");

    // try to create a queue with a dead letter queue/redrive policy that has a redriveQueueArn with a non-existant queue
    String nonexistantQueueRedriveQueueArn = "arn:aws:sqs:" + region + ":" + accountId + ":" + queueName;
    String jsonWithNonexistantQueueRedriveQueueArn = "{\"deadLetterTargetArn\":\"" + nonexistantQueueRedriveQueueArn + "\",\"maxReceiveCount\":\"" + minMaxReceiveCount  + "\"}";
    // try to create a queue with a dead letter queue/redrive policy that has a redriveQueueArn in a different account
    failCreateQueueWithBadRedrivePolicyTest(queueName, jsonWithNonexistantQueueRedriveQueueArn, "has a redriveQueueArn with a non-existant queue");

    // Create a queue with correct values
    String goodJson = "{\"deadLetterTargetArn\":\"" + legitRedriveQueueArn + "\",\"maxReceiveCount\":\"" + minMaxReceiveCount  + "\"}";
    print("Trying to create queue " + queueName + " with attribute RedrivePolicy that is correct (should succeed)");
    CreateQueueRequest createQueueRequest1 = new CreateQueueRequest();
    createQueueRequest1.setQueueName(queueName);
    createQueueRequest1.setAttributes(ImmutableMap.of("RedrivePolicy", goodJson));
    String queueUrl =  sqs.createQueue(createQueueRequest1).getQueueUrl();

    // Try idempotent case
    print("Trying to use idempotentcy in create queue " + queueName);
    // 3) you can create the same queue if no arguments are different
    CreateQueueRequest createQueueRequest2 = new CreateQueueRequest();
    createQueueRequest2.setQueueName(queueName);
    createQueueRequest2.setAttributes(ImmutableMap.of("RedrivePolicy", goodJson));
    assertThat(queueUrl.equals(sqs.createQueue(createQueueRequest2).getQueueUrl()), "Create queue should be idempotent if no parameters have changed");

    String otherGoodJson = "{\"deadLetterTargetArn\":\"" + legitRedriveQueueArn + "\",\"maxReceiveCount\":\"" + maxMaxReceiveCount  + "\"}";
    print("Trying to create queue " + queueName + " with different attribute RedrivePolicy (should fail) ");
    CreateQueueRequest createQueueRequest3 = new CreateQueueRequest();
    createQueueRequest3.setQueueName(queueName);
    createQueueRequest3.setAttributes(ImmutableMap.of("RedrivePolicy", otherGoodJson));
    try {
      sqs.createQueue(createQueueRequest3);
      assertThat(false, "Should fail creating a queue with with different attribute RedrivePolicy");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with different attribute RedrivePolicy");
    }

    createdQueuesMap.put(queueName, queueUrl);
  }


  private String defaultIfNullOrJustWhitespace(String target, String defaultValue) {
    if (target == null) return defaultValue;
    if (target.trim().isEmpty()) return defaultValue;
    return target;
  }

  private void createQueueWithNoAttributesTest(String prefix, String suffix, int maxLength, Map<String, String> createdQueuesMap) {
    // first create a queue
    String queueName = prefix + suffix;
    print("Creating queue " + queueName);
    String queueUrl = sqs.createQueue(queueName).getQueueUrl();
    createdQueuesMap.put(queueName, queueUrl);

    // now try to create a second queue, fail in many ways

    print("Trying to create a queue with bad chars in the name(should fail)");
    // 1) fail due to bad characters
    String badCharsQueueName = "@#%#$%#@$%#@$%";
    try {
      sqs.createQueue("@#%#$%#@$%#@$%");
      assertThat(false, "Should fail creating a queue with bad chars " + badCharsQueueName);
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with bad chars " + badCharsQueueName);
    }

    print("Trying to create a queue with too many chars in the name (should fail)");
    // 2) fail due to too many chars (may fail if property has changed)
    String tooManyCharsQueueName = Strings.repeat("X", maxLength + 1);
    try {
      sqs.createQueue(tooManyCharsQueueName);
      assertThat(false, "Should fail creating a queue with too many chars " + tooManyCharsQueueName);
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with bad chars " + tooManyCharsQueueName);
    }

    print("Trying to use idempotentcy in create queue " + queueName);
    // 3) you can create the same queue if no arguments are different
    assertThat(queueUrl.equals(sqs.createQueue(queueName).getQueueUrl()), "Create queue should be idempotent if no parameters have changed");
  }

  private int getLocalConfigInt(String propertySuffixInCapsAndUnderscores) throws IOException {
    String propertyName = "services.simplequeue." + propertySuffixInCapsAndUnderscores.toLowerCase();
    return Integer.parseInt(getConfigProperty(LOCAL_EUCTL_FILE, propertyName));
  }

  private void createQueueWithSingleAttributeTest(String prefix, String suffix, String attributeName, 
                                                  int min, int max, Map<String, String> createdQueuesMap) throws Exception {
    // Try the attribute with a value that is too low
    String queueName = prefix + suffix;
    print("Trying to create queue " + queueName + " with Attribute " + attributeName + " with too low value (should fail)");
    CreateQueueRequest createQueueRequest1 = new CreateQueueRequest();
    createQueueRequest1.setQueueName(queueName);
    createQueueRequest1.setAttributes(ImmutableMap.of(attributeName, String.valueOf(min - 1)));
    try {
      sqs.createQueue(createQueueRequest1);
      assertThat(false, "Should fail creating a queue with attribute '" + attributeName + "', value too small");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with attribute '" + attributeName + "', value too small");
    }

    // Try the attribute with a value that is too high
    print("Trying to create queue " + queueName + " with Attribute " + attributeName + " with too high value (should fail)");
    CreateQueueRequest createQueueRequest2 = new CreateQueueRequest();
    createQueueRequest2.setQueueName(queueName);
    createQueueRequest2.setAttributes(ImmutableMap.of(attributeName, String.valueOf(max + 1)));
    try {
      sqs.createQueue(createQueueRequest2);
      assertThat(false, "Should fail creating a queue with attribute '" + attributeName + "', value too big");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with attribute '" + attributeName + "', value too big");
    }

    // Try the attribute with a value that is too high
    print("Trying to create queue " + queueName + " with Attribute " + attributeName + " with value not a number (should fail)");
    // Try the attribute with a value that is not a number
    CreateQueueRequest createQueueRequest3 = new CreateQueueRequest();
    createQueueRequest3.setQueueName(queueName);
    createQueueRequest3.setAttributes(ImmutableMap.of(attributeName, "X"));
    try {
      sqs.createQueue(createQueueRequest3);
      assertThat(false, "Should fail creating a queue with attribute '" + attributeName + "', value not a number");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with attribute '" + attributeName + "', value not a number");
    }

    // Try the attribute with a value that is too high
    print("Trying to create queue " + queueName + " with Attribute " + attributeName + " with legal value (should succeed)");
    // Try with a correct attribute
    CreateQueueRequest createQueueRequest4 = new CreateQueueRequest();
    createQueueRequest4.setQueueName(queueName);
    createQueueRequest4.setAttributes(ImmutableMap.of(attributeName, String.valueOf(min)));
    String queueUrl = sqs.createQueue(createQueueRequest4).getQueueUrl();
    
    createdQueuesMap.put(queueName, queueUrl);

    print("Trying to create queue " + queueName + " with Attribute " + attributeName + " with different legal value (should fail)");
    // Fail due to a different value with the same queue name
    CreateQueueRequest createQueueRequest5 = new CreateQueueRequest();
    createQueueRequest5.setQueueName(queueName);
    createQueueRequest5.setAttributes(ImmutableMap.of(attributeName, String.valueOf(max)));
    try {
      sqs.createQueue(createQueueRequest5);
      assertThat(false, "Should fail creating a queue with attribute '" + attributeName + "', existing queue with different number");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with attribute '" + attributeName + "', existing queue with different number");
    }

    print("Trying to create queue " + queueName + " with Attribute " + attributeName + " with original legal value (should succeed, idempotent)");
    // Try with a correct attribute
    CreateQueueRequest createQueueRequest6 = new CreateQueueRequest();
    createQueueRequest6.setQueueName(queueName);
    createQueueRequest6.setAttributes(ImmutableMap.of(attributeName, String.valueOf(min)));
    assertThat(queueUrl.equals(sqs.createQueue(createQueueRequest6).getQueueUrl()), "Create queue should be idempotent if no parameters have changed");

  }
}
