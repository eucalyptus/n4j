package com.eucalyptus.tests.awssdk;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.URL;
import java.util.Collections;
import java.util.List;

import static com.eucalyptus.tests.awssdk.N4j.IAM_ENDPOINT;
import static com.eucalyptus.tests.awssdk.N4j.SQS_ENDPOINT;
import static com.eucalyptus.tests.awssdk.N4j.assertThat;
import static com.eucalyptus.tests.awssdk.N4j.getCloudInfoAndSqs;
import static com.eucalyptus.tests.awssdk.N4j.getSqsClientWithNewAccount;
import static com.eucalyptus.tests.awssdk.N4j.getUserCreds;
import static com.eucalyptus.tests.awssdk.N4j.print;
import static com.eucalyptus.tests.awssdk.N4j.testInfo;

/**
 * Created by ethomas on 10/4/16.
 */
public class TestSQSAttributeValuesInMessages {

  private String account;
  private String otherAccount;

  private AmazonSQS accountSQSClient;
  private AmazonIdentityManagement accountIAMClient;

  private AmazonSQS otherAccountSQSClient;

  @BeforeClass
  public void init() throws Exception {
    print("### PRE SUITE SETUP - " + this.getClass().getSimpleName());

    try {
      getCloudInfoAndSqs();
      account = "sqs-account-a-" + System.currentTimeMillis();
      SQSUtils.synchronizedCreateAccount(account);
      AWSCredentials creds = getUserCreds(account, "admin");
      accountSQSClient = new AmazonSQSClient(
        new BasicAWSCredentials(creds.getAWSAccessKeyId(), creds.getAWSSecretKey())
      );
      accountSQSClient.setEndpoint(SQS_ENDPOINT);
      accountIAMClient = new AmazonIdentityManagementClient(
        new BasicAWSCredentials(creds.getAWSAccessKeyId(), creds.getAWSSecretKey())
      );
      accountIAMClient.setEndpoint(IAM_ENDPOINT);
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
  public void testAttributeValuesInMessages() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testAttributeValuesInMessages");
    String queueName = "queue_name_attributes_in_message";
    int errorSecs = 5;

    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);
    createQueueRequest.getAttributes().put("VisibilityTimeout", "0");
    String queueUrl = accountSQSClient.createQueue(createQueueRequest).getQueueUrl();

    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);


    long localQueueCreateTimeSecs = System.currentTimeMillis() / 1000;
    long remoteQueueCreateTimeSecs = Long.parseLong(
      accountSQSClient.getQueueAttributes(queueUrl, Collections.singletonList("All"))
        .getAttributes()
        .get("CreatedTimestamp")
    );
    long clockSkew = Math.abs(remoteQueueCreateTimeSecs - localQueueCreateTimeSecs);

    Thread.sleep(30000L);
    // now send a message
    String messageId = accountSQSClient.sendMessage(queueUrl, "hello").getMessageId();
    long localSendMessageTimeSecs = System.currentTimeMillis() / 1000;
    Thread.sleep(30000L);
    int numReceives = 0;
    Message lastMessage = null;
    long localFirstReceiveTimeSecs = 0; long start = System.currentTimeMillis();
    while (numReceives < 50 && System.currentTimeMillis() - start < 120000L) {
      ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
      receiveMessageRequest.setQueueUrl(queueUrl);
      receiveMessageRequest.setAttributeNames(Collections.singletonList("All"));
      ReceiveMessageResult receiveMessageResult = accountSQSClient.receiveMessage(receiveMessageRequest);
      if (receiveMessageResult != null && receiveMessageResult.getMessages() != null) {
        for (Message message : receiveMessageResult.getMessages()) {
          if (message.getMessageId().equals(messageId)) {
            numReceives++;
            if (numReceives == 1) {
              localFirstReceiveTimeSecs = System.currentTimeMillis() / 1000;
            }
            lastMessage = message;
          }
        }
      }
    }
    assertThat(Integer.parseInt(lastMessage.getAttributes().get("ApproximateReceiveCount")) == numReceives, "numReceives should match");
    assertThat(lastMessage.getAttributes().get("SenderId").equals(accountIAMClient.getUser().getUser().getUserId()), "sender id should match");
    // clock skew should be within range

    long remoteSendTimestampSecs = Long.parseLong(lastMessage.getAttributes().get("SentTimestamp"));

    assertThat(
      Math.abs(clockSkew - Math.abs(remoteSendTimestampSecs - localSendMessageTimeSecs)) < errorSecs,
      "Sender timestamp should match");

    long remoteFirstReceiveTimestampSecs = Long.parseLong(lastMessage.getAttributes().get("ApproximateFirstReceiveTimestamp"));

    assertThat(
      Math.abs(clockSkew - Math.abs(remoteFirstReceiveTimestampSecs - localFirstReceiveTimeSecs)) < errorSecs,
      "First receive timestamp should match");
  }
}
