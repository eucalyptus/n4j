package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.URL;
import java.util.Collection;
import java.util.List;

import static com.eucalyptus.tests.awssdk.N4j.assertThat;
import static com.eucalyptus.tests.awssdk.N4j.getCloudInfoAndSqs;
import static com.eucalyptus.tests.awssdk.N4j.getSqsClientWithNewAccount;
import static com.eucalyptus.tests.awssdk.N4j.print;
import static com.eucalyptus.tests.awssdk.N4j.testInfo;

/**
 * Created by ethomas on 9/21/16.
 */
public class TestSQSDeleteMessage {

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
  public void testDeleteMessageNonExistentAccount() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteMessageNonExistentAccount");
    String queueName = "queue_name_delete_message_nonexistent_account";
    String queueUrl = createQueueWithZeroDelayAndVisibilityTimeout(accountSQSClient, queueName);
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);
    String receiptHandle = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    try {
      accountSQSClient.deleteMessage(queueUrl.replace(accountId, "000000000000"), receiptHandle);
      assertThat(false, "Should fail deleting message on a queue from a non-existent user");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 404, "Correctly fail deleting a message on a queue from a non-existent user");
    }
  }

  @Test
  public void testDeleteMessageOtherAccount() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteMessageOtherAccount");
    String queueName = "queue_name_delete_message_other_account";
    String queueUrl = createQueueWithZeroDelayAndVisibilityTimeout(accountSQSClient, queueName);
    String otherAccountQueueUrl = createQueueWithZeroDelayAndVisibilityTimeout(otherAccountSQSClient, queueName);
    String receiptHandle = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    try {
      accountSQSClient.deleteMessage(otherAccountQueueUrl, receiptHandle);
      assertThat(false, "Should fail deleting a message on a queue from a different user");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail deleting a message on a queue from a different user");
    }
  }

  @Test
  public void testDeleteMessageNonExistentQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteMessageNonExistentQueue");
    String queueName = "queue_name_delete_message_nonexistent_queue";
    String queueUrl = createQueueWithZeroDelayAndVisibilityTimeout(accountSQSClient, queueName);
    String receiptHandle = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    try {
      accountSQSClient.deleteMessage(queueUrl + "-bogus", receiptHandle);
      assertThat(false, "Should fail deleting a message on non-existent queue");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail deleting a message on non-existent queue");
    }
  }

  @Test
  public void testDeleteMessageBogusReceiptHandles() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteMessageBogusReceiptHandles");
    String queueName = "queue_name_delete_message_bogus_rh";
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
    for (String bogusReceiptHandle: bogusReceiptHandles) {
      try {
        accountSQSClient.deleteMessage(queueUrl, bogusReceiptHandle);
        assertThat(false, "Should fail deleting messages with bogus receipt handles");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 404, "Correctly fail deleting messages with bogus receipt handles");
      }
    }
  }

  @Test
  public void testDeleteSubsequentReceiptHandles() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteSubsequentReceiptHandles");
    String queueName = "queue_name_delete_message_subsequent_rh";
    String queueUrl = createQueueWithZeroDelayAndVisibilityTimeout(accountSQSClient, queueName);

    String receiptHandle1 = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    String receiptHandle2 = getReceiptHandle(accountSQSClient, queueUrl, false);
    assertThat(receiptHandle2 != null, "Haven't deleted message yet");
    accountSQSClient.deleteMessage(queueUrl, receiptHandle1);
    // message should still exist
    String receiptHandle3 = getReceiptHandle(accountSQSClient, queueUrl, false);
    assertThat(receiptHandle3 != null, "Old receipt handle should not delete message");
    accountSQSClient.deleteMessage(queueUrl, receiptHandle3);
    // message should be done now
    String receiptHandle4 = getReceiptHandle(accountSQSClient, queueUrl, false);
    assertThat(receiptHandle4 == null, "Message should be gone");
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
}
