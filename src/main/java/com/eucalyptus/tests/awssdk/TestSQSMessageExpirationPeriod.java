package com.eucalyptus.tests.awssdk;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static com.eucalyptus.tests.awssdk.N4j.assertThat;
import static com.eucalyptus.tests.awssdk.N4j.getCloudInfoAndSqs;
import static com.eucalyptus.tests.awssdk.N4j.getSqsClientWithNewAccount;
import static com.eucalyptus.tests.awssdk.N4j.print;
import static com.eucalyptus.tests.awssdk.N4j.testInfo;

/**
 * Created by ethomas on 10/4/16.
 */
public class TestSQSMessageExpirationPeriod {

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
  public void testMessageExpirationPeriod() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testMessageExpirationPeriod");
    String queueName = "queue_name_message_expiration_period";
    int errorSecs = 5;

    Map<String, String> attributeMap = new HashMap<String, String>();
    attributeMap.put("VisibilityTimeout","0");
    attributeMap.put("MessageRetentionPeriod", "75");
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setAttributes(attributeMap);
    createQueueRequest.setQueueName(queueName);
    String queueUrl = accountSQSClient.createQueue(createQueueRequest).getQueueUrl();
    String messageId1 = accountSQSClient.sendMessage(queueUrl, "hello").getMessageId();
    long startTime1Secs = System.currentTimeMillis() / 1000;
    accountSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("MessageRetentionPeriod", "60"));
    String messageId2 = accountSQSClient.sendMessage(queueUrl, "hello").getMessageId();
    long startTime2Secs = System.currentTimeMillis() / 1000;

    long endTime1Secs = startTime1Secs;
    long endTime2Secs = startTime2Secs;

    // Poll for 90 seconds
    for (int i = 0; i < 90; i++) {
      long startTimeLoop = System.currentTimeMillis();
      ReceiveMessageResult receiveMessageResult = accountSQSClient.receiveMessage(queueUrl);
      if (receiveMessageResult != null && receiveMessageResult.getMessages() != null) {
        for (Message message: receiveMessageResult.getMessages()) {
          long nowSecs = System.currentTimeMillis() / 1000;
          if (message.getMessageId().equals(messageId1)) {
            endTime1Secs = nowSecs;
          } else if (message.getMessageId().equals(messageId2)) {
            endTime2Secs = nowSecs;
          }
        }
      }
      long sleepTime = 1000 - (System.currentTimeMillis() - startTimeLoop);
      Thread.sleep(sleepTime > 0 ? sleepTime : 1L);
    }

    // first message should have been gone after 75 seconds, and the second one gone after 60 seconds
    assertThat(Math.abs(75 - (endTime1Secs - startTime1Secs)) < errorSecs, "First message should be gone after 75 seconds");
    assertThat(Math.abs(60 - (endTime2Secs - startTime2Secs)) < errorSecs, "Second message should be gone after 60 seconds");
  }

}
