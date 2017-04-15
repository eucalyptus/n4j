package com.eucalyptus.tests.awssdk;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.eucalyptus.tests.awssdk.N4j.assertThat;
import static com.eucalyptus.tests.awssdk.N4j.getCloudInfoAndSqs;
import static com.eucalyptus.tests.awssdk.N4j.getSqsClientWithNewAccount;
import static com.eucalyptus.tests.awssdk.N4j.print;
import static com.eucalyptus.tests.awssdk.N4j.testInfo;

/**
 * Created by ethomas on 10/4/16.
 */
public class TestSQSVisibilityTimeout {

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
  public void testVisibilityTimeout() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testVisibilityTimeout");
    String queueName = "queue_name_message_delay";
    int errorSecs = 5;

    // start with a delay seconds of 15 seconds
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);
    createQueueRequest.getAttributes().put("VisibilityTimeout", "15");
    String queueUrl = accountSQSClient.createQueue(createQueueRequest).getQueueUrl();

    String messageId = accountSQSClient.sendMessage(queueUrl, "hello").getMessageId();
    String receiptHandle = waitUntilReceiveMessage(accountSQSClient, queueUrl, messageId);
    long firstReceiveTime = System.currentTimeMillis() / 1000L;
    // wait until we get a message id
    receiptHandle = waitUntilReceiveMessage(accountSQSClient, queueUrl, messageId);
    long secondReceiveTime = System.currentTimeMillis() / 1000L;
    assertThat(Math.abs(15 - (secondReceiveTime - firstReceiveTime)) < errorSecs, "Should receive the second time around 15 secs after the first");

    // change the delay seconds (shouldn't affect the current time)
    accountSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("VisibilityTimeout", "30"));
    receiptHandle = waitUntilReceiveMessage(accountSQSClient, queueUrl, messageId);
    long thirdReceiveTime = System.currentTimeMillis() / 1000L;
    assertThat(Math.abs(15 - (thirdReceiveTime - secondReceiveTime)) < errorSecs, "Should receive the third time around 15 secs after the second");

    // test override
    receiptHandle = waitUntilReceiveMessage(accountSQSClient, queueUrl, messageId, 15);
    long fourthReceiveTime = System.currentTimeMillis() / 1000L;
    assertThat(Math.abs(30 - (fourthReceiveTime - thirdReceiveTime)) < errorSecs, "Should receive the fourth time around 30 secs after the third");

    receiptHandle = waitUntilReceiveMessage(accountSQSClient, queueUrl, messageId);
    long fifthReceiveTime = System.currentTimeMillis() / 1000L;
    assertThat(Math.abs(15 - (fifthReceiveTime - fourthReceiveTime)) < errorSecs, "Should receive the fifth time around 30 secs after the fourth");

    Thread.sleep(25000L);
    // test change message visibility
    accountSQSClient.changeMessageVisibility(queueUrl, receiptHandle, 45);
    long startChangeMessageVisibility = System.currentTimeMillis() / 1000L;
    receiptHandle = waitUntilReceiveMessage(accountSQSClient, queueUrl, messageId);
    long sixthReceiveTime = System.currentTimeMillis() / 1000L;
    assertThat(Math.abs(45 - (sixthReceiveTime - startChangeMessageVisibility)) < errorSecs, "Should receive the sixth time around 45 secs after changing the message availability");
  }

  private String waitUntilReceiveMessage(AmazonSQS sqsClient, String queueUrl, String messageId) throws InterruptedException {
    return waitUntilReceiveMessage(sqsClient, queueUrl, messageId, null);
  }

  private String waitUntilReceiveMessage(AmazonSQS sqsClient, String queueUrl, String messageId, Integer overrideVisibilityTimeout) throws InterruptedException {
    for (int i = 0; i < 60; i++) {
      ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
      if (overrideVisibilityTimeout != null) {
        receiveMessageRequest.setVisibilityTimeout(overrideVisibilityTimeout);
      }
      receiveMessageRequest.setQueueUrl(queueUrl);
      ReceiveMessageResult receiveMessageResult = sqsClient.receiveMessage(receiveMessageRequest);
      if (receiveMessageResult != null && receiveMessageResult.getMessages() != null) {
        for (Message message: receiveMessageResult.getMessages()) {
          if (message.getMessageId().equals(messageId)) {
            return message.getReceiptHandle();
          }
        }
      }
      Thread.sleep(1000L);
    }
    throw new InterruptedException("timeout waiting for message");
  }

}
