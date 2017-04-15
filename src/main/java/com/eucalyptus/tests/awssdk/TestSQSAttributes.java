package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.SetQueueAttributesRequest;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.eucalyptus.tests.awssdk.N4j.assertThat;
import static com.eucalyptus.tests.awssdk.N4j.getCloudInfoAndSqs;
import static com.eucalyptus.tests.awssdk.N4j.getSqsClientWithNewAccount;
import static com.eucalyptus.tests.awssdk.N4j.print;
import static com.eucalyptus.tests.awssdk.N4j.testInfo;

/**
 * Created by ethomas on 10/4/16.
 */
public class TestSQSAttributes {
  private String account;
  private String otherAccount;

  private AmazonSQS accountSQSClient;
  private AmazonSQS otherAccountSQSClient;

  @BeforeClass
  public void init() throws Exception {
    print("### PRE SUITE SETUP - " + this.getClass().getSimpleName());

    try {
      getCloudInfoAndSqs();
      account = "sqs-account-a-" + System.currentTimeMillis();
      SQSUtils.synchronizedCreateAccount(account);
      accountSQSClient = getSqsClientWithNewAccount(account, "admin");
      otherAccount = "sqs-account-b-" + System.currentTimeMillis();
      SQSUtils.synchronizedCreateAccount(otherAccount);
      otherAccountSQSClient = getSqsClientWithNewAccount(otherAccount, "admin");
    } catch (Exception e) {
      try {
        teardown();
      } catch (Exception ie) {
      }
      throw e;
    }
  }

  @AfterClass
  public void teardown() throws Exception {
    print("### POST SUITE CLEANUP - " + this.getClass().getSimpleName());
    if (account != null) {
      if (accountSQSClient != null) {
        ListQueuesResult listQueuesResult = accountSQSClient.listQueues();
        if (listQueuesResult != null) {
          listQueuesResult.getQueueUrls().forEach(accountSQSClient::deleteQueue);
        }
      }
      SQSUtils.synchronizedDeleteAccount(account);
    }
    if (otherAccount != null) {
      if (otherAccountSQSClient != null) {
        ListQueuesResult listQueuesResult = otherAccountSQSClient.listQueues();
        if (listQueuesResult != null) {
          listQueuesResult.getQueueUrls().forEach(otherAccountSQSClient::deleteQueue);
        }
      }
      SQSUtils.synchronizedDeleteAccount(otherAccount);
    }
  }

  @Test
  public void testSetQueueAttributesOtherAccount() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testSetQueueAttributesOtherAccount");
    String queueName = "queue_name_set_attributes_other_account";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    String otherAccountQueueUrl = otherAccountSQSClient.createQueue(queueName).getQueueUrl();
    try {
      // just some attribute
      accountSQSClient.setQueueAttributes(otherAccountQueueUrl,
        ImmutableMap.of("DelaySeconds", "30"));
      assertThat(false, "Should fail setting queue attributes on different account");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail setting queue attributes on different account");
    }
  }

  @Test
  public void testSetQueueAttributesNonExistentAccount() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testSetQueueAttributesNonExistentAccount");
    String queueName = "queue_name_set_attributes_nonexistent_account";

    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);

    try {
      // just some attribute
      accountSQSClient.setQueueAttributes(queueUrl.replace(accountId, "000000000000"),
        ImmutableMap.of("DelaySeconds", "30"));
      assertThat(false, "Should fail setting queue attribute from non-existent user");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 404, "Correctly fail setting queue attribute from a non-existent user");
    }
  }

  @Test
  public void testSetAttributesNonExistentQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testSetAttributesNonExistentQueue");
    String queueName = "queue_name_set_attributes_nonexistent_queue";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    try {
      // just some attribute
      accountSQSClient.setQueueAttributes(queueUrl + "-bogus",
        ImmutableMap.of("DelaySeconds", "30"));
      SetQueueAttributesRequest setQueueAttributesRequest = new SetQueueAttributesRequest();
      assertThat(false, "Should fail setting attribute on non-existent queue");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail setting attribute on non-existent queue");
    }
  }

  @Test
  public void testSetReadOnlyQueueAttributes() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testSetAttributesNonExistentQueue");
    String queueName = "queue_name_set_read_only_queue_attributes";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    try {
      accountSQSClient.setQueueAttributes(queueUrl,
        ImmutableMap.of("ApproximateNumberOfMessagesNotVisible", "30"));
      assertThat(false, "Should fail setting read only attribute on queue");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail setting read only attribute on queue");
    }
  }

  @Test
  public void testSetBogusQueueAttributes() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testSetAttributesNonExistentQueue");
    String queueName = "queue_name_set_bogus_queue_attributes";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    try {
      accountSQSClient.setQueueAttributes(queueUrl,
        ImmutableMap.of("Temperature", "98.6"));
      assertThat(false, "Should fail setting bogus attribute on queue");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail setting bogus attribute on queue");
    }
  }

  @Test
  public void testGetQueueAttributesOtherAccount() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testGetQueueAttributesOtherAccount");
    String queueName = "queue_name_get_attributes_other_account";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    String otherAccountQueueUrl = otherAccountSQSClient.createQueue(queueName).getQueueUrl();
    try {
      accountSQSClient.getQueueAttributes(otherAccountQueueUrl,
        Lists.newArrayList("DelaySeconds"));
      assertThat(false, "Should fail getting queue attributes on different account");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail getting queue attributes on different account");
    }
  }

  @Test
  public void testGetQueueAttributesNonExistentAccount() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testGetQueueAttributesNonExistentAccount");
    String queueName = "queue_name_get_attributes_nonexistent_account";

    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);

    try {
      accountSQSClient.getQueueAttributes(queueUrl.replace(accountId, "000000000000"),
        Lists.newArrayList("DelaySeconds"));
      assertThat(false, "Should fail getting queue attribute from non-existent user");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 404, "Correctly fail getting queue attribute from a non-existent user");
    }
  }

  @Test
  public void testGetAttributesNonExistentQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testGetAttributesNonExistentQueue");
    String queueName = "queue_name_get_attributes_nonexistent_queue";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    try {
      accountSQSClient.getQueueAttributes(queueUrl + "-bogus",
        Lists.newArrayList("DelaySeconds"));
      assertThat(false, "Should fail getting attribute on non-existent queue");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail getting attribute on non-existent queue");
    }
  }

  @Test
  public void testGetBogusQueueAttributes() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testGetAttributesNonExistentQueue");
    String queueName = "queue_name_get_bogus_queue_attributes";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    try {
      accountSQSClient.getQueueAttributes(queueUrl,
        Lists.newArrayList("Temperature"));
      assertThat(false, "Should fail getting bogus attribute on queue");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail getting bogus attribute on queue");
    }
  }

  private static final String SAMPLE_POLICY = "{\n" +
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


  @Test
  public void testGetAttributesOnCreateQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testGetAttributesOnCreateQueue");
    String otherQueueName = "queue_name_get_attributes_on_create_queue_1";
    String otherQueueUrl = accountSQSClient.createQueue(otherQueueName).getQueueUrl();

    // we need this queue's arn to put a dead letter policy in.  Might as well do getAttributes() to get it
    String otherQueueArn = accountSQSClient.getQueueAttributes(otherQueueUrl,
      Lists.newArrayList("QueueArn")).getAttributes().get("QueueArn");
    Map<String, String> attributes = ImmutableMap.<String,String>builder()
      .put("DelaySeconds", "5")
      .put("MaximumMessageSize", "128000")
      .put("MessageRetentionPeriod", "1200")
      .put("Policy", SAMPLE_POLICY)
      .put("ReceiveMessageWaitTimeSeconds", "10")
      .put("RedrivePolicy", "{\"maxReceiveCount\":\"5\",\"deadLetterTargetArn\":\"" + otherQueueArn + "\"}")
      .put("VisibilityTimeout", "5")
      .build();

    String queueName = "queue_name_get_atttributes_on_create_queue_2";
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);
    createQueueRequest.setAttributes(attributes);
    String queueUrl = accountSQSClient.createQueue(createQueueRequest).getQueueUrl();

    assertThat(
      attributes.equals(
        accountSQSClient.getQueueAttributes(
          queueUrl,
          Lists.newArrayList(attributes.keySet())
        ).getAttributes()
      ),
      "Queue attributes should match create values"
    );
  }

  @Test
  public void testGetAttributesOnSetAttributesQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testGetAttributesOnSetAttributesQueue");
    String otherQueueName = "queue_name_get_set_atttributes_queue_1";
    String otherQueueUrl = accountSQSClient.createQueue(otherQueueName).getQueueUrl();

    String queueName = "queue_name_get_set_atttributes__queue_2";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();


    // we need this queue's arn to put a dead letter policy in.  Might as well do getAttributes() to get it
    String otherQueueArn = accountSQSClient.getQueueAttributes(otherQueueUrl,
      Lists.newArrayList("QueueArn")).getAttributes().get("QueueArn");

    Map<String, String> attributes = ImmutableMap.<String,String>builder()
      .put("DelaySeconds", "5")
      .put("MaximumMessageSize", "128000")
      .put("MessageRetentionPeriod", "1200")
      .put("Policy", SAMPLE_POLICY)
      .put("ReceiveMessageWaitTimeSeconds", "10")
      .put("RedrivePolicy", "{\"maxReceiveCount\":\"5\",\"deadLetterTargetArn\":\"" + otherQueueArn + "\"}")
      .put("VisibilityTimeout", "5")
      .build();

    accountSQSClient.setQueueAttributes(queueUrl, attributes);

    assertThat(
      attributes.equals(
        accountSQSClient.getQueueAttributes(
          queueUrl,
          Lists.newArrayList(attributes.keySet())
        ).getAttributes()
      ),
      "Queue attributes should match set values"
    );
  }

  @Test
  public void testChangeSomeAttributes() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeSomeAttributes");
    String otherQueueName = "queue_name_change_some_atttributes_queue_1";
    String otherQueueUrl = accountSQSClient.createQueue(otherQueueName).getQueueUrl();

    // we need this queue's arn to put a dead letter policy in.  Might as well do getAttributes() to get it
    String otherQueueArn = accountSQSClient.getQueueAttributes(otherQueueUrl,
      Lists.newArrayList("QueueArn")).getAttributes().get("QueueArn");

    Map<String, String> attributes = ImmutableMap.<String,String>builder()
      .put("DelaySeconds", "5")
      .put("MaximumMessageSize", "128000")
      .put("MessageRetentionPeriod", "1200")
      .put("Policy", SAMPLE_POLICY)
      .put("ReceiveMessageWaitTimeSeconds", "10")
      .put("RedrivePolicy", "{\"maxReceiveCount\":\"5\",\"deadLetterTargetArn\":\""+otherQueueArn+"\"}")
      .put("VisibilityTimeout", "5")
      .build();

    String queueName = "queue_name_change_some_atttributes_queue_2";
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);
    createQueueRequest.setAttributes(attributes);
    String queueUrl = accountSQSClient.createQueue(createQueueRequest).getQueueUrl();

    // now change some of the attributes
    Map<String, String> attributes2 = ImmutableMap.<String,String>builder()
      .put("DelaySeconds", "20")
      .put("MaximumMessageSize", "130000")
      .put("Policy", "")
      .put("ReceiveMessageWaitTimeSeconds", "10")
      .put("RedrivePolicy", "")
      .build();
    accountSQSClient.setQueueAttributes(queueUrl, attributes2);

    Map<String, String> attributes3 = Maps.newHashMap();
    attributes3.putAll(attributes);
    attributes3.putAll(attributes2);
    List<String> getAttributeKeys = Lists.newArrayList(attributes3.keySet());
    // Policy and RedrivePolicy are essentially removed if empty (keep it in the key list though)
    attributes3.remove("Policy");
    attributes3.remove("RedrivePolicy");
    assertThat(
      attributes3.equals(
        accountSQSClient.getQueueAttributes(
          queueUrl,
          getAttributeKeys
        ).getAttributes()
      ),
      "Queue attributes should match correctly updated values"
    );
  }


  @Test
  public void testGetAttributesFilter() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testGetAttributesFilter");
    String otherQueueName = "queue_name_get_atttributes_filter_1";
    String otherQueueUrl = accountSQSClient.createQueue(otherQueueName).getQueueUrl();

    // we need this queue's arn to put a dead letter policy in.  Might as well do getAttributes() to get it
    String otherQueueArn = accountSQSClient.getQueueAttributes(otherQueueUrl,
      Lists.newArrayList("QueueArn")).getAttributes().get("QueueArn");

    Map<String, String> attributes = ImmutableMap.<String,String>builder()
      .put("DelaySeconds", "5")
      .put("MaximumMessageSize", "128000")
      .put("MessageRetentionPeriod", "1200")
      .put("Policy", SAMPLE_POLICY)
      .put("ReceiveMessageWaitTimeSeconds", "10")
      .put("RedrivePolicy", "{\"maxReceiveCount\":\"5\",\"deadLetterTargetArn\":\""+otherQueueArn+"\"}")
      .put("VisibilityTimeout", "5")
      .build();
    String queueName = "queue_name_get_atttributes_filter_2";
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);
    createQueueRequest.setAttributes(attributes);
    String queueUrl = accountSQSClient.createQueue(createQueueRequest).getQueueUrl();

    List<String> attributeNames = Lists.newArrayList(
      "ApproximateNumberOfMessages",
      "ApproximateNumberOfMessagesNotVisible",
      "VisibilityTimeout",
      "CreatedTimestamp",
      "LastModifiedTimestamp",
      "Policy",
      "MaximumMessageSize",
      "MessageRetentionPeriod",
      "QueueArn",
      "ApproximateNumberOfMessagesDelayed",
      "DelaySeconds",
      "ReceiveMessageWaitTimeSeconds",
      "RedrivePolicy"
    );
    assertThat(
      Sets.newHashSet(attributeNames).equals(
        accountSQSClient.getQueueAttributes(
          queueUrl,
          Lists.newArrayList("All")
        ).getAttributes().keySet()
      ),
      "All queue attributes should be returned"
    );
    assertThat(
      Collections.emptySet().equals(
        accountSQSClient.getQueueAttributes(
          queueUrl,
          Lists.newArrayList()
        ).getAttributes().keySet()
      ),
      "No queue attributes should be returned"
    );
    // try some random subsets
    Random random = new Random();
    for (int i=0;i<30;i++) {
      Collections.shuffle(attributeNames);
      List<String> subList = attributeNames.subList(0, random.nextInt(attributeNames.size()));
      assertThat(Sets.newHashSet(subList).equals(
        accountSQSClient.getQueueAttributes(
          queueUrl,
          subList
        ).getAttributes().keySet()
      ),
      "Matching queue attributes should be returned"
      );
    }
  }
}
