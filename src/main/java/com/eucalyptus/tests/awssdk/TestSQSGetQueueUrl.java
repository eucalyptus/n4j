package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.GetQueueUrlRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.URL;
import java.util.List;

import static com.eucalyptus.tests.awssdk.N4j.assertThat;
import static com.eucalyptus.tests.awssdk.N4j.getCloudInfoAndSqs;
import static com.eucalyptus.tests.awssdk.N4j.getSqsClientWithNewAccount;
import static com.eucalyptus.tests.awssdk.N4j.print;
import static com.eucalyptus.tests.awssdk.N4j.testInfo;

/**
 * Created by ethomas on 9/21/16.
 */
public class TestSQSGetQueueUrl {

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
  public void testGetQueueUrlOtherAccount() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testGetQueueUrlOtherAccount");
    String queueName = "queue_name_get_queue_url_other_account";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    String otherAccountQueueUrl = otherAccountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(otherAccountQueueUrl).getPath()));
    String otherAccountId = pathParts.get(0);

    try {
      GetQueueUrlRequest getQueueUrlRequest = new GetQueueUrlRequest();
      getQueueUrlRequest.setQueueName(queueName);
      getQueueUrlRequest.setQueueOwnerAWSAccountId(otherAccountId);
      accountSQSClient.getQueueUrl(getQueueUrlRequest);
      assertThat(false, "Should fail getting queue url on different account");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail getting queue url on different account");
    }
  }

  @Test
  public void testGetQueueUrlNonExistentAccount() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testGetQueueUrlNonExistentAccount");
    String queueName = "queue_name_get_queue_url_nonexistent_account";

    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();

    try {
      GetQueueUrlRequest getQueueUrlRequest = new GetQueueUrlRequest();
      getQueueUrlRequest.setQueueName(queueName);
      getQueueUrlRequest.setQueueOwnerAWSAccountId("000000000000");
      accountSQSClient.getQueueUrl(getQueueUrlRequest);
      assertThat(false, "Should fail deleting queue non-existent user");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 404, "Correctly fail getting queue url from a non-existent user");
    }
  }

  @Test
  public void testGetQueueUrlNonExistentQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testGetQueueUrlNonExistentQueue");
    String queueName = "queue_name_get_queue_url_nonexistent_queue";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    try {
      accountSQSClient.getQueueUrl(queueName + "-bogus");
      assertThat(false, "Should fail getting queue url non-existent queue");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail getting queue url non-existent queue");
    }
  }

  @Test
  public void testGetQueueUrl() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testGetQueueUrl");
    String queueName = "queue_name_get_queue_url";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    assertThat(accountSQSClient.getQueueUrl(queueName).getQueueUrl().equals(queueUrl), "queue url matches created value");
  }

}
