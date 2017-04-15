package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sqs.AmazonSQS;
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
public class TestSQSDeleteQueue {

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
  public void testDeleteQueueOtherAccount() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteQueueOtherAccount");
    String queueName = "queue_name_delete_other_account";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    String otherAccountQueueUrl = otherAccountSQSClient.createQueue(queueName).getQueueUrl();
    try {
      accountSQSClient.deleteQueue(otherAccountQueueUrl);
      assertThat(false, "Should fail deleting queue on different account");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail deleting queue on different account");
    }
  }

  @Test
  public void testDeleteQueueNonExistentAccount() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteQueueNonExistentAccount");
    String queueName = "queue_name_delete_nonexistent_account";

    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);

    try {
      accountSQSClient.deleteQueue(queueUrl.replace(accountId, "000000000000"));
      assertThat(false, "Should fail deleting queue non-existent user");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 404, "Correctly fail deleting a queue from a non-existent user");
    }
  }

  @Test
  public void testDeleteNonExistentQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteNonExistentQueue");
    String queueName = "queue_name_delete_nonexistent_queue";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    try {
      accountSQSClient.deleteQueue(queueUrl + "-bogus");
      assertThat(false, "Should fail deleting non-existent queue");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail deleting non-existent queue");
    }
  }

  @Test
  public void testDeleteQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteQueue");
    String queueName = "queue_name_delete";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    accountSQSClient.deleteQueue(queueUrl);
  }

  @Test
  public void testDeleteQueueWithMessages() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteQueueWithMessages");
    String queueName = "queue_name_delete_with_messages";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    accountSQSClient.sendMessage(queueUrl,"1");
    accountSQSClient.sendMessage(queueUrl,"2");
    accountSQSClient.sendMessage(queueUrl,"3");
    accountSQSClient.deleteQueue(queueUrl);
  }

  @Test
  public void testDeleteAlreadyDeletedQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteAlreadyDeletedQueue");
    String queueName = "queue_name_delete_already_deleted_queue";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    accountSQSClient.deleteQueue(queueUrl);
    try {
      accountSQSClient.deleteQueue(queueUrl);
      assertThat(false, "Should fail deleting already deleted queue");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail deleting already deleted queue");
    }
  }

}
