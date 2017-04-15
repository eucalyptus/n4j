package com.eucalyptus.tests.awssdk;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.SendMessageBatchResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.eucalyptus.tests.awssdk.N4j.LOCAL_EUCTL_FILE;
import static com.eucalyptus.tests.awssdk.N4j.assertThat;
import static com.eucalyptus.tests.awssdk.N4j.getCloudInfoAndSqs;
import static com.eucalyptus.tests.awssdk.N4j.getConfigProperty;
import static com.eucalyptus.tests.awssdk.N4j.getSqsClientWithNewAccount;
import static com.eucalyptus.tests.awssdk.N4j.print;
import static com.eucalyptus.tests.awssdk.N4j.sqs;
import static com.eucalyptus.tests.awssdk.N4j.testInfo;

/**
 * Created by ethomas on 9/21/16.
 */
public class TestSQSLongPolling {

  private String account;

  private AmazonSQS accountSQSClient;

  private ExecutorService pool;

  @BeforeClass
  public void init() throws Exception {
    print("### PRE SUITE SETUP - " + this.getClass().getSimpleName());

    try {
      getCloudInfoAndSqs();
      account = "sqs-account-a-" + System.currentTimeMillis();
      SQSUtils.synchronizedCreateAccount(account);
      accountSQSClient = getSqsClientWithNewAccount(account, "admin");
      pool = Executors.newFixedThreadPool(3);
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
      if (pool != null) {
        pool.shutdown();
      }
    }
  }

  private static final long NUM_SECONDS_AFTER_TIMEOUT = 10; // It can take this long for timeout to occur after specified time

  @Test
  public void testPollingTimeoutNoMessagesQueue10() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testPollingTimeoutNoMessagesQueue10");
    assertThat("true".equalsIgnoreCase(getConfigProperty(LOCAL_EUCTL_FILE, "services.simplequeue.enable_long_polling")), "Metric collection needs to be enabled");
    String queueName = "queue_name_test_polling_timeout_no_messages";
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);
    int QUEUE_WAIT_TIME = 10;
    createQueueRequest.getAttributes().put("ReceiveMessageWaitTimeSeconds", "" + QUEUE_WAIT_TIME);
    String queueUrl = accountSQSClient.createQueue(createQueueRequest).getQueueUrl();
    // verify QUEUE_WAIT_TIME seconds if no messages in queue
    long startTimeQueueCase = System.currentTimeMillis();
    sqs.receiveMessage(queueUrl);
    long endTimeQueueCase = System.currentTimeMillis();
    long durationSecsQueueCase = (endTimeQueueCase - startTimeQueueCase) / 1000L;
    assertThat(durationSecsQueueCase >= QUEUE_WAIT_TIME && durationSecsQueueCase <= QUEUE_WAIT_TIME + NUM_SECONDS_AFTER_TIMEOUT, "Expected to take between " + QUEUE_WAIT_TIME + " and " + (QUEUE_WAIT_TIME + NUM_SECONDS_AFTER_TIMEOUT) + ", took " + durationSecsQueueCase);
  }

  @Test
  public void testPollingTimeoutNoMessagesReceiveMessage5() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testPollingTimeoutNoMessagesReceiveMessage5");
    assertThat("true".equalsIgnoreCase(getConfigProperty(LOCAL_EUCTL_FILE, "services.simplequeue.enable_long_polling")), "Metric collection needs to be enabled");
    testPollingTimeoutNoMessagesReceiptDelay(5);
  }

  @Test
  public void testPollingTimeoutNoMessagesReceiveMessage10() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testPollingTimeoutNoMessagesReceiveMessage10");
    assertThat("true".equalsIgnoreCase(getConfigProperty(LOCAL_EUCTL_FILE, "services.simplequeue.enable_long_polling")), "Metric collection needs to be enabled");
    testPollingTimeoutNoMessagesReceiptDelay(10);
  }

  @Test
  public void testPollingTimeoutNoMessagesReceiveMessage15() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testPollingTimeoutNoMessagesReceiveMessage15");
    assertThat("true".equalsIgnoreCase(getConfigProperty(LOCAL_EUCTL_FILE, "services.simplequeue.enable_long_polling")), "Metric collection needs to be enabled");
    testPollingTimeoutNoMessagesReceiptDelay(15);
  }

  @Test
  public void testPollingTimeoutNoMessagesReceiveMessage20() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testPollingTimeoutNoMessagesReceiveMessage20");
    assertThat("true".equalsIgnoreCase(getConfigProperty(LOCAL_EUCTL_FILE, "services.simplequeue.enable_long_polling")), "Metric collection needs to be enabled");
    testPollingTimeoutNoMessagesReceiptDelay(20);
  }

  private void testPollingTimeoutNoMessagesReceiptDelay(int i) {
    String queueName = "queue_name_test_polling_timeout_no_messages_" + i;
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);
    int QUEUE_WAIT_TIME = 10;
    createQueueRequest.getAttributes().put("ReceiveMessageWaitTimeSeconds", "" + QUEUE_WAIT_TIME);
    String queueUrl = accountSQSClient.createQueue(createQueueRequest).getQueueUrl();
    ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
    receiveMessageRequest.setQueueUrl(queueUrl);
    receiveMessageRequest.setWaitTimeSeconds(i);
    long startTime = System.currentTimeMillis();
    accountSQSClient.receiveMessage(receiveMessageRequest);
    long endTime = System.currentTimeMillis();
    long durationSecs = (endTime - startTime) / 1000L;
    assertThat(durationSecs >= i && durationSecs <= i + NUM_SECONDS_AFTER_TIMEOUT, "Expected to take between " + i + " and " + (i  + NUM_SECONDS_AFTER_TIMEOUT) + ", took " + durationSecs);
  }

  @Test
  public void testPollingTimeoutSuccessNoDelay() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testPollingTimeoutSuccessNoDelay");
    assertThat("true".equalsIgnoreCase(getConfigProperty(LOCAL_EUCTL_FILE, "services.simplequeue.enable_long_polling")), "Metric collection needs to be enabled");
    String queueName = "queue_name_test_polling_timeout_success_no_delay";
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);
    String queueUrl = accountSQSClient.createQueue(createQueueRequest).getQueueUrl();
    long SLEEP_TIME_SECS = 5;
    long startTime = System.currentTimeMillis();
    Future<ReceiveMessageResult> receiveMessageResultFuture = pool.submit(new Callable<ReceiveMessageResult>() {
      @Override
      public ReceiveMessageResult call() throws Exception {
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
        receiveMessageRequest.setWaitTimeSeconds(20);
        receiveMessageRequest.setQueueUrl(queueUrl);
        return accountSQSClient.receiveMessage(receiveMessageRequest);
      }
    });
    Future<SendMessageResult> sendMessageResultFuture = pool.submit(new Callable<SendMessageResult>() {
      @Override
      public SendMessageResult call() throws Exception {
        Thread.sleep(SLEEP_TIME_SECS * 1000L);
        return accountSQSClient.sendMessage(queueUrl, "hello");
      }
    });
    ReceiveMessageResult receiveMessageResult = receiveMessageResultFuture.get();
    long endTime = System.currentTimeMillis();
    long durationSecs = (endTime - startTime) / 1000L;
    assertThat(durationSecs >= SLEEP_TIME_SECS && durationSecs <= SLEEP_TIME_SECS + NUM_SECONDS_AFTER_TIMEOUT, "Expected to take between " + SLEEP_TIME_SECS + " and " + (SLEEP_TIME_SECS + NUM_SECONDS_AFTER_TIMEOUT) + ", took " + durationSecs);
    sendMessageResultFuture.get();
  }

  @Test
  public void testPollingTimeoutSuccessDelayQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testPollingTimeoutSuccessDelayQueue");
    assertThat("true".equalsIgnoreCase(getConfigProperty(LOCAL_EUCTL_FILE, "services.simplequeue.enable_long_polling")), "Metric collection needs to be enabled");
    String queueName = "queue_name_test_polling_timeout_success_delay_queue";
    long QUEUE_DELAY_SECONDS = 10;
    int SEND_MESSAGE_DELAY_SECONDS = 5;
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);
    createQueueRequest.getAttributes().put("DelaySeconds", "" + QUEUE_DELAY_SECONDS);
    String queueUrl = accountSQSClient.createQueue(createQueueRequest).getQueueUrl();
    long SLEEP_TIME_SECS = 5;
    long startTime = System.currentTimeMillis();
    Future<ReceiveMessageResult> receiveMessageResultFuture = pool.submit(new Callable<ReceiveMessageResult>() {
      @Override
      public ReceiveMessageResult call() throws Exception {
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
        receiveMessageRequest.setWaitTimeSeconds(20);
        receiveMessageRequest.setQueueUrl(queueUrl);
        return accountSQSClient.receiveMessage(receiveMessageRequest);
      }
    });
    Future<SendMessageResult> sendMessageResultFuture = pool.submit(new Callable<SendMessageResult>() {
      @Override
      public SendMessageResult call() throws Exception {
        Thread.sleep(SLEEP_TIME_SECS * 1000L);
        SendMessageRequest sendMessageRequest = new SendMessageRequest();
        sendMessageRequest.setMessageBody("hello");
        sendMessageRequest.setQueueUrl(queueUrl);
        sendMessageRequest.setDelaySeconds(SEND_MESSAGE_DELAY_SECONDS);
        return accountSQSClient.sendMessage(sendMessageRequest);
      }
    });
    ReceiveMessageResult receiveMessageResult = receiveMessageResultFuture.get();
    long endTime = System.currentTimeMillis();
    long durationSecs = (endTime - startTime) / 1000L;
    assertThat(durationSecs >= SEND_MESSAGE_DELAY_SECONDS + SLEEP_TIME_SECS && durationSecs <= SEND_MESSAGE_DELAY_SECONDS + SLEEP_TIME_SECS + NUM_SECONDS_AFTER_TIMEOUT, "Expected to take between " + (SEND_MESSAGE_DELAY_SECONDS + SLEEP_TIME_SECS) + " and " + (SEND_MESSAGE_DELAY_SECONDS + SLEEP_TIME_SECS + NUM_SECONDS_AFTER_TIMEOUT) + ", took " + durationSecs);
    sendMessageResultFuture.get();
  }

  @Test
  public void testPollingTimeoutSuccessDelayMessage() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testPollingTimeoutSuccessDelayMessage");
    assertThat("true".equalsIgnoreCase(getConfigProperty(LOCAL_EUCTL_FILE, "services.simplequeue.enable_long_polling")), "Metric collection needs to be enabled");
    String queueName = "queue_name_test_polling_timeout_success_delay_message";
    long QUEUE_DELAY_SECONDS = 5;

    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);
    createQueueRequest.getAttributes().put("DelaySeconds", "" + QUEUE_DELAY_SECONDS);
    String queueUrl = accountSQSClient.createQueue(createQueueRequest).getQueueUrl();
    long SLEEP_TIME_SECS = 5;
    long startTime = System.currentTimeMillis();
    Future<ReceiveMessageResult> receiveMessageResultFuture = pool.submit(new Callable<ReceiveMessageResult>() {
      @Override
      public ReceiveMessageResult call() throws Exception {
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
        receiveMessageRequest.setWaitTimeSeconds(20);
        receiveMessageRequest.setQueueUrl(queueUrl);
        return accountSQSClient.receiveMessage(receiveMessageRequest);
      }
    });
    Future<SendMessageResult> sendMessageResultFuture = pool.submit(new Callable<SendMessageResult>() {
      @Override
      public SendMessageResult call() throws Exception {
        Thread.sleep(SLEEP_TIME_SECS * 1000L);
        return accountSQSClient.sendMessage(queueUrl, "hello");
      }
    });
    ReceiveMessageResult receiveMessageResult = receiveMessageResultFuture.get();
    long endTime = System.currentTimeMillis();
    long durationSecs = (endTime - startTime) / 1000L;
    assertThat(durationSecs >= QUEUE_DELAY_SECONDS + SLEEP_TIME_SECS && durationSecs <= QUEUE_DELAY_SECONDS + SLEEP_TIME_SECS + NUM_SECONDS_AFTER_TIMEOUT, "Expected to take between " + (QUEUE_DELAY_SECONDS + SLEEP_TIME_SECS) + " and " + (QUEUE_DELAY_SECONDS + SLEEP_TIME_SECS + NUM_SECONDS_AFTER_TIMEOUT) + ", took " + durationSecs);
    sendMessageResultFuture.get();
  }

  @Test
  public void testPollingTimeoutSuccessMultipleReceivers() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testPollingTimeoutSuccessMultipleReceivers");
    assertThat("true".equalsIgnoreCase(getConfigProperty(LOCAL_EUCTL_FILE, "services.simplequeue.enable_long_polling")), "Metric collection needs to be enabled");
    String queueName = "queue_name_test_polling_timeout_success_multiple_receivers";
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);
    String queueUrl = accountSQSClient.createQueue(createQueueRequest).getQueueUrl();
    int DELAY_1 = 5;
    int DELAY_2 = 15;
    int MAX_DELAY = Math.max(DELAY_1, DELAY_2);
    long startTime = System.currentTimeMillis();
    Future<ReceiveMessageResult> receiveMessageResultFuture1 = pool.submit(new Callable<ReceiveMessageResult>() {
      @Override
      public ReceiveMessageResult call() throws Exception {
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
        receiveMessageRequest.setWaitTimeSeconds(20);
        receiveMessageRequest.setQueueUrl(queueUrl);
        return accountSQSClient.receiveMessage(receiveMessageRequest);
      }
    });
    Future<ReceiveMessageResult> receiveMessageResultFuture2 = pool.submit(new Callable<ReceiveMessageResult>() {
      @Override
      public ReceiveMessageResult call() throws Exception {
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
        receiveMessageRequest.setWaitTimeSeconds(20);
        receiveMessageRequest.setQueueUrl(queueUrl);
        return accountSQSClient.receiveMessage(receiveMessageRequest);
      }
    });
    Future<SendMessageBatchResult> sendMessageBatchResultFuture = pool.submit(new Callable<SendMessageBatchResult>() {
      @Override
      public SendMessageBatchResult call() throws Exception {
        SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest();
        sendMessageBatchRequest.setQueueUrl(queueUrl);
        SendMessageBatchRequestEntry entry1 = new SendMessageBatchRequestEntry();
        entry1.setId("id1");
        entry1.setMessageBody("hello");
        entry1.setDelaySeconds(DELAY_1);
        sendMessageBatchRequest.getEntries().add(entry1);
        SendMessageBatchRequestEntry entry2 = new SendMessageBatchRequestEntry();
        entry2.setId("id2");
        entry2.setMessageBody("hello");
        entry2.setDelaySeconds(DELAY_2);
        sendMessageBatchRequest.getEntries().add(entry2);
        return accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      }
    });
    ReceiveMessageResult receiveMessageResult1 = receiveMessageResultFuture1.get();
    ReceiveMessageResult receiveMessageResult2 = receiveMessageResultFuture2.get();
    long endTime = System.currentTimeMillis();
    long durationSecs = (endTime - startTime) / 1000L;
    assertThat(durationSecs >= MAX_DELAY && durationSecs <= MAX_DELAY + NUM_SECONDS_AFTER_TIMEOUT, "Expected to take between " + MAX_DELAY + " and " + (MAX_DELAY + NUM_SECONDS_AFTER_TIMEOUT) + ", took " + durationSecs);
    sendMessageBatchResultFuture.get();
  }

  @Test
  public void testPollingTimeoutSuccessFailureMultipleReceivers() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testPollingTimeoutSuccessFailureMultipleReceivers");
    assertThat("true".equalsIgnoreCase(getConfigProperty(LOCAL_EUCTL_FILE, "services.simplequeue.enable_long_polling")), "Metric collection needs to be enabled");
    String queueName = "queue_name_test_polling_timeout_failure_multiple_receivers";
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);
    String queueUrl = accountSQSClient.createQueue(createQueueRequest).getQueueUrl();
    int DELAY_1 = 5;
    int MAX_DELAY = 20; // second message will not be received
    long startTime = System.currentTimeMillis();
    Future<ReceiveMessageResult> receiveMessageResultFuture1 = pool.submit(new Callable<ReceiveMessageResult>() {
      @Override
      public ReceiveMessageResult call() throws Exception {
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
        receiveMessageRequest.setWaitTimeSeconds(20);
        receiveMessageRequest.setQueueUrl(queueUrl);
        return accountSQSClient.receiveMessage(receiveMessageRequest);
      }
    });
    Future<ReceiveMessageResult> receiveMessageResultFuture2 = pool.submit(new Callable<ReceiveMessageResult>() {
      @Override
      public ReceiveMessageResult call() throws Exception {
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
        receiveMessageRequest.setWaitTimeSeconds(20);
        receiveMessageRequest.setQueueUrl(queueUrl);
        return accountSQSClient.receiveMessage(receiveMessageRequest);
      }
    });
    Future<SendMessageBatchResult> sendMessageBatchResultFuture = pool.submit(new Callable<SendMessageBatchResult>() {
      @Override
      public SendMessageBatchResult call() throws Exception {
        SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest();
        sendMessageBatchRequest.setQueueUrl(queueUrl);
        SendMessageBatchRequestEntry entry1 = new SendMessageBatchRequestEntry();
        entry1.setId("id1");
        entry1.setMessageBody("hello");
        entry1.setDelaySeconds(DELAY_1);
        sendMessageBatchRequest.getEntries().add(entry1);
        return accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      }
    });
    ReceiveMessageResult receiveMessageResult1 = receiveMessageResultFuture1.get();
    ReceiveMessageResult receiveMessageResult2 = receiveMessageResultFuture2.get();
    long endTime = System.currentTimeMillis();
    long durationSecs = (endTime - startTime) / 1000L;
    assertThat(durationSecs >= MAX_DELAY && durationSecs <= MAX_DELAY + NUM_SECONDS_AFTER_TIMEOUT, "Expected to take between " + MAX_DELAY + " and " + (MAX_DELAY + NUM_SECONDS_AFTER_TIMEOUT) + ", took " + durationSecs);
    sendMessageBatchResultFuture.get();
  }

}
