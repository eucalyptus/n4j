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

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.List;

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
public class TestSQSChangeMessageVisibility {

  private int MAX_VISIBILITY_TIMEOUT;

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
  public void testChangeMessageVisibilityNonExistentAccount() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeMessageVisibilityNonExistentAccount");
    String queueName = "queue_name_change_message_visibility_nonexistent_account";
    String queueUrl = createQueueWithZeroDelayAndVisibilityTimeout(accountSQSClient, queueName);
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);
    String receiptHandle = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    try {
      accountSQSClient.changeMessageVisibility(queueUrl.replace(accountId, "000000000000"), receiptHandle, 0);
      assertThat(false, "Should fail deleting message on a queue from a non-existent user");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 404, "Correctly fail changing message visibility on a queue from a non-existent user");
    }
  }

  @Test
  public void testChangeMessageVisibilityOtherAccount() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeMessageVisibilityOtherAccount");
    String queueName = "queue_name_change_message_visibility_other_account";
    String queueUrl = createQueueWithZeroDelayAndVisibilityTimeout(accountSQSClient, queueName);
    String otherAccountQueueUrl = createQueueWithZeroDelayAndVisibilityTimeout(otherAccountSQSClient, queueName);
    String receiptHandle = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    try {
      accountSQSClient.changeMessageVisibility(otherAccountQueueUrl, receiptHandle, 0);
      assertThat(false, "Should fail changing message visibility on a queue from a different user");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail changing message visibility on a queue from a different user");
    }
  }

  @Test
  public void testChangeMessageVisibilityNonExistentQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeMessageVisibilityNonExistentQueue");
    String queueName = "queue_name_change_message_visibility_nonexistent_queue";
    String queueUrl = createQueueWithZeroDelayAndVisibilityTimeout(accountSQSClient, queueName);
    String receiptHandle = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    try {
      accountSQSClient.changeMessageVisibility(queueUrl + "-bogus", receiptHandle, 0);
      assertThat(false, "Should fail changing message visibility on non-existent queue");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail changing message visibility on non-existent queue");
    }
  }

  @Test
  public void testChangeMessageVisibilityReceiptHandleOtherAccount() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeMessageVisibilityNonExistentQueue");
    String queueName = "queue_name_change_message_visibility_other_account_receipt_handle";
    String queueUrl = createQueueWithZeroDelayAndVisibilityTimeout(accountSQSClient, queueName);
    String otherAccountQueueUrl = createQueueWithZeroDelayAndVisibilityTimeout(otherAccountSQSClient, queueName);
    String otherAccountReceiptHandle = sendMessageAndGetReceiptHandle(otherAccountSQSClient, otherAccountQueueUrl);
    try {
      accountSQSClient.changeMessageVisibility(queueUrl, otherAccountReceiptHandle, 0);
      assertThat(false, "Should fail changing message visibility on receipt handle from another account");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 404, "Correctly fail changing message visibility on receipt handle from another account");
    }
  }

  @Test
  public void testChangeMessageVisibilityBogusReceiptHandles() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeMessageVisibilityBogusReceiptHandles");
    String queueName = "queue_name_change_message_visibility_bogus_rh";
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
        accountSQSClient.changeMessageVisibility(queueUrl, bogusReceiptHandle, 0);
        assertThat(false, "Should fail changing message visibility with bogus receipt handles");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 404, "Correctly fail changing message visibility with bogus receipt handles");
      }
    }
  }

  @Test
  public void testChangeMessageVisibilityLowTimeout() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeMessageVisibilityLowTimeout");
    String queueName = "queue_name_change_message_visibility_low_timeout";
    String queueUrl = createQueueWithZeroDelayAndVisibilityTimeout(accountSQSClient, queueName);
    String receiptHandle = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    try {
      accountSQSClient.changeMessageVisibility(queueUrl, receiptHandle, -1);
      assertThat(false, "Should fail changing message visibility with too low a visibility timeout");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail changing message visibility with too low a visibility timeout");
    }
  }

  @Test
  public void testChangeMessageVisibilityHighTimeout() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeMessageVisibilityHighTimeout");
    String queueName = "queue_name_change_message_visibility_high_timeout";
    String queueUrl = createQueueWithZeroDelayAndVisibilityTimeout(accountSQSClient, queueName);
    String receiptHandle = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    try {
      accountSQSClient.changeMessageVisibility(queueUrl, receiptHandle, 1 + MAX_VISIBILITY_TIMEOUT);
      assertThat(false, "Should fail changing message visibility with too high a visibility timeout");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail changing message visibility with too high a visibility timeout");
    }
  }

    @Test
    public void testChangeMessageVisibilityDeletedMessage() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeMessageVisibilityDeletedMessage");
    String queueName = "queue_name_change_message_visibility_deleted_message";
    String queueUrl = createQueueWithZeroDelayAndVisibilityTimeout(accountSQSClient, queueName);
    String receiptHandle = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    accountSQSClient.deleteMessage(queueUrl, receiptHandle);
      try {
        accountSQSClient.changeMessageVisibility(queueUrl, receiptHandle, 0);
        assertThat(false, "Should fail changing message visibility with too high a visibility timeout");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 400, "Correctly fail changing message visibility with too high a visibility timeout");
      }
    }

  @Test
  public void testChangeMessageVisibilityOldMessage() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeMessageVisibilityOldMessage");
    String queueName = "queue_name_change_message_visibility_old_message";
    String queueUrl = createQueueWithZeroDelayAndVisibilityTimeout(accountSQSClient, queueName);
    String receiptHandle1 = sendMessageAndGetReceiptHandle(accountSQSClient, queueUrl);
    accountSQSClient.changeMessageVisibility(queueUrl, receiptHandle1, 0);
    String receiptHandle2 = getReceiptHandle(accountSQSClient, queueUrl, false);
    try {
      accountSQSClient.changeMessageVisibility(queueUrl, receiptHandle1, 0);
      assertThat(false, "Should fail changing message visibility with an old receipt handle");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail changing message visibility with an old receipt handle");
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
