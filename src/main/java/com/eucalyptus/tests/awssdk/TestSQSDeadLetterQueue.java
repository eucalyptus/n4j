package com.eucalyptus.tests.awssdk;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Collections;

import static com.eucalyptus.tests.awssdk.N4j.assertThat;
import static com.eucalyptus.tests.awssdk.N4j.getCloudInfoAndSqs;
import static com.eucalyptus.tests.awssdk.N4j.getSqsClientWithNewAccount;
import static com.eucalyptus.tests.awssdk.N4j.print;
import static com.eucalyptus.tests.awssdk.N4j.testInfo;

/**
 * Created by ethomas on 10/6/16.
 */
public class TestSQSDeadLetterQueue {
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
  public void testDeadLetterQueueMoveMessage() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeadLetterQueueMoveMessage");
    String queuePrefix = "test_dead_letter_queue_move_message";
    String queue1Url = accountSQSClient.createQueue(queuePrefix + "1").getQueueUrl();
    String queue2Url = accountSQSClient.createQueue(queuePrefix + "2").getQueueUrl();

    // set a visibility timeout to 0 to allow quicker processing
    accountSQSClient.setQueueAttributes(queue1Url, ImmutableMap.of("VisibilityTimeout", "0"));
    accountSQSClient.setQueueAttributes(queue2Url, ImmutableMap.of("VisibilityTimeout", "0"));

    // set redrive policy
    String redrivePolicy1to2 = "{\"maxReceiveCount\":\"5\",\"deadLetterTargetArn\":\"" +
      accountSQSClient.getQueueAttributes(queue2Url, Collections.singletonList("QueueArn")).getAttributes().get("QueueArn") + "\"}";
    accountSQSClient.setQueueAttributes(queue1Url, ImmutableMap.of("RedrivePolicy", redrivePolicy1to2));

    // send a message on the first queue
    String messageId = accountSQSClient.sendMessage(queue1Url, "hello").getMessageId();

    verifyNoMessagesOnQueue(accountSQSClient, queue2Url, 5);

    // receive the message 5 times.
    for (int i = 0; i < 5; i++) {
      receiveSpecificMessage(accountSQSClient, queue1Url, messageId, 5);
    }

    // now verify no messages on the original queue
    verifyNoMessagesOnQueue(accountSQSClient, queue1Url, 5);

    // and receive the message on the new queue
    Message message = receiveSpecificMessage(accountSQSClient, queue2Url, messageId, 5);
    assertThat("6".equals(message.getAttributes().get("ApproximateReceiveCount")), "Should receive 6 times");
  }

  @Test
  public void testDeadLetterQueueMoveMessageTwice() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeadLetterQueueMoveMessageTwice");
    String queuePrefix = "test_dead_letter_queue_move_message_twice";
    String queue1Url = accountSQSClient.createQueue(queuePrefix + "1").getQueueUrl();
    String queue2Url = accountSQSClient.createQueue(queuePrefix + "2").getQueueUrl();
    String queue3Url = accountSQSClient.createQueue(queuePrefix + "3").getQueueUrl();

    // set a visibility timeout to 0 to allow quicker processing
    accountSQSClient.setQueueAttributes(queue1Url, ImmutableMap.of("VisibilityTimeout", "0"));
    accountSQSClient.setQueueAttributes(queue2Url, ImmutableMap.of("VisibilityTimeout", "0"));
    accountSQSClient.setQueueAttributes(queue3Url, ImmutableMap.of("VisibilityTimeout", "0"));

    // set redrive policy
    String redrivePolicy1to2 = "{\"maxReceiveCount\":\"5\",\"deadLetterTargetArn\":\"" +
      accountSQSClient.getQueueAttributes(queue2Url, Collections.singletonList("QueueArn")).getAttributes().get("QueueArn") + "\"}";
    accountSQSClient.setQueueAttributes(queue1Url, ImmutableMap.of("RedrivePolicy", redrivePolicy1to2));

    // set redrive policy
    String redrivePolicy2to3 = "{\"maxReceiveCount\":\"10\",\"deadLetterTargetArn\":\"" +
      accountSQSClient.getQueueAttributes(queue3Url, Collections.singletonList("QueueArn")).getAttributes().get("QueueArn") + "\"}";
    accountSQSClient.setQueueAttributes(queue2Url, ImmutableMap.of("RedrivePolicy", redrivePolicy2to3));

    // send a message on the first queue
    String messageId = accountSQSClient.sendMessage(queue1Url, "hello").getMessageId();

    verifyNoMessagesOnQueue(accountSQSClient, queue2Url, 5);
    verifyNoMessagesOnQueue(accountSQSClient, queue3Url, 5);

    // receive the message 5 times.
    for (int i = 0; i < 5; i++) {
      receiveSpecificMessage(accountSQSClient, queue1Url, messageId, 5);
    }

    verifyNoMessagesOnQueue(accountSQSClient, queue1Url, 5);
    verifyNoMessagesOnQueue(accountSQSClient, queue3Url, 5);

    // receive the message 10 times.
    for (int i = 0; i < 10; i++) {
      receiveSpecificMessage(accountSQSClient, queue2Url, messageId, 5);
    }
    verifyNoMessagesOnQueue(accountSQSClient, queue1Url, 5);
    verifyNoMessagesOnQueue(accountSQSClient, queue2Url, 5);
    Message message = receiveSpecificMessage(accountSQSClient, queue3Url, messageId, 60);
    assertThat("16".equals(message.getAttributes().get("ApproximateReceiveCount")), "Should receive 6 times");

  }

  @Test
  public void testDeadLetterQueueMoveOnlyIfExists() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeadLetterQueueMoveOnlyIfExists");
    String queuePrefix = "test_dead_letter_queue_move_only_if_exists";
    String queue1Url = accountSQSClient.createQueue(queuePrefix + "1").getQueueUrl();
    String queue2Url = accountSQSClient.createQueue(queuePrefix + "2").getQueueUrl();

    // set a visibility timeout to 0 to allow quicker processing
    accountSQSClient.setQueueAttributes(queue1Url, ImmutableMap.of("VisibilityTimeout", "0"));
    accountSQSClient.setQueueAttributes(queue2Url, ImmutableMap.of("VisibilityTimeout", "0"));

    // set redrive policy
    String redrivePolicy1to2 = "{\"maxReceiveCount\":\"5\",\"deadLetterTargetArn\":\"" +
      accountSQSClient.getQueueAttributes(queue2Url, Collections.singletonList("QueueArn")).getAttributes().get("QueueArn") + "\"}";
    accountSQSClient.setQueueAttributes(queue1Url, ImmutableMap.of("RedrivePolicy", redrivePolicy1to2));

    // send a message on the first queue
    String messageId = accountSQSClient.sendMessage(queue1Url, "hello").getMessageId();

    verifyNoMessagesOnQueue(accountSQSClient, queue2Url, 5);

    // receive the message 4 times.
    for (int i = 0; i < 4; i++) {
      receiveSpecificMessage(accountSQSClient, queue1Url, messageId, 5);
    }

    // delete the dead letter queue
    accountSQSClient.deleteQueue(queue2Url);
    // receive the message 4 more times (no queue to move it to)
    for (int i = 0; i < 4; i++) {
      receiveSpecificMessage(accountSQSClient, queue1Url, messageId, 5);
    }
    // recreate second queue
    queue2Url = accountSQSClient.createQueue(queuePrefix + "2").getQueueUrl();
    accountSQSClient.setQueueAttributes(queue2Url, ImmutableMap.of("VisibilityTimeout", "0"));


    // now verify no messages on the original queue
    verifyNoMessagesOnQueue(accountSQSClient, queue1Url, 5);

    // and receive the message on the new queue
    Message message = receiveSpecificMessage(accountSQSClient, queue2Url, messageId, 5);
    assertThat("9".equals(message.getAttributes().get("ApproximateReceiveCount")), "Should receive 9 times");
  }

  private Message receiveSpecificMessage(AmazonSQS sqsClient, String queueUrl, String messageId, int maxTries) throws InterruptedException {
    for (int i=0;i<maxTries;i++) {
      ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
      receiveMessageRequest.setAttributeNames(Collections.singleton("All"));
      receiveMessageRequest.setQueueUrl(queueUrl);
      ReceiveMessageResult receiveMessageResult = sqsClient.receiveMessage(receiveMessageRequest);
      if (receiveMessageResult != null && receiveMessageResult.getMessages() != null) {
        for (Message message: receiveMessageResult.getMessages()) {
          if (message.getMessageId().equals(messageId)) return message;
        }
      }
      Thread.sleep(1000L);
    }
    assertThat(false, "Should have received a specific message");
    return null;
  }

  private void verifyNoMessagesOnQueue(AmazonSQS sqsClient, String queueUrl, int maxTries) throws InterruptedException {
    for (int i=0;i<maxTries;i++) {
      ReceiveMessageResult receiveMessageResult = sqsClient.receiveMessage(queueUrl);
      assertThat(!(receiveMessageResult != null && receiveMessageResult.getMessages() != null &&
        !receiveMessageResult.getMessages().isEmpty()), "Should not have any messages on the queue currently");
      Thread.sleep(1000L);
    }
  }

}
