package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.BatchResultErrorEntry;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequest;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.DeleteMessageBatchResult;
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
public class TestSQSDeleteMessageBatch {

  private String account;
  private String otherAccount;

  private AmazonSQS accountSQSClient;
  private AmazonSQS otherAccountSQSClient;

  private int MAX_NUM_BATCH_ENTRIES;
  private int MAX_BATCH_ID_LENGTH;
  private int MAX_RECEIVE_MESSAGE_MAX_NUMBER_OF_MESSAGES;

  @BeforeClass
  public void init() throws Exception {
    print("### PRE SUITE SETUP - " + this.getClass().getSimpleName());

    try {
      getCloudInfoAndSqs();
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
  public void testDeleteMessageBatchNonExistentAccount() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteMessageBatchNonExistentAccount");
    String queueName = "queue_name_delete_message_batch_nonexistent_account";
    String queueUrl = createQueueWithZeroDelayAndVisibilityTimeout(accountSQSClient, queueName);
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);
    String receiptHandle = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    DeleteMessageBatchRequest deleteMessageBatchRequest = new DeleteMessageBatchRequest();
    deleteMessageBatchRequest.setQueueUrl(queueUrl.replace(accountId, "000000000000"));
    DeleteMessageBatchRequestEntry deleteMessageBatchRequestEntry = new DeleteMessageBatchRequestEntry();
    deleteMessageBatchRequestEntry.setReceiptHandle(receiptHandle);
    deleteMessageBatchRequestEntry.setId("id");
    deleteMessageBatchRequest.getEntries().add(deleteMessageBatchRequestEntry);
    try {
      accountSQSClient.deleteMessageBatch(deleteMessageBatchRequest);
      assertThat(false, "Should fail deleting message on a queue from a non-existent user");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 404, "Correctly fail batch deleting a message on a queue from a non-existent user");
    }
  }

  @Test
  public void testDeleteMessageBatchOtherAccount() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteMessageBatchOtherAccount");
    String queueName = "queue_name_delete_message_batch_other_account";
    String queueUrl = createQueueWithZeroDelayAndVisibilityTimeout(accountSQSClient, queueName);
    String otherAccountQueueUrl = createQueueWithZeroDelayAndVisibilityTimeout(otherAccountSQSClient, queueName);
    String receiptHandle = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    DeleteMessageBatchRequest deleteMessageBatchRequest = new DeleteMessageBatchRequest();
    deleteMessageBatchRequest.setQueueUrl(otherAccountQueueUrl);
    DeleteMessageBatchRequestEntry deleteMessageBatchRequestEntry = new DeleteMessageBatchRequestEntry();
    deleteMessageBatchRequestEntry.setReceiptHandle(receiptHandle);
    deleteMessageBatchRequestEntry.setId("id");
    deleteMessageBatchRequest.getEntries().add(deleteMessageBatchRequestEntry);
    try {
      accountSQSClient.deleteMessageBatch(deleteMessageBatchRequest);
      assertThat(false, "Should fail batch deleting a message on a queue from a different user");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail batch deleting a message on a queue from a different user");
    }
  }

  @Test
  public void testDeleteMessageBatchNonExistentQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteMessageBatchNonExistentQueue");
    String queueName = "queue_name_delete_message_batch_nonexistent_queue";
    String queueUrl = createQueueWithZeroDelayAndVisibilityTimeout(accountSQSClient, queueName);
    String receiptHandle = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    DeleteMessageBatchRequest deleteMessageBatchRequest = new DeleteMessageBatchRequest();
    deleteMessageBatchRequest.setQueueUrl(queueUrl + "-bogus");
    DeleteMessageBatchRequestEntry deleteMessageBatchRequestEntry = new DeleteMessageBatchRequestEntry();
    deleteMessageBatchRequestEntry.setReceiptHandle(receiptHandle);
    deleteMessageBatchRequestEntry.setId("id");
    deleteMessageBatchRequest.getEntries().add(deleteMessageBatchRequestEntry);
    try {
      accountSQSClient.deleteMessageBatch(deleteMessageBatchRequest);
      assertThat(false, "Should fail batch deleting a message on non-existent queue");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch deleting a message on non-existent queue");
    }
  }

  @Test
  public void testDeleteMessageBatchBogusReceiptHandles() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteMessageBatchBogusReceiptHandles");
    String queueName = "queue_name_delete_message_batch_bogus_rh";
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
    DeleteMessageBatchRequest deleteMessageBatchRequest = new DeleteMessageBatchRequest();
    deleteMessageBatchRequest.setQueueUrl(queueUrl);
    int id = 0;
    for (String bogusReceiptHandle: bogusReceiptHandles) {
      DeleteMessageBatchRequestEntry deleteMessageBatchRequestEntry = new DeleteMessageBatchRequestEntry();
      deleteMessageBatchRequestEntry.setId("id" + (id++));
      deleteMessageBatchRequestEntry.setReceiptHandle(bogusReceiptHandle);
      deleteMessageBatchRequest.getEntries().add(deleteMessageBatchRequestEntry);
    }

    DeleteMessageBatchResult deleteMessageBatchResult = accountSQSClient.deleteMessageBatch(deleteMessageBatchRequest);
    assertThat(deleteMessageBatchResult.getFailed().size() == bogusReceiptHandles.size(), "Should fail batch deleting a message with bogus receipt handles");
    for (BatchResultErrorEntry batchResultErrorEntry: deleteMessageBatchResult.getFailed()) {
      assertThat(batchResultErrorEntry.getCode().equals("ReceiptHandleIsInvalid"), "Correctly fail batch deleting a message with bogus receipt handles");
    }
  }

  @Test
  public void testDeleteSubsequentReceiptHandles() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteSubsequentReceiptHandles");
    String queueName = "queue_name_delete_message_batch_subsequent_rh";
    String queueUrl = createQueueWithZeroDelayAndVisibilityTimeout(accountSQSClient, queueName);

    DeleteMessageBatchRequest deleteMessageBatchRequest = new DeleteMessageBatchRequest();
    deleteMessageBatchRequest.setQueueUrl(queueUrl);
    DeleteMessageBatchRequestEntry deleteMessageBatchRequestEntry = new DeleteMessageBatchRequestEntry();
    deleteMessageBatchRequestEntry.setId("id");
    deleteMessageBatchRequest.getEntries().add(deleteMessageBatchRequestEntry);
    String receiptHandle1 = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    String receiptHandle2 = getReceiptHandle(accountSQSClient, queueUrl, false);
    assertThat(receiptHandle2 != null, "Haven't deleted message yet");
    deleteMessageBatchRequestEntry.setReceiptHandle(receiptHandle1);
    DeleteMessageBatchResult deleteMessageBatchResult = accountSQSClient.deleteMessageBatch(deleteMessageBatchRequest);
    assertThat(deleteMessageBatchResult.getSuccessful().size() == 1, "Call should succeed");
    // message should still exist
    String receiptHandle3 = getReceiptHandle(accountSQSClient, queueUrl, false);
    assertThat(receiptHandle3 != null, "Old receipt handle should not delete message");
    deleteMessageBatchRequestEntry.setReceiptHandle(receiptHandle3);
    deleteMessageBatchResult = accountSQSClient.deleteMessageBatch(deleteMessageBatchRequest);
    assertThat(deleteMessageBatchResult.getSuccessful().size() == 1, "Call should succeed");
    // message should be done now
    String receiptHandle4 = getReceiptHandle(accountSQSClient, queueUrl, false);
    assertThat(receiptHandle4 == null, "Message should be gone");
  }

  @Test
  public void testDeleteMessageBatchTooFewMessages() {
    String queueName = "queue_name_delete_message_batch_no_messages";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    DeleteMessageBatchRequest deleteMessageBatchRequest = new DeleteMessageBatchRequest();
    deleteMessageBatchRequest.setQueueUrl(queueUrl);
    try {
      accountSQSClient.deleteMessageBatch(deleteMessageBatchRequest);
      assertThat(false, "Should fail batch deleting messages with no messages");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Should fail batch deleting messages with no messages");
    }
  }

  @Test
  public void testDeleteMessageBatchTooManyMessages() throws InterruptedException {
    String queueName = "queue_name_delete_message_batch_too_many_messages";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    DeleteMessageBatchRequest deleteMessageBatchRequest = new DeleteMessageBatchRequest();
    for (int i=0;i<MAX_NUM_BATCH_ENTRIES + 1; i++) {
      DeleteMessageBatchRequestEntry deleteMessageBatchRequestEntry = new DeleteMessageBatchRequestEntry();
      deleteMessageBatchRequestEntry.setId("id" + i);
      String receiptHandle = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
      deleteMessageBatchRequestEntry.setReceiptHandle(receiptHandle);
      deleteMessageBatchRequest.getEntries().add(deleteMessageBatchRequestEntry);
    }
    deleteMessageBatchRequest.setQueueUrl(queueUrl);
    try {
      accountSQSClient.deleteMessageBatch(deleteMessageBatchRequest);
      assertThat(false, "Should fail batch deleting with too many messages");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch deleting with too many messages");
    }
  }

  @Test
  public void testDeleteMessageBatchId() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteMessageBatchId");
    String queueName = "queue_name_delete_message_batch_id";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();

    DeleteMessageBatchRequest deleteMessageBatchRequest = new DeleteMessageBatchRequest();
    DeleteMessageBatchRequestEntry deleteMessageBatchRequestEntry = new DeleteMessageBatchRequestEntry();
    String receiptHandle = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    deleteMessageBatchRequestEntry.setReceiptHandle(receiptHandle);
    deleteMessageBatchRequest.getEntries().add(deleteMessageBatchRequestEntry);
    deleteMessageBatchRequest.setQueueUrl(queueUrl);

    // test null
    deleteMessageBatchRequestEntry.setId(null);
    try {
      accountSQSClient.deleteMessageBatch(deleteMessageBatchRequest);
      assertThat(false, "Should fail batch batch deleting messages with a batch id that is null");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch batch deleting messages with a batch id that is null");
    }

    // test empty
    deleteMessageBatchRequestEntry.setId("");
    try {
      accountSQSClient.deleteMessageBatch(deleteMessageBatchRequest);
      assertThat(false, "Should fail batch batch deleting messages with a batch id that is empty");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch batch deleting messages with a batch id that is empty");
    }

    // test too long
    deleteMessageBatchRequestEntry.setId(Strings.repeat("X", MAX_BATCH_ID_LENGTH + 1));
    try {
      accountSQSClient.deleteMessageBatch(deleteMessageBatchRequest);
      assertThat(false, "Should fail batch deleting messages with a batch id that is too long");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch batch deleting messages with a batch id that is too long");
    }

    // test invalid characters
    deleteMessageBatchRequestEntry.setId("!@#$%^&*()");
    try {
      accountSQSClient.deleteMessageBatch(deleteMessageBatchRequest);
      assertThat(false, "Should fail batch batch deleting messages with a batch id that contains invalid characters");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch batch deleting messages with a batch id that contains invalid characters");
    }

    // test duplicate keys
    deleteMessageBatchRequest = new DeleteMessageBatchRequest();
    DeleteMessageBatchRequestEntry deleteMessageBatchRequestEntry0 = new DeleteMessageBatchRequestEntry();
    deleteMessageBatchRequestEntry0.setId("id0");
    String receiptHandle0 = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    deleteMessageBatchRequestEntry.setReceiptHandle(receiptHandle0);
    deleteMessageBatchRequest.getEntries().add(deleteMessageBatchRequestEntry0);
    DeleteMessageBatchRequestEntry deleteMessageBatchRequestEntry1 = new DeleteMessageBatchRequestEntry();
    deleteMessageBatchRequestEntry1.setId("id0");
    String receiptHandle1 = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    deleteMessageBatchRequestEntry.setReceiptHandle(receiptHandle1);
    deleteMessageBatchRequest.getEntries().add(deleteMessageBatchRequestEntry1);
    deleteMessageBatchRequest.setQueueUrl(queueUrl);

    try {
      accountSQSClient.deleteMessageBatch(deleteMessageBatchRequest);
      assertThat(false, "Should fail batch deleting messages with duplicate ids");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch deleting messages with duplicate ids");
    }
  }

  @Test
  public void testDeleteMessageBatchSuccess() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteMessageBatchSuccess");
    String queueName = "queue_name_delete_message_batch_success";
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);
    createQueueRequest.getAttributes().put("VisibilityTimeout", "0");
    String queueUrl = accountSQSClient.createQueue(createQueueRequest).getQueueUrl();

    Set<String> messageIds = Sets.newHashSet();
    for (int i = 0; i < MAX_NUM_BATCH_ENTRIES; i++) {
      messageIds.add(accountSQSClient.sendMessage(queueUrl, "hello").getMessageId());
    }
    Map<String, String> receiptHandles = Maps.newHashMap();
    ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
    receiveMessageRequest.setQueueUrl(queueUrl);
    receiveMessageRequest.setMaxNumberOfMessages(MAX_RECEIVE_MESSAGE_MAX_NUMBER_OF_MESSAGES);
    long startTimeFirstLoop = System.currentTimeMillis();
    while (receiptHandles.size() < MAX_NUM_BATCH_ENTRIES && System.currentTimeMillis() - startTimeFirstLoop < 120000L) {
      ReceiveMessageResult receiveMessageResult = accountSQSClient.receiveMessage(receiveMessageRequest);
      if (receiveMessageResult != null && receiveMessageResult.getMessages() != null) {
        for (Message message : receiveMessageResult.getMessages()) {
          receiptHandles.put(message.getMessageId(), message.getReceiptHandle());
        }
      }
    }
    assertThat(receiptHandles.size() == MAX_NUM_BATCH_ENTRIES, "We should receive all the messages in a timely manner");

    String otherMessageId = accountSQSClient.sendMessage(queueUrl, "hello").getMessageId();

    DeleteMessageBatchRequest deleteMessageBatchRequest = new DeleteMessageBatchRequest();
    deleteMessageBatchRequest.setQueueUrl(queueUrl);
    for (Map.Entry<String, String> mapEntry: receiptHandles.entrySet()) {
      DeleteMessageBatchRequestEntry e = new DeleteMessageBatchRequestEntry();
      e.setReceiptHandle(mapEntry.getValue());
      e.setId(mapEntry.getKey());
      deleteMessageBatchRequest.getEntries().add(e);
    }
    DeleteMessageBatchResult deleteMessageBatchResult =
      accountSQSClient.deleteMessageBatch(deleteMessageBatchRequest);

    assertThat(deleteMessageBatchResult.getSuccessful().size() == MAX_NUM_BATCH_ENTRIES,
      "Should have successfully deleted all messages");

    long startTimeSecondLoop = System.currentTimeMillis();
    while (System.currentTimeMillis() - startTimeSecondLoop < 120000L) {
      ReceiveMessageResult receiveMessageResult = accountSQSClient.receiveMessage(receiveMessageRequest);
      if (receiveMessageResult != null && receiveMessageResult.getMessages() != null) {
        for (Message message : receiveMessageResult.getMessages()) {
          if (!message.getMessageId().equals(otherMessageId)) {
            assertThat(false, "Some other message stuck around");
          }
        }
      }
      Thread.sleep(1000L);
    }
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

  private int getLocalConfigInt(String propertySuffixInCapsAndUnderscores) throws IOException {
    String propertyName = "services.simplequeue." + propertySuffixInCapsAndUnderscores.toLowerCase();
    return Integer.parseInt(getConfigProperty(LOCAL_EUCTL_FILE, propertyName));
  }

}
