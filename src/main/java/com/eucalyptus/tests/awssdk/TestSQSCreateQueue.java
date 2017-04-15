package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static com.eucalyptus.tests.awssdk.N4j.LOCAL_EUCTL_FILE;
import static com.eucalyptus.tests.awssdk.N4j.assertThat;
import static com.eucalyptus.tests.awssdk.N4j.getCloudInfoAndSqs;
import static com.eucalyptus.tests.awssdk.N4j.getConfigProperty;
import static com.eucalyptus.tests.awssdk.N4j.getSqsClientWithNewAccount;
import static com.eucalyptus.tests.awssdk.N4j.print;
import static com.eucalyptus.tests.awssdk.N4j.testInfo;

/**
 * Created by ethomas on 9/14/16.
 */
public class TestSQSCreateQueue {

  private int MAX_QUEUE_NAME_LENGTH_CHARS;
  private int MAX_DELAY_SECONDS;
  private int MAX_MAXIMUM_MESSAGE_SIZE;
  private int MAX_MESSAGE_RETENTION_PERIOD;
  private int MAX_RECEIVE_MESSAGE_WAIT_TIME_SECONDS;
  private int MAX_VISIBILITY_TIMEOUT;
  private int MAX_MAX_RECEIVE_COUNT;
  private String region;

  private String account;
  private String otherAccount;

  private AmazonSQS accountSQSClient;
  private AmazonSQS otherAccountSQSClient;
  
  @BeforeClass
  public void init() throws Exception {
    print("### PRE SUITE SETUP - " + this.getClass().getSimpleName());

    try {
      getCloudInfoAndSqs();
      MAX_QUEUE_NAME_LENGTH_CHARS = getLocalConfigInt("MAX_QUEUE_NAME_LENGTH_CHARS");
      MAX_DELAY_SECONDS = getLocalConfigInt("MAX_DELAY_SECONDS");
      MAX_MAXIMUM_MESSAGE_SIZE = getLocalConfigInt("MAX_MAXIMUM_MESSAGE_SIZE");
      MAX_MESSAGE_RETENTION_PERIOD = getLocalConfigInt("MAX_MESSAGE_RETENTION_PERIOD");
      MAX_RECEIVE_MESSAGE_WAIT_TIME_SECONDS = getLocalConfigInt("MAX_RECEIVE_MESSAGE_WAIT_TIME_SECONDS");
      MAX_VISIBILITY_TIMEOUT = getLocalConfigInt("MAX_VISIBILITY_TIMEOUT");
      MAX_MAX_RECEIVE_COUNT = getLocalConfigInt("MAX_MAX_RECEIVE_COUNT");
      region = defaultIfNullOrJustWhitespace(getConfigProperty(LOCAL_EUCTL_FILE, "region.region_name"), "eucalyptus");
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
  public void testQueueUrlSyntax() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testQueueUrlSyntax");

    String queueName = "queue_url_syntax";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    // first make sure we have a queue url with an account id and a queue name
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    assertThat(pathParts.size() == 2, "The queue URL path needs two 'suffix' values: account id, and queue name");
    assertThat(pathParts.get(1).equals(queueName), "The queue URL path needs to end in the queue name");
  }

  @Test
  public void testQueueName() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testQueueName");
    // fail due to bad characters
    String badCharsQueueName = "@#%#$%#@$%#@$%";
    try {
      accountSQSClient.createQueue("@#%#$%#@$%#@$%");
      assertThat(false, "Should fail creating a queue with bad chars " + badCharsQueueName);
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with bad chars " + badCharsQueueName);
    }
    // fail due to too many chars (may fail if property has changed)
    String tooManyCharsQueueName = Strings.repeat("X", MAX_QUEUE_NAME_LENGTH_CHARS + 1);
    try {
      accountSQSClient.createQueue(tooManyCharsQueueName);
      assertThat(false, "Should fail creating a queue with too many chars " + tooManyCharsQueueName);
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with bad chars " + tooManyCharsQueueName);
    }

    // try to create the queue, and check that a second call works (idempotency)
    String queueName = "queue_name";
    String queueUrlFirstAttempt = accountSQSClient.createQueue(queueName).getQueueUrl();
    String queueUrlNextAttempt = accountSQSClient.createQueue(queueName).getQueueUrl();
    assertThat(queueUrlFirstAttempt.equals(queueUrlNextAttempt), "Called 'createQueue' with the same name, no parameters, idempotent.  Get the same url back");
  }

  @Test
  public void testDelaySeconds() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDelaySeconds");
    String queueName = "queue_delay_seconds";
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);
    helpTestSingleValueNumericAttribute(createQueueRequest, "DelaySeconds", 0, MAX_DELAY_SECONDS);
  }

  @Test
  public void testMessageRetentionPeriod() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - MessageRetentionPeriod");
    String queueName = "queue_message_retention_period";
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);
    helpTestSingleValueNumericAttribute(createQueueRequest, "MessageRetentionPeriod", 60, MAX_MESSAGE_RETENTION_PERIOD);
  }

  @Test
  public void testMaximumMessageSize() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testMaximumMessageSize");
    String queueName = "queue_maximum_message_size";
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);
    helpTestSingleValueNumericAttribute(createQueueRequest, "MaximumMessageSize", 1024, MAX_MAXIMUM_MESSAGE_SIZE);
  }
  @Test
  public void testReceiveMessageWaitTimeSeconds() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testReceiveMessageWaitTimeSeconds");
    String queueName = "queue_receive_message_wait_time_seconds";
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);
    helpTestSingleValueNumericAttribute(createQueueRequest, "ReceiveMessageWaitTimeSeconds", 0, MAX_RECEIVE_MESSAGE_WAIT_TIME_SECONDS);
  }

  @Test
  public void testVisibilityTimeout() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testVisibilityTimeout");
    String queueName = "queue_visibility_timeout";
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);
    helpTestSingleValueNumericAttribute(createQueueRequest, "VisibilityTimeout", 0, MAX_VISIBILITY_TIMEOUT);
  }

  @Test
  public void testRedrivePolicy() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testRedrivePolicy");
    String queueName = "queue_redrive_policy";

    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);
    
    // bad json
    createQueueRequest.getAttributes().put("RedrivePolicy", "{bx!");
    try {
      accountSQSClient.createQueue(createQueueRequest);
      assertThat(false, "Should fail creating a queue with attribute RedrivePolicy with bad json");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with attribute RedrivePolicy with bad json");
    }

    // non-object json
    createQueueRequest.getAttributes().put("RedrivePolicy", "[1,2,3]");
    try {
      accountSQSClient.createQueue(createQueueRequest);
      assertThat(false, "Should fail creating a queue with attribute RedrivePolicy with non-object json");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with attribute RedrivePolicy with non-object json");
    }

    // create legitimate queues so we can test various combinations
    String existingQueueName = "queue_redrive_policy_existing";

    String existingQueueArn = makeArn(accountSQSClient.createQueue(existingQueueName).getQueueUrl());
    String otherAccountExistingQueueArn = makeArn(otherAccountSQSClient.createQueue(existingQueueName).getQueueUrl());

    // missing 'maxReceiveCount'
    createQueueRequest.getAttributes().put("RedrivePolicy", redrivePolicyJSON(existingQueueArn, null));
    try {
      accountSQSClient.createQueue(createQueueRequest);
      assertThat(false, "Should fail creating a queue with attribute RedrivePolicy with no maxReceiveCount");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with attribute RedrivePolicy with no maxReceiveCount");
    }

    // missing 'deadLetterTargetArn'
    createQueueRequest.getAttributes().put("RedrivePolicy", redrivePolicyJSON(null, "1"));
    try {
      accountSQSClient.createQueue(createQueueRequest);
      assertThat(false, "Should fail creating a queue with attribute RedrivePolicy with no deadLetterTargetArn");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with attribute RedrivePolicy with no deadLetterTargetArn");
    }

    // too many fields
    createQueueRequest.getAttributes().put("RedrivePolicy", redrivePolicyJSON(existingQueueArn, "1", "Bob", "5"));
    try {
      accountSQSClient.createQueue(createQueueRequest);
      assertThat(false, "Should fail creating a queue with attribute RedrivePolicy with too many fields");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with attribute RedrivePolicy with too many fields");
    }

    // too low max receive count
    createQueueRequest.getAttributes().put("RedrivePolicy", redrivePolicyJSON(existingQueueArn, "0"));
    try {
      accountSQSClient.createQueue(createQueueRequest);
      assertThat(false, "Should fail creating a queue with attribute RedrivePolicy with maxReceiveCount that is too low");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with maxReceiveCount that is too low");
    }

    // too high max receive count
    createQueueRequest.getAttributes().put("RedrivePolicy", redrivePolicyJSON(existingQueueArn, String.valueOf(1 + MAX_MAX_RECEIVE_COUNT)));
    try {
      accountSQSClient.createQueue(createQueueRequest);
      assertThat(false, "Should fail creating a queue with attribute RedrivePolicy with maxReceiveCount that is too high");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with maxReceiveCount that is too high");
    }

    // not a number max receive count
    createQueueRequest.getAttributes().put("RedrivePolicy", redrivePolicyJSON(existingQueueArn, "X"));
    try {
      accountSQSClient.createQueue(createQueueRequest);
      assertThat(false, "Should fail creating a queue with attribute RedrivePolicy with maxReceiveCount that is not a number");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with maxReceiveCount that is not a number");
    }

    // different region dead letter queue
    createQueueRequest.getAttributes().put("RedrivePolicy", 
      redrivePolicyJSON(changeArnField(existingQueueArn, ARN_REGION_FIELD, region + "bogus"), "1"));
    try {
      accountSQSClient.createQueue(createQueueRequest);
      assertThat(false, "Should fail creating a queue with attribute RedrivePolicy with deadLetterTargetArn in a different region");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with deadLetterTargetArn in a different region");
    }

    // different account dead letter queue
    createQueueRequest.getAttributes().put("RedrivePolicy",
      redrivePolicyJSON(otherAccountExistingQueueArn, "1"));
    try {
      accountSQSClient.createQueue(createQueueRequest);
      assertThat(false, "Should fail creating a queue with attribute RedrivePolicy with deadLetterTargetArn in a different account");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with deadLetterTargetArn in a different account");
    }

    // nonexistent account dead letter queue
    createQueueRequest.getAttributes().put("RedrivePolicy",
      redrivePolicyJSON(changeArnField(existingQueueArn, ARN_ACCOUNT_FIELD, "000000000000"), "1"));
    try {
      accountSQSClient.createQueue(createQueueRequest);
      assertThat(false, "Should fail creating a queue with attribute RedrivePolicy with deadLetterTargetArn in a nonexistent account");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with deadLetterTargetArn in a nonexistent account");
    }

    // nonexistent queue dead letter queue
    createQueueRequest.getAttributes().put("RedrivePolicy",
      redrivePolicyJSON(changeArnField(existingQueueArn, ARN_QUEUE_NAME_FIELD, "bogus"), "1"));
    try {
      accountSQSClient.createQueue(createQueueRequest);
      assertThat(false, "Should fail creating a queue with attribute RedrivePolicy with deadLetterTargetArn with a nonexistent queue");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with deadLetterTargetArn with a nonexistent queue");
    }

    // set proper value.  Test idempotency
    createQueueRequest.getAttributes().put("RedrivePolicy", redrivePolicyJSON(existingQueueArn, "1"));
    String queueUrlFirstAttempt = accountSQSClient.createQueue(createQueueRequest).getQueueUrl();
    String queueUrlNextAttempt = accountSQSClient.createQueue(createQueueRequest).getQueueUrl();
    assertThat(queueUrlFirstAttempt.equals(queueUrlNextAttempt), "Called 'createQueue' with the same name, same parameters, idempotent.  Get the same url back");

    // test different value with idempotency.  should fail
    createQueueRequest.getAttributes().put("RedrivePolicy", redrivePolicyJSON(existingQueueArn, String.valueOf(MAX_MAX_RECEIVE_COUNT)));
    try {
      accountSQSClient.createQueue(createQueueRequest);
      assertThat(false, "Should fail creating a queue with attribute RedrivePolicy, existing queue with different number");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with attribute 'RedrivePolicy', existing queue with different number");
    }
  }

  @Test
  public void testPolicy() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testPolicy");
    String queueName = "queue_policy";

    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);

    // Try to create a queue with a bad policy
    createQueueRequest.getAttributes().put("Policy", "{\"my\":\"policy\"}");
    try {
      accountSQSClient.createQueue(createQueueRequest);
      assertThat(false, "Should fail creating a queue with a bad Policy");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with a bad Policy");
    }

    // Try to create a queue with a good policy
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

    // set proper value.  Test idempotency
    createQueueRequest.getAttributes().put("Policy", goodPolicy);
    String queueUrlFirstAttempt = accountSQSClient.createQueue(createQueueRequest).getQueueUrl();
    String queueUrlNextAttempt = accountSQSClient.createQueue(createQueueRequest).getQueueUrl();
    assertThat(queueUrlFirstAttempt.equals(queueUrlNextAttempt), "Called 'createQueue' with the same name, same parameters, idempotent.  Get the same url back");

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
    // test different value with idempotency.  should fail
    createQueueRequest.getAttributes().put("Policy", otherGoodPolicy);
    try {
      accountSQSClient.createQueue(createQueueRequest);
      assertThat(false, "Should fail creating a queue with attribute Policy, existing queue with different number");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with attribute 'Policy', existing queue with different number");
    }
 }

  @Test
  public void testBadAttributes() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testBadAttributes");
    String queueName = "queue_bad_attributes";

    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);

    // Try to create a queue with a read only attribute
    createQueueRequest.getAttributes().put("ApproximateNumberOfMessages", "0");
    try {
      accountSQSClient.createQueue(createQueueRequest);
      assertThat(false, "Should fail creating a queue with a read only attribute");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with a read only attribute");
    }

    // Try to create a queue with a read only attribute
    createQueueRequest.getAttributes().clear();
    createQueueRequest.getAttributes().put("All", "0");
    try {
      accountSQSClient.createQueue(createQueueRequest);
      assertThat(false, "Should fail creating a queue with an 'All' attribute");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with an 'All' attribute");
    }

    // Try to create a queue with a read only attribute
    createQueueRequest.getAttributes().clear();
    createQueueRequest.getAttributes().put("SalineConduction", "0");
    try {
      accountSQSClient.createQueue(createQueueRequest);
      assertThat(false, "Should fail creating a queue with a bogus attribute");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with a bogus attribute");
    }
  }

  @Test
  public void testRedrivePolicyJSON() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testRedrivePolicyJSON");
    assertThat("{}".equals(redrivePolicyJSON(null, null)), "no args");
    assertThat("{\"maxReceiveCount\":\"5\"}".equals(redrivePolicyJSON(null, "5")), "max receive count");
    assertThat("{\"deadLetterTargetArn\":\"arn:aws:sqs:eucalyptus:123456789012:queuename\"}".equals(redrivePolicyJSON("arn:aws:sqs:eucalyptus:123456789012:queuename", null)), "deadLetterTargetArn");
    assertThat("{\"deadLetterTargetArn\":\"arn:aws:sqs:eucalyptus:123456789012:queuename\",\"maxReceiveCount\":\"5\"}".equals(redrivePolicyJSON("arn:aws:sqs:eucalyptus:123456789012:queuename", "5")), "both args");
    assertThat("{\"deadLetterTargetArn\":\"arn:aws:sqs:eucalyptus:123456789012:queuename\",\"maxReceiveCount\":\"5\",\"A\":\"B\"}".equals(redrivePolicyJSON("arn:aws:sqs:eucalyptus:123456789012:queuename", "5","A","B")), "extra args");
  }

  @Test
  public void testMakeArn() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testMakeArn");
    assertThat(new String("arn:aws:sqs:" + region + ":123456789012:queueName").equals(makeArn("https://sqs.myhostodmain.com/123456789012/queueName")), "test make arn");
  }

  @Test
  public void testChangeArnField() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeArnField");
    assertThat("Y:X:X:X:X:X:X:X:X".equals(changeArnField("X:X:X:X:X:X:X:X:X",0,"Y")), "Test Field 0");
    assertThat("X:Y:X:X:X:X:X:X:X".equals(changeArnField("X:X:X:X:X:X:X:X:X",1,"Y")), "Test Field 1");
    assertThat("X:X:Y:X:X:X:X:X:X".equals(changeArnField("X:X:X:X:X:X:X:X:X", 2, "Y")), "Test Field 2");
    assertThat("X:X:X:Y:X:X:X:X:X".equals(changeArnField("X:X:X:X:X:X:X:X:X",3,"Y")), "Test Field 3");
    assertThat("X:X:X:X:Y:X:X:X:X".equals(changeArnField("X:X:X:X:X:X:X:X:X", 4, "Y")), "Test Field 4");
    assertThat("X:X:X:X:X:Y:X:X:X".equals(changeArnField("X:X:X:X:X:X:X:X:X",5,"Y")), "Test Field 5");
    assertThat("X:X:X:X:X:X:Y:X:X".equals(changeArnField("X:X:X:X:X:X:X:X:X",6,"Y")), "Test Field 6");
    assertThat("X:X:X:X:X:X:X:Y:X".equals(changeArnField("X:X:X:X:X:X:X:X:X", 7, "Y")), "Test Field 7");
    assertThat("X:X:X:X:X:X:X:X:Y".equals(changeArnField("X:X:X:X:X:X:X:X:X", 8, "Y")), "Test Field 8");
  }

  private void helpTestSingleValueNumericAttribute(CreateQueueRequest createQueueRequest, String attributeName, int min, int max) {
    // too low
    createQueueRequest.getAttributes().put(attributeName, String.valueOf(min - 1));
    try {
      accountSQSClient.createQueue(createQueueRequest);
      assertThat(false, "Should fail creating a queue with " + attributeName + " value too small");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with " + attributeName + " value too small");
    }

    // too high
    createQueueRequest.getAttributes().put(attributeName, String.valueOf(1 + max));
    try {
      accountSQSClient.createQueue(createQueueRequest);
      assertThat(false, "Should fail creating a queue with " + attributeName + " value too large");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with " + attributeName + " value too large");
    }

    // non-numeric
    createQueueRequest.getAttributes().put(attributeName, "X");
    try {
      accountSQSClient.createQueue(createQueueRequest);
      assertThat(false, "Should fail creating a queue with " + attributeName + " value not a number");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with " + attributeName + " value not a number");
    }
    // set proper value.  Test idempotency
    createQueueRequest.getAttributes().put(attributeName, String.valueOf(min));
    String queueUrlFirstAttempt = accountSQSClient.createQueue(createQueueRequest).getQueueUrl();
    String queueUrlNextAttempt = accountSQSClient.createQueue(createQueueRequest).getQueueUrl();
    assertThat(queueUrlFirstAttempt.equals(queueUrlNextAttempt), "Called 'createQueue' with the same name, same parameters, idempotent.  Get the same url back");

    // test different value with idempotency.  should fail
    createQueueRequest.getAttributes().put(attributeName, String.valueOf(max));

    try {
      accountSQSClient.createQueue(createQueueRequest);
      assertThat(false, "Should fail creating a queue with attribute '" + attributeName + "', existing queue with different number");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail creating a queue with attribute '" + attributeName + "', existing queue with different number");
    }
  }

  private String redrivePolicyJSON(String deadLetterTargetArn, String maxReceiveCount, String... extraArgs) {
    Map<String, String> fieldMap = Maps.newLinkedHashMap();
    if (deadLetterTargetArn != null) {
      fieldMap.put("deadLetterTargetArn", deadLetterTargetArn);
    }
    if (maxReceiveCount != null) {
      fieldMap.put("maxReceiveCount", maxReceiveCount);
    }
    if (extraArgs != null) {
      for (int i = 0; i < extraArgs.length; i += 2) {
        fieldMap.put(extraArgs[i], extraArgs[i + 1]);
      }
    }
    String delimiter = "";
    StringBuilder builder = new StringBuilder("{");
    for (Map.Entry<String, String> entry : fieldMap.entrySet()) {
      builder.append(String.format("%s\"%s\":\"%s\"", delimiter, entry.getKey(), entry.getValue()));
      delimiter = ",";
    }
    builder.append("}");
    return builder.toString();
  }

  private String makeArn(String queueUrl) throws MalformedURLException {
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    return "arn:aws:sqs:" + region + ":" + pathParts.get(0) + ":" + pathParts.get(1);
  }

  private String changeArnField(String oldArn, int position, String newValue) {
    List<String> parts = Lists.newArrayList(Splitter.on(':').split(oldArn));
    parts.set(position, newValue);
    return Joiner.on(':').join(parts);
  }

  private static final int ARN_REGION_FIELD = 3;
  private static final int ARN_ACCOUNT_FIELD = 4;
  private static final int ARN_QUEUE_NAME_FIELD = 5;

  private String defaultIfNullOrJustWhitespace(String target, String defaultValue) {
    if (target == null) return defaultValue;
    if (target.trim().isEmpty()) return defaultValue;
    return target;
  }

  private int getLocalConfigInt(String propertySuffixInCapsAndUnderscores) throws IOException {
    String propertyName = "services.simplequeue." + propertySuffixInCapsAndUnderscores.toLowerCase();
    return Integer.parseInt(getConfigProperty(LOCAL_EUCTL_FILE, propertyName));
  }

}
