package com.eucalyptus.tests.awssdk;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequest;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.SendMessageBatchResult;
import com.amazonaws.services.sqs.model.SendMessageBatchResultEntry;
import com.beust.jcommander.internal.Maps;
import com.google.common.collect.ImmutableList;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.internal.annotations.Sets;

import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import static com.eucalyptus.tests.awssdk.N4j.CW_ENDPOINT;
import static com.eucalyptus.tests.awssdk.N4j.LOCAL_EUCTL_FILE;
import static com.eucalyptus.tests.awssdk.N4j.SQS_ENDPOINT;
import static com.eucalyptus.tests.awssdk.N4j.assertThat;
import static com.eucalyptus.tests.awssdk.N4j.getCloudInfoAndSqs;
import static com.eucalyptus.tests.awssdk.N4j.getConfigProperty;
import static com.eucalyptus.tests.awssdk.N4j.getUserCreds;
import static com.eucalyptus.tests.awssdk.N4j.print;
import static com.eucalyptus.tests.awssdk.N4j.testInfo;

/**
 * Created by ethomas on 9/21/16.
 */
public class TestSQSCloudWatchMetrics {

  private String account;

  private AmazonSQS accountSQSClient;
  private AmazonCloudWatch accountCWClient;

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
      accountCWClient = new AmazonCloudWatchClient(
        new BasicAWSCredentials(creds.getAWSAccessKeyId(), creds.getAWSSecretKey())
      );
      accountCWClient.setEndpoint(CW_ENDPOINT);
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
  }

  @Test
  public void testCloudWatchMetrics() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testCloudWatchMetrics");
    assertThat("true".equalsIgnoreCase(getConfigProperty(LOCAL_EUCTL_FILE, "services.simplequeue.enable_metrics_collection")), "Metric collection needs to be enabled");
    String queueName = "queue_name_test_cloudwatch_metrics";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();

    // let's put some stats in
    // A bunch of empty receives
    final int MIN_EMPTY_RECEIVES = 10;
    for (int i=0;i<MIN_EMPTY_RECEIVES; i++) {
      accountSQSClient.receiveMessage(queueUrl);
    }

    // send a bunch of messages to delete, via batch and in single messages
    // send individually
    final int NUM_MESSAGES_TO_SEND_1 = 6;
    String body = "This is a message body";
    Set<String> firstMessageIds = Sets.newHashSet();
    for (int i=0;i<NUM_MESSAGES_TO_SEND_1; i++) {
      firstMessageIds.add(accountSQSClient.sendMessage(queueUrl, body).getMessageId());
    }
    // send batch
    Set<String> secondMessageIds = Sets.newHashSet();
    final int NUM_MESSAGES_TO_SEND_2 = 7;
    SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest();
    sendMessageBatchRequest.setQueueUrl(queueUrl);
    for (int i=0;i<NUM_MESSAGES_TO_SEND_2;i++) {
      SendMessageBatchRequestEntry sendMessageBatchRequestEntry = new SendMessageBatchRequestEntry();
      sendMessageBatchRequestEntry.setMessageBody(body);
      sendMessageBatchRequestEntry.setId("id-" + i);
      sendMessageBatchRequest.getEntries().add(sendMessageBatchRequestEntry);
    }
    SendMessageBatchResult sendMessageBatchResult = accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
    for (SendMessageBatchResultEntry sendMessageBatchResultEntry: sendMessageBatchResult.getSuccessful()) {
      secondMessageIds.add(sendMessageBatchResultEntry.getMessageId());
    }
    Map<String, String> firstTwoSetsOfReceiptHandles = Maps.newHashMap();
    // now receive all of these messages
    final long TIMEOUT = 2 * 60 * 1000L; // two minute timeout
    long startTime = System.currentTimeMillis();
    while (firstTwoSetsOfReceiptHandles.keySet().size() < NUM_MESSAGES_TO_SEND_1 + NUM_MESSAGES_TO_SEND_2 && System.currentTimeMillis() - startTime < TIMEOUT) {
      ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
      receiveMessageRequest.setMaxNumberOfMessages(10);
      receiveMessageRequest.setQueueUrl(queueUrl);
      ReceiveMessageResult receiveMessageResult = accountSQSClient.receiveMessage(queueUrl);
      if (receiveMessageResult != null && receiveMessageResult.getMessages() != null) {
        for (Message message: receiveMessageResult.getMessages()) {
          firstTwoSetsOfReceiptHandles.put(message.getMessageId(), message.getReceiptHandle());
        }
      }
    }
    assertThat(firstTwoSetsOfReceiptHandles.keySet().size() == NUM_MESSAGES_TO_SEND_1 + NUM_MESSAGES_TO_SEND_2, "Should have received all messages");
    // now delete all of these messages (some in batch)
    for (String messageId: firstMessageIds) {
      accountSQSClient.deleteMessage(queueUrl, firstTwoSetsOfReceiptHandles.get(messageId));
    }
    DeleteMessageBatchRequest deleteMessageBatchRequest = new DeleteMessageBatchRequest();
    deleteMessageBatchRequest.setQueueUrl(queueUrl);
    for (String messageId: secondMessageIds) {
      DeleteMessageBatchRequestEntry deleteMessageBatchRequestEntry = new DeleteMessageBatchRequestEntry();
      deleteMessageBatchRequestEntry.setId(messageId);
      deleteMessageBatchRequestEntry.setReceiptHandle(firstTwoSetsOfReceiptHandles.get(messageId));
      deleteMessageBatchRequest.getEntries().add(deleteMessageBatchRequestEntry);
    }
    accountSQSClient.deleteMessageBatch(deleteMessageBatchRequest);

    // create a bunch of messages that will ultimately go into the 'Not Visible' column
    final int NUM_MESSAGES_NOT_VISIBLE = 5;
    accountSQSClient.setQueueAttributes(queueUrl, Collections.singletonMap("VisibilityTimeout","1200"));
    Set<String> notVisibleMessageIds = Sets.newHashSet();
    for (int i=0;i<NUM_MESSAGES_NOT_VISIBLE; i++) {
      notVisibleMessageIds.add(accountSQSClient.sendMessage(queueUrl, body).getMessageId());
    }
    Date oldestMessageTimestamp = new Date();  // oldest message should be sent about here.
    startTime = System.currentTimeMillis();
    while (!notVisibleMessageIds.isEmpty() && System.currentTimeMillis() - startTime < TIMEOUT) {
      ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
      receiveMessageRequest.setMaxNumberOfMessages(10);
      receiveMessageRequest.setQueueUrl(queueUrl);
      ReceiveMessageResult receiveMessageResult = accountSQSClient.receiveMessage(queueUrl);
      if (receiveMessageResult != null && receiveMessageResult.getMessages() != null) {
        for (Message message: receiveMessageResult.getMessages()) {
          notVisibleMessageIds.remove(message.getMessageId());
        }
      }
    }

    // now add a bunch of visible messages
    final int NUM_MESSAGES_VISIBLE = 4;
    for (int i=0;i<NUM_MESSAGES_VISIBLE; i++) {
      accountSQSClient.sendMessage(queueUrl, body);
    }
    // now add a bunch of delayed messages
    accountSQSClient.setQueueAttributes(queueUrl, Collections.singletonMap("DelaySeconds","900"));
    final int NUM_MESSAGES_DELAYED = 3;
    for (int i=0;i<NUM_MESSAGES_DELAYED; i++) {
      accountSQSClient.sendMessage(queueUrl, body);
    }
    final int NUM_SENT_MESSAGES = NUM_MESSAGES_TO_SEND_1 + NUM_MESSAGES_TO_SEND_2 + NUM_MESSAGES_NOT_VISIBLE + NUM_MESSAGES_VISIBLE + NUM_MESSAGES_DELAYED;
    final int NUM_RECEIVED_MESSAGES = NUM_MESSAGES_TO_SEND_1 + NUM_MESSAGES_TO_SEND_2 + NUM_MESSAGES_NOT_VISIBLE;
    final int NUM_DELETED_MESSAGES = NUM_MESSAGES_TO_SEND_1 + NUM_MESSAGES_TO_SEND_2;
    Thread.sleep(10 * 60 * 1000L); // get one to two more data points
    GetMetricStatisticsRequest getMetricStatisticsRequest = new GetMetricStatisticsRequest();
    getMetricStatisticsRequest.setNamespace("AWS/SQS");
    Dimension dimension = new Dimension();
    dimension.setName("QueueName");
    dimension.setValue(queueName);
    getMetricStatisticsRequest.setDimensions(Collections.singletonList(dimension));
    Date endTime = new Date();
    getMetricStatisticsRequest.setEndTime(endTime);
    getMetricStatisticsRequest.setPeriod(60);
    getMetricStatisticsRequest.setStartTime(new Date(endTime.getTime() - 900 * 1000));

    // NumberOfMessagesSent
    getMetricStatisticsRequest.setMetricName("NumberOfMessagesSent");
    getMetricStatisticsRequest.setStatistics(Collections.singletonList("Sum"));
    GetMetricStatisticsResult getMetricStatisticsResult = accountCWClient.getMetricStatistics(getMetricStatisticsRequest);
    int numMessagesSent = 0;
    for (Datapoint datapoint: getMetricStatisticsResult.getDatapoints()) {
      numMessagesSent += datapoint.getSum();
    }
    assertThat(numMessagesSent == NUM_SENT_MESSAGES, "Expected " + NUM_SENT_MESSAGES + ", got " + numMessagesSent + " for NumberOfMessagesSent");

    // SentMessageSize
    getMetricStatisticsRequest.setMetricName("SentMessageSize");
    getMetricStatisticsRequest.setStatistics(ImmutableList.of("Sum", "SampleCount"));
    getMetricStatisticsResult = accountCWClient.getMetricStatistics(getMetricStatisticsRequest);
    int sentMessageBytes = 0;
    numMessagesSent = 0;
    for (Datapoint datapoint: getMetricStatisticsResult.getDatapoints()) {
      sentMessageBytes += datapoint.getSum();
      numMessagesSent += datapoint.getSampleCount();
    }
    assertThat(sentMessageBytes == NUM_SENT_MESSAGES * body.length(), "Expected " + NUM_SENT_MESSAGES * body.length() + ", got " + sentMessageBytes + " for SentMessageSize");
    assertThat(numMessagesSent == NUM_SENT_MESSAGES, "Expected " + NUM_SENT_MESSAGES + ", got " + numMessagesSent + " for NumberOfMessagesSent");

    // NumberOfMessagesReceived
    getMetricStatisticsRequest.setMetricName("NumberOfMessagesReceived");
    getMetricStatisticsRequest.setStatistics(Collections.singletonList("Sum"));
    getMetricStatisticsResult = accountCWClient.getMetricStatistics(getMetricStatisticsRequest);
    int numMessagesReceived = 0;
    for (Datapoint datapoint: getMetricStatisticsResult.getDatapoints()) {
      numMessagesReceived += datapoint.getSum();
    }
    assertThat(numMessagesReceived == NUM_RECEIVED_MESSAGES, "Expected " + NUM_RECEIVED_MESSAGES + ", got " + numMessagesReceived + " for NumberOfMessagesReceived");

    // NumberOfMessagesDeleted
    getMetricStatisticsRequest.setMetricName("NumberOfMessagesDeleted");
    getMetricStatisticsRequest.setStatistics(Collections.singletonList("Sum"));
    getMetricStatisticsResult = accountCWClient.getMetricStatistics(getMetricStatisticsRequest);
    int numMessagesDeleted = 0;
    for (Datapoint datapoint: getMetricStatisticsResult.getDatapoints()) {
      numMessagesDeleted += datapoint.getSum();
    }
    assertThat(numMessagesDeleted == NUM_DELETED_MESSAGES, "Expected " + NUM_DELETED_MESSAGES + ", got " + numMessagesDeleted + " for NumberOfMessagesDeleted");

    // NumberOfEmptyReceives
    getMetricStatisticsRequest.setMetricName("NumberOfEmptyReceives");
    getMetricStatisticsRequest.setStatistics(Collections.singletonList("Sum"));
    getMetricStatisticsResult = accountCWClient.getMetricStatistics(getMetricStatisticsRequest);
    int numEmptyReceives = 0;
    for (Datapoint datapoint: getMetricStatisticsResult.getDatapoints()) {
      numEmptyReceives += datapoint.getSum();
    }
    assertThat(numEmptyReceives >= MIN_EMPTY_RECEIVES, "Expected " + MIN_EMPTY_RECEIVES + ", got " + numEmptyReceives + " for NumberOfEmptyReceives");

    // ApproximateNumberOfMessagesDelayed
    getMetricStatisticsRequest.setMetricName("ApproximateNumberOfMessagesDelayed");
    getMetricStatisticsRequest.setStatistics(Collections.singletonList("Sum"));
    getMetricStatisticsResult = accountCWClient.getMetricStatistics(getMetricStatisticsRequest);
    Date newestDate = null;
    int numMessagesDelayed = 0;
    for (Datapoint datapoint: getMetricStatisticsResult.getDatapoints()) {
      if (newestDate == null || newestDate.before(datapoint.getTimestamp())) {
        newestDate = datapoint.getTimestamp();
        numMessagesDelayed = (int) datapoint.getSum().doubleValue();
      }
    }
    assertThat(numMessagesDelayed == NUM_MESSAGES_DELAYED, "Expected " + NUM_MESSAGES_DELAYED + ", got " + numMessagesDelayed + " for ApproximateNumberOfMessagesDelayed");

    // ApproximateNumberOfMessagesNotVisible
    getMetricStatisticsRequest.setMetricName("ApproximateNumberOfMessagesNotVisible");
    getMetricStatisticsRequest.setStatistics(Collections.singletonList("Sum"));
    getMetricStatisticsResult = accountCWClient.getMetricStatistics(getMetricStatisticsRequest);
    newestDate = null;
    int numMessagesNotVisible = 0;
    for (Datapoint datapoint: getMetricStatisticsResult.getDatapoints()) {
      if (newestDate == null || newestDate.before(datapoint.getTimestamp())) {
        newestDate = datapoint.getTimestamp();
        numMessagesNotVisible = (int) datapoint.getSum().doubleValue();
      }
    }
    assertThat(numMessagesNotVisible == NUM_MESSAGES_NOT_VISIBLE, "Expected " + NUM_MESSAGES_NOT_VISIBLE + ", got " + numMessagesNotVisible + " for ApproximateNumberOfMessagesNotVisible");

    // ApproximateNumberOfMessagesVisible
    getMetricStatisticsRequest.setMetricName("ApproximateNumberOfMessagesVisible");
    getMetricStatisticsRequest.setStatistics(Collections.singletonList("Sum"));
    getMetricStatisticsResult = accountCWClient.getMetricStatistics(getMetricStatisticsRequest);
    newestDate = null;
    int numMessagesVisible = 0;
    for (Datapoint datapoint: getMetricStatisticsResult.getDatapoints()) {
      if (newestDate == null || newestDate.before(datapoint.getTimestamp())) {
        newestDate = datapoint.getTimestamp();
        numMessagesVisible = (int) datapoint.getSum().doubleValue();
      }
    }
    assertThat(numMessagesVisible == NUM_MESSAGES_VISIBLE, "Expected " + NUM_MESSAGES_VISIBLE + ", got " + numMessagesVisible + " for ApproximateNumberOfMessagesVisible");

    // ApproximateAgeOfOldestMessage
    getMetricStatisticsRequest.setMetricName("ApproximateAgeOfOldestMessage");
    getMetricStatisticsRequest.setStatistics(Collections.singletonList("Sum"));
    getMetricStatisticsResult = accountCWClient.getMetricStatistics(getMetricStatisticsRequest);
    newestDate = null;
    int ageOldestMessage = 0;
    int ageApproximateOldestMessage = 0;
    for (Datapoint datapoint: getMetricStatisticsResult.getDatapoints()) {
      if (newestDate == null || newestDate.before(datapoint.getTimestamp())) {
        newestDate = datapoint.getTimestamp();
        ageOldestMessage = (int) datapoint.getSum().doubleValue();
        ageApproximateOldestMessage = (int) ((newestDate.getTime() - oldestMessageTimestamp.getTime()) / 1000);
      }
    }
    assertThat(Math.abs(ageOldestMessage - ageApproximateOldestMessage) <= 300, "Expected less or equal to 300, got " +
      Math.abs(ageOldestMessage - ageApproximateOldestMessage) + " for difference between approximate and computed value for ApproximateAgeOfOldestMessage");
  }
}
