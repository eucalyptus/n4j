package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.BatchResultErrorEntry;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityBatchRequest;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityBatchRequestEntry;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityBatchResult;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.eucalyptus.tests.awssdk.N4j.LOCAL_EUCTL_FILE;
import static com.eucalyptus.tests.awssdk.N4j.assertThat;
import static com.eucalyptus.tests.awssdk.N4j.getCloudInfoAndSqs;
import static com.eucalyptus.tests.awssdk.N4j.getConfigProperty;
import static com.eucalyptus.tests.awssdk.N4j.getSqsClientWithNewAccount;
import static com.eucalyptus.tests.awssdk.N4j.print;
import static com.eucalyptus.tests.awssdk.N4j.testInfo;

/**
 * Created by ethomas on 9/21/16.
 */
public class TestSQSChangeMessageVisibilityBatch {

  private int MAX_VISIBILITY_TIMEOUT;
  private int MAX_NUM_BATCH_ENTRIES;
  private int MAX_BATCH_ID_LENGTH;
  private int MAX_RECEIVE_MESSAGE_MAX_NUMBER_OF_MESSAGES;
  private String account;
  private String otherAccount;

  private AmazonSQS accountSQSClient;
  private AmazonSQS otherAccountSQSClient;

  @BeforeClass
  public void init() throws Exception {
    print("### PRE SUITE SETUP - " + this.getClass().getSimpleName());

    try {
      getCloudInfoAndSqs();
      MAX_VISIBILITY_TIMEOUT = getLocalConfigInt("MAX_VISIBILITY_TIMEOUT");
      MAX_NUM_BATCH_ENTRIES = getLocalConfigInt("MAX_NUM_BATCH_ENTRIES");
      MAX_BATCH_ID_LENGTH = getLocalConfigInt("MAX_BATCH_ID_LENGTH");
      MAX_RECEIVE_MESSAGE_MAX_NUMBER_OF_MESSAGES = getLocalConfigInt("MAX_RECEIVE_MESSAGE_MAX_NUMBER_OF_MESSAGES");
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
  public void testChangeMessageVisibilityBatchNonExistentAccount() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeMessageVisibilityBatchNonExistentAccount");
    String queueName = "queue_name_change_message_batch_visibility_nonexistent_account";
    String queueUrl = createQueueWithZeroDelayAndVisibilityTimeout(accountSQSClient, queueName);
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);
    String receiptHandle = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    try {
      ChangeMessageVisibilityBatchRequest changeMessageVisibilityBatchRequest = new ChangeMessageVisibilityBatchRequest();
      changeMessageVisibilityBatchRequest.setQueueUrl(queueUrl.replace(accountId, "000000000000"));
      ChangeMessageVisibilityBatchRequestEntry changeMessageVisibilityBatchRequestEntry = new ChangeMessageVisibilityBatchRequestEntry();
      changeMessageVisibilityBatchRequestEntry.setId("id");
      changeMessageVisibilityBatchRequestEntry.setReceiptHandle(receiptHandle);
      changeMessageVisibilityBatchRequestEntry.setVisibilityTimeout(0);
      changeMessageVisibilityBatchRequest.getEntries().add(changeMessageVisibilityBatchRequestEntry);
      accountSQSClient.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);
      assertThat(false, "Should fail changing message visibility batch on a queue from a non-existent user");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 404, "Correctly fail changing message visibility batch on a queue from a non-existent user");
    }
  }

  @Test
  public void testChangeMessageVisibilityBatchOtherAccount() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeMessageVisibilityBatchOtherAccount");
    String queueName = "queue_name_change_message_batch_visibility_other_account";
    String queueUrl = createQueueWithZeroDelayAndVisibilityTimeout(accountSQSClient, queueName);
    String otherAccountQueueUrl = createQueueWithZeroDelayAndVisibilityTimeout(otherAccountSQSClient, queueName);
    String receiptHandle = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    try {
      ChangeMessageVisibilityBatchRequest changeMessageVisibilityBatchRequest = new ChangeMessageVisibilityBatchRequest();
      changeMessageVisibilityBatchRequest.setQueueUrl(otherAccountQueueUrl);
      ChangeMessageVisibilityBatchRequestEntry changeMessageVisibilityBatchRequestEntry = new ChangeMessageVisibilityBatchRequestEntry();
      changeMessageVisibilityBatchRequestEntry.setId("id");
      changeMessageVisibilityBatchRequestEntry.setReceiptHandle(receiptHandle);
      changeMessageVisibilityBatchRequestEntry.setVisibilityTimeout(0);
      changeMessageVisibilityBatchRequest.getEntries().add(changeMessageVisibilityBatchRequestEntry);
      accountSQSClient.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);
      assertThat(false, "Should fail changing message visibility batch on a queue from a different user");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail changing message visibility batch on a queue from a different user");
    }
  }

  @Test
  public void testChangeMessageVisibilityBatchNonExistentQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeMessageVisibilityBatchNonExistentQueue");
    String queueName = "queue_name_change_message_batch_visibility_nonexistent_queue";
    String queueUrl = createQueueWithZeroDelayAndVisibilityTimeout(accountSQSClient, queueName);
    String receiptHandle = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    try {
      ChangeMessageVisibilityBatchRequest changeMessageVisibilityBatchRequest = new ChangeMessageVisibilityBatchRequest();
      changeMessageVisibilityBatchRequest.setQueueUrl(queueUrl + "-bogus");
      ChangeMessageVisibilityBatchRequestEntry changeMessageVisibilityBatchRequestEntry = new ChangeMessageVisibilityBatchRequestEntry();
      changeMessageVisibilityBatchRequestEntry.setId("id");
      changeMessageVisibilityBatchRequestEntry.setReceiptHandle(receiptHandle);
      changeMessageVisibilityBatchRequestEntry.setVisibilityTimeout(0);
      changeMessageVisibilityBatchRequest.getEntries().add(changeMessageVisibilityBatchRequestEntry);
      accountSQSClient.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);
      assertThat(false, "Should fail changing message visibility batch on non-existent queue");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail changing message visibility batch on non-existent queue");
    }
  }

  @Test
  public void testChangeMessageVisibilityBatchBogusReceiptHandles() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeMessageVisibilityBatchBogusReceiptHandles");
    String queueName = "queue_name_change_message_batch_visibility_bogus_rh";
    String queueUrl = createQueueWithZeroDelayAndVisibilityTimeout(accountSQSClient, queueName);
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);
    String otherAccountQueueUrl = createQueueWithZeroDelayAndVisibilityTimeout(otherAccountSQSClient, queueName);
    List<String> otherPathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(otherAccountQueueUrl).getPath()));
    String otherAccountId = otherPathParts.get(0);

    String receiptHandle = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);

    Collection<String> bogusReceiptHandles = ImmutableList.of(
      "bob", "a:b:c:d:e:f", "toot-toot", receiptHandle + "-again",
      receiptHandle.replaceAll(accountId, "000000000000"),
      receiptHandle.replaceAll(accountId, otherAccountId)
    );
    ChangeMessageVisibilityBatchRequest changeMessageVisibilityBatchRequest = new ChangeMessageVisibilityBatchRequest();
    changeMessageVisibilityBatchRequest.setQueueUrl(queueUrl);
    int id = 0;
    for (String bogusReceiptHandle: bogusReceiptHandles) {
      ChangeMessageVisibilityBatchRequestEntry changeMessageVisibilityBatchRequestEntry = new ChangeMessageVisibilityBatchRequestEntry();
      changeMessageVisibilityBatchRequestEntry.setId("id"+(id++));
      changeMessageVisibilityBatchRequestEntry.setVisibilityTimeout(0);
      changeMessageVisibilityBatchRequestEntry.setReceiptHandle(bogusReceiptHandle);
      changeMessageVisibilityBatchRequest.getEntries().add(changeMessageVisibilityBatchRequestEntry);
    }

    ChangeMessageVisibilityBatchResult changeMessageVisibilityBatchResult = accountSQSClient.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);
    assertThat(changeMessageVisibilityBatchResult.getFailed().size() == bogusReceiptHandles.size(), "Should fail changing message visibility batch with bogus receipt handles");
    for (BatchResultErrorEntry batchResultErrorEntry: changeMessageVisibilityBatchResult.getFailed()) {
      assertThat(batchResultErrorEntry.getCode().equals("ReceiptHandleIsInvalid"), "Correctly fail changing message visibility batch with bogus receipt handles");
    }
  }

  @Test
  public void testChangeMessageVisibilityBatchLowTimeout() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeMessageVisibilityBatchLowTimeout");
    String queueName = "queue_name_change_message_batch_visibility_low_timeout";
    String queueUrl = createQueueWithZeroDelayAndVisibilityTimeout(accountSQSClient, queueName);
    String receiptHandle = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    ChangeMessageVisibilityBatchRequest changeMessageVisibilityBatchRequest = new ChangeMessageVisibilityBatchRequest();
    changeMessageVisibilityBatchRequest.setQueueUrl(queueUrl);
    ChangeMessageVisibilityBatchRequestEntry changeMessageVisibilityBatchRequestEntry = new ChangeMessageVisibilityBatchRequestEntry();
    changeMessageVisibilityBatchRequestEntry.setId("id");
    changeMessageVisibilityBatchRequestEntry.setReceiptHandle(receiptHandle);
    changeMessageVisibilityBatchRequestEntry.setVisibilityTimeout(-1);
    changeMessageVisibilityBatchRequest.getEntries().add(changeMessageVisibilityBatchRequestEntry);
    accountSQSClient.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);
    ChangeMessageVisibilityBatchResult changeMessageVisibilityBatchResult = accountSQSClient.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);
    assertThat(changeMessageVisibilityBatchResult.getFailed().size() == 1, "Should fail changing message visibility batch with too low a visibility timeout");
    assertThat(changeMessageVisibilityBatchResult.getFailed().get(0).getCode().equals("InvalidParameterValue"), "Should fail changing message visibility batch with too low a visibility timeout");
  }

  @Test
  public void testChangeMessageVisibilityBatchHighTimeout() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeMessageVisibilityBatchHighTimeout");
    String queueName = "queue_name_change_message_batch_visibility_high_timeout";
    String queueUrl = createQueueWithZeroDelayAndVisibilityTimeout(accountSQSClient, queueName);
    String receiptHandle = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    ChangeMessageVisibilityBatchRequest changeMessageVisibilityBatchRequest = new ChangeMessageVisibilityBatchRequest();
    changeMessageVisibilityBatchRequest.setQueueUrl(queueUrl);
    ChangeMessageVisibilityBatchRequestEntry changeMessageVisibilityBatchRequestEntry = new ChangeMessageVisibilityBatchRequestEntry();
    changeMessageVisibilityBatchRequestEntry.setId("id");
    changeMessageVisibilityBatchRequestEntry.setReceiptHandle(receiptHandle);
    changeMessageVisibilityBatchRequestEntry.setVisibilityTimeout(1 + MAX_VISIBILITY_TIMEOUT);
    changeMessageVisibilityBatchRequest.getEntries().add(changeMessageVisibilityBatchRequestEntry);
    accountSQSClient.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);
    ChangeMessageVisibilityBatchResult changeMessageVisibilityBatchResult = accountSQSClient.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);
    assertThat(changeMessageVisibilityBatchResult.getFailed().size() == 1, "Should fail changing message visibility batch with too high a visibility timeout");
    assertThat(changeMessageVisibilityBatchResult.getFailed().get(0).getCode().equals("InvalidParameterValue"), "Should fail changing message visibility batch with too high a visibility timeout");
  }

  @Test
  public void testChangeMessageVisibilityBatchDeletedMessage() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeMessageVisibilityBatchDeletedMessage");
    String queueName = "queue_name_change_message_batch_visibility_deleted_message";
    String queueUrl = createQueueWithZeroDelayAndVisibilityTimeout(accountSQSClient, queueName);
    String receiptHandle = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    accountSQSClient.deleteMessage(queueUrl, receiptHandle);
    ChangeMessageVisibilityBatchRequest changeMessageVisibilityBatchRequest = new ChangeMessageVisibilityBatchRequest();
    changeMessageVisibilityBatchRequest.setQueueUrl(queueUrl);
    ChangeMessageVisibilityBatchRequestEntry changeMessageVisibilityBatchRequestEntry = new ChangeMessageVisibilityBatchRequestEntry();
    changeMessageVisibilityBatchRequestEntry.setId("id");
    changeMessageVisibilityBatchRequestEntry.setReceiptHandle(receiptHandle);
    changeMessageVisibilityBatchRequestEntry.setVisibilityTimeout(0);
    changeMessageVisibilityBatchRequest.getEntries().add(changeMessageVisibilityBatchRequestEntry);
    accountSQSClient.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);
    ChangeMessageVisibilityBatchResult changeMessageVisibilityBatchResult = accountSQSClient.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);
    assertThat(changeMessageVisibilityBatchResult.getFailed().size() == 1, "Should fail changing message visibility batch with already deleted message");
    assertThat(changeMessageVisibilityBatchResult.getFailed().get(0).getCode().equals("InvalidParameterValue"), "Should fail changing message visibility deleted message");
  }

  @Test
  public void testChangeMessageVisibilityBatchOldMessage() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeMessageVisibilityBatchOldMessage");
    String queueName = "queue_name_change_message_batch_visibility_old_message";
    String queueUrl = createQueueWithZeroDelayAndVisibilityTimeout(accountSQSClient, queueName);
    String receiptHandle1 = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    ChangeMessageVisibilityBatchRequest changeMessageVisibilityBatchRequest = new ChangeMessageVisibilityBatchRequest();
    changeMessageVisibilityBatchRequest.setQueueUrl(queueUrl);
    ChangeMessageVisibilityBatchRequestEntry changeMessageVisibilityBatchRequestEntry = new ChangeMessageVisibilityBatchRequestEntry();
    changeMessageVisibilityBatchRequestEntry.setId("id");
    changeMessageVisibilityBatchRequestEntry.setReceiptHandle(receiptHandle1);
    changeMessageVisibilityBatchRequestEntry.setVisibilityTimeout(0);
    changeMessageVisibilityBatchRequest.getEntries().add(changeMessageVisibilityBatchRequestEntry);
    accountSQSClient.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);
    String receiptHandle2 = getReceiptHandle(accountSQSClient, queueUrl, false);
    accountSQSClient.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);
    ChangeMessageVisibilityBatchResult changeMessageVisibilityBatchResult = accountSQSClient.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);
    assertThat(changeMessageVisibilityBatchResult.getFailed().size() == 1, "Should fail changing message visibility batch with an old receipt handle");
    assertThat(changeMessageVisibilityBatchResult.getFailed().get(0).getCode().equals("InvalidParameterValue"), "Should fail changing message visibility batch with an old receipt handle");
  }

  private String createQueueWithZeroDelayAndVisibilityTimeout(AmazonSQS sqs, String queueName) {
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);
    createQueueRequest.getAttributes().put("DelaySeconds","0");
    createQueueRequest.getAttributes().put("VisibilityTimeout", "0");
    return sqs.createQueue(createQueueRequest).getQueueUrl();
  }

  private String sendMessageAndGetReceiptHandle(AmazonSQS sqs, String queueUrl) throws InterruptedException {
    sqs.sendMessage(queueUrl, "bob");
    return getReceiptHandle(sqs, queueUrl, true);
  }

  private String getReceiptHandle(AmazonSQS sqs, String queueUrl, boolean failIfNone) throws InterruptedException {
    long startTime = System.currentTimeMillis();
    while ((System.currentTimeMillis() - startTime) < 5000L) {
      ReceiveMessageResult receiveMessageResult = sqs.receiveMessage(queueUrl);
      if (receiveMessageResult != null && receiveMessageResult.getMessages() != null && receiveMessageResult.getMessages().size() > 0) {
        return receiveMessageResult.getMessages().get(0).getReceiptHandle();
      }
      Thread.sleep(1000L);
    }
    if (failIfNone) {
      throw new InterruptedException("timeout");
    }
    return null;
  }

  @Test
  public void testChangeMessageVisibilityBatchTooFewMessages() {
    String queueName = "queue_name_change_message_visibility_batch_no_messages";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    ChangeMessageVisibilityBatchRequest changeMessageVisibilityBatchRequest = new ChangeMessageVisibilityBatchRequest();
    changeMessageVisibilityBatchRequest.setQueueUrl(queueUrl);
    try {
      accountSQSClient.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);
      assertThat(false, "Should fail changing message visibility batch with no messages");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Should fail changing message visibility batch with no messages");
    }
  }

  @Test
  public void testChangeMessageVisibilityBatchTooManyMessages() throws InterruptedException {
    String queueName = "queue_name_change_message_visibility_batch_too_many_messages";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    ChangeMessageVisibilityBatchRequest changeMessageVisibilityBatchRequest = new ChangeMessageVisibilityBatchRequest();
    for (int i=0;i<MAX_NUM_BATCH_ENTRIES + 1; i++) {
      ChangeMessageVisibilityBatchRequestEntry changeMessageVisibilityBatchRequestEntry = new ChangeMessageVisibilityBatchRequestEntry();
      changeMessageVisibilityBatchRequestEntry.setId("id" + i);
      String receiptHandle = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
      changeMessageVisibilityBatchRequestEntry.setReceiptHandle(receiptHandle);
      changeMessageVisibilityBatchRequestEntry.setVisibilityTimeout(0);
      changeMessageVisibilityBatchRequest.getEntries().add(changeMessageVisibilityBatchRequestEntry);
    }
    changeMessageVisibilityBatchRequest.setQueueUrl(queueUrl);
    try {
      accountSQSClient.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);
      assertThat(false, "Should fail batch sending with too many messages");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with too many messages");
    }
  }

  @Test
  public void testChangeMessageVisibilityBatchId() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeMessageVisibilityBatchId");
    String queueName = "queue_name_change_message_visibility_batch_id";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();

    ChangeMessageVisibilityBatchRequest changeMessageVisibilityBatchRequest = new ChangeMessageVisibilityBatchRequest();
    ChangeMessageVisibilityBatchRequestEntry changeMessageVisibilityBatchRequestEntry = new ChangeMessageVisibilityBatchRequestEntry();
    String receiptHandle = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    changeMessageVisibilityBatchRequestEntry.setReceiptHandle(receiptHandle);
    changeMessageVisibilityBatchRequest.getEntries().add(changeMessageVisibilityBatchRequestEntry);
    changeMessageVisibilityBatchRequest.setQueueUrl(queueUrl);

    // test null
    changeMessageVisibilityBatchRequestEntry.setId(null);
    try {
      accountSQSClient.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);
      assertThat(false, "Should fail batch changing message visibility batch with a batch id that is null");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch changing message visibility batch with a batch id that is null");
    }

    // test empty
    changeMessageVisibilityBatchRequestEntry.setId("");
    try {
      accountSQSClient.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);
      assertThat(false, "Should fail batch changing message visibility batch with a batch id that is empty");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch changing message visibility batch with a batch id that is empty");
    }

    // test too long
    changeMessageVisibilityBatchRequestEntry.setId(Strings.repeat("X", MAX_BATCH_ID_LENGTH + 1));
    try {
      accountSQSClient.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);
      assertThat(false, "Should fail changing message visibility batch with a batch id that is too long");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch changing message visibility batch with a batch id that is too long");
    }

    // test invalid characters
    changeMessageVisibilityBatchRequestEntry.setId("!@#$%^&*()");
    try {
      accountSQSClient.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);
      assertThat(false, "Should fail batch changing message visibility batch with a batch id that contains invalid characters");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch changing message visibility batch with a batch id that contains invalid characters");
    }

    // test duplicate keys
    changeMessageVisibilityBatchRequest = new ChangeMessageVisibilityBatchRequest();
    ChangeMessageVisibilityBatchRequestEntry changeMessageVisibilityBatchRequestEntry0 = new ChangeMessageVisibilityBatchRequestEntry();
    changeMessageVisibilityBatchRequestEntry0.setId("id0");
    String receiptHandle0 = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    changeMessageVisibilityBatchRequestEntry.setReceiptHandle(receiptHandle0);
    changeMessageVisibilityBatchRequest.getEntries().add(changeMessageVisibilityBatchRequestEntry0);
    ChangeMessageVisibilityBatchRequestEntry changeMessageVisibilityBatchRequestEntry1 = new ChangeMessageVisibilityBatchRequestEntry();
    changeMessageVisibilityBatchRequestEntry1.setId("id0");
    String receiptHandle1 = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    changeMessageVisibilityBatchRequestEntry.setReceiptHandle(receiptHandle1);
    changeMessageVisibilityBatchRequest.getEntries().add(changeMessageVisibilityBatchRequestEntry1);
    changeMessageVisibilityBatchRequest.setQueueUrl(queueUrl);

    try {
      accountSQSClient.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);
      assertThat(false, "Should fail changing message visibility batch with duplicate ids");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail changing message visibility batch with duplicate ids");
    }
  }

  @Test
  public void testChangeMessageVisibilityBatchSuccess() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeMessageVisibilityBatchSuccess");
    String queueName = "queue_name_change_message_visibility_batch_success";
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);
    createQueueRequest.getAttributes().put("VisibilityTimeout", "0");
    String queueUrl = accountSQSClient.createQueue(createQueueRequest).getQueueUrl();

    Set<String> messageIds = Sets.newHashSet();
    for (int i = 0; i < MAX_NUM_BATCH_ENTRIES; i++) {
      messageIds.add(accountSQSClient.sendMessage(queueUrl, "hello").getMessageId());
    }
    Map<String, String> receiptHandles = Maps.newHashMap();
    Map<String, Long> lastReceivedTimes = Maps.newHashMap();
    ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
    receiveMessageRequest.setQueueUrl(queueUrl);
    receiveMessageRequest.setMaxNumberOfMessages(MAX_RECEIVE_MESSAGE_MAX_NUMBER_OF_MESSAGES);
    long startTimeFirstLoop = System.currentTimeMillis();
    while (receiptHandles.size() < MAX_NUM_BATCH_ENTRIES && System.currentTimeMillis() - startTimeFirstLoop < 120000L) {
      ReceiveMessageResult receiveMessageResult = accountSQSClient.receiveMessage(receiveMessageRequest);
      if (receiveMessageResult != null && receiveMessageResult.getMessages() != null) {
        for (Message message : receiveMessageResult.getMessages()) {
          receiptHandles.put(message.getMessageId(), message.getReceiptHandle());
          lastReceivedTimes.put(message.getMessageId(), System.currentTimeMillis() / 1000L);
        }
      }
    }
    assertThat(receiptHandles.size() == MAX_NUM_BATCH_ENTRIES && lastReceivedTimes.size() == MAX_NUM_BATCH_ENTRIES, "We should receive all the messages in a timely manner");

    int spacingSecs = 15;
    Map<String, Long> visibilityTimeouts = Maps.newHashMap();
    ChangeMessageVisibilityBatchRequest changeMessageVisibilityBatchRequest = new ChangeMessageVisibilityBatchRequest();
    changeMessageVisibilityBatchRequest.setQueueUrl(queueUrl);
    int i = 0;
    for (Map.Entry<String, String> mapEntry: receiptHandles.entrySet()) {
      ChangeMessageVisibilityBatchRequestEntry e = new ChangeMessageVisibilityBatchRequestEntry();
      i++;
      e.setVisibilityTimeout(i * spacingSecs);
      visibilityTimeouts.put(mapEntry.getKey(), i * spacingSecs * 1L);
      e.setReceiptHandle(mapEntry.getValue());
      e.setId(mapEntry.getKey());
      changeMessageVisibilityBatchRequest.getEntries().add(e);
    }
    ChangeMessageVisibilityBatchResult changeMessageVisibilityBatchResult =
      accountSQSClient.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);

    assertThat(changeMessageVisibilityBatchResult.getSuccessful().size() == MAX_NUM_BATCH_ENTRIES,
      "Should have successfully changed all visibilities");

    Map<String, Long> nextReceivedTimes = Maps.newHashMap();
    long startTimeSecondLoop = System.currentTimeMillis();
    while (nextReceivedTimes.size() < MAX_NUM_BATCH_ENTRIES && System.currentTimeMillis() - startTimeSecondLoop < spacingSecs * 1000 * MAX_NUM_BATCH_ENTRIES + 120000L) {
      ReceiveMessageResult receiveMessageResult = accountSQSClient.receiveMessage(receiveMessageRequest);
      if (receiveMessageResult != null && receiveMessageResult.getMessages() != null) {
        for (Message message : receiveMessageResult.getMessages()) {
          if (!nextReceivedTimes.containsKey(message.getMessageId())) {
            nextReceivedTimes.put(message.getMessageId(), System.currentTimeMillis() / 1000L);
          }
        }
      }
      Thread.sleep(1000L);
    }

    assertThat(nextReceivedTimes.size() == MAX_NUM_BATCH_ENTRIES, "We should receive all the messages in a timely manner");
    int errorSecs = 5;
    for (String messageId: nextReceivedTimes.keySet()) {
      assertThat(Math.abs(visibilityTimeouts.get(messageId) - (nextReceivedTimes.get(messageId) - lastReceivedTimes.get(messageId))) < errorSecs, "Visibility timeout should match how long it takes to see the message again" );
    }
  }

  private int getLocalConfigInt(String propertySuffixInCapsAndUnderscores) throws IOException {
    String propertyName = "services.simplequeue." + propertySuffixInCapsAndUnderscores.toLowerCase();
    return Integer.parseInt(getConfigProperty(LOCAL_EUCTL_FILE, propertyName));
  }

}
