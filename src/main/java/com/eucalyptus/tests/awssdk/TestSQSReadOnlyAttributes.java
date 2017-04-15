package com.eucalyptus.tests.awssdk;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.GetQueueAttributesResult;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.eucalyptus.tests.awssdk.N4j.assertThat;
import static com.eucalyptus.tests.awssdk.N4j.getCloudInfoAndSqs;
import static com.eucalyptus.tests.awssdk.N4j.getSqsClientWithNewAccount;
import static com.eucalyptus.tests.awssdk.N4j.print;
import static com.eucalyptus.tests.awssdk.N4j.testInfo;

/**
 * Created by ethomas on 10/4/16.
 */
public class TestSQSReadOnlyAttributes {
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
  public void testReadOnlyAttributes() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testReadOnlyAttributes");
    String queueName = "queue_name_read_only_account";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();

    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);

    GetQueueAttributesResult getQueueAttributesResult = accountSQSClient.getQueueAttributes(queueUrl, Collections.singletonList("All"));
    String queueArn = getQueueAttributesResult.getAttributes().get("QueueArn");
    assertThat(queueArn.startsWith("arn:aws:sqs:"), "Arn match (start)");
    assertThat(queueArn.endsWith(":" + accountId + ":" + queueName), "Arn match (start)");
    assertThat(numbersMatch(getQueueAttributesResult, 0, 0, 0), "Should have 0 messages at all to start");

    accountSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("VisibilityTimeout","40"));
    accountSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("MessageRetentionPeriod","60"));
    accountSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("DelaySeconds","0"));
    // add 5 'normal' messages
    Set<String> undelayedMessageIds = Sets.newHashSet();
    for (int i=0;i<5;i++) {
      undelayedMessageIds.add(accountSQSClient.sendMessage(queueUrl, "hello").getMessageId());
    }
    getQueueAttributesResult = accountSQSClient.getQueueAttributes(queueUrl, Collections.singletonList("All"));
    assertThat(numbersMatch(getQueueAttributesResult, 0, 5, 0), "Should have 5 visible messages");
    accountSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("DelaySeconds", "20"));
    for (int i=0;i<3;i++) {
      accountSQSClient.sendMessage(queueUrl, "hello").getMessageId();
    }
    getQueueAttributesResult = accountSQSClient.getQueueAttributes(queueUrl, Collections.singletonList("All"));
    assertThat(numbersMatch(getQueueAttributesResult, 3, 5, 0), "Should have 5 visible messages, 3 delayed messages");
    long start = System.currentTimeMillis(); while(!undelayedMessageIds.isEmpty() && System.currentTimeMillis() - start < 120000L) {
      ReceiveMessageResult receiveMessageResult = accountSQSClient.receiveMessage(queueUrl);
      if (receiveMessageResult != null && receiveMessageResult.getMessages() != null) {
        for (Message m : receiveMessageResult.getMessages()) {
          undelayedMessageIds.remove(m.getMessageId());
        }
      }
    }
    getQueueAttributesResult = accountSQSClient.getQueueAttributes(queueUrl, Collections.singletonList("All"));
    assertThat(numbersMatch(getQueueAttributesResult, 3, 0, 5), "Should have 5 invisible messages, 3 delayed messages");
    Thread.sleep(20000L);
    getQueueAttributesResult = accountSQSClient.getQueueAttributes(queueUrl, Collections.singletonList("All"));
    assertThat(numbersMatch(getQueueAttributesResult, 0, 3, 5), "Should have 5 invisible messages, 3 visible messages");
    Thread.sleep(20000L);
    getQueueAttributesResult = accountSQSClient.getQueueAttributes(queueUrl, Collections.singletonList("All"));
    assertThat(numbersMatch(getQueueAttributesResult, 0, 8, 0), "Should have 8 visible messages");
    Thread.sleep(20000L);
    getQueueAttributesResult = accountSQSClient.getQueueAttributes(queueUrl, Collections.singletonList("All"));
    assertThat(numbersMatch(getQueueAttributesResult, 0, 0, 0), "Should have 0 messages (all expired)");
  }

  private boolean numbersMatch(GetQueueAttributesResult getQueueAttributesResult, int delayed, int visible, int notVisible) {
    return Integer.parseInt(getQueueAttributesResult.getAttributes().get("ApproximateNumberOfMessagesDelayed")) == delayed &&
      Integer.parseInt(getQueueAttributesResult.getAttributes().get("ApproximateNumberOfMessages")) == visible &&
      Integer.parseInt(getQueueAttributesResult.getAttributes().get("ApproximateNumberOfMessagesNotVisible")) == notVisible;

  }
}
