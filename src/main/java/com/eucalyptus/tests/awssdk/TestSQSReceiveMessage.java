package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
public class TestSQSReceiveMessage {

  private int MAX_RECEIVE_MESSAGE_WAIT_TIME_SECONDS;
  private int MAX_VISIBILITY_TIMEOUT;
  private int MAX_RECEIVE_MESSAGE_MAX_NUMBER_OF_MESSAGES;

  private String account;
  private String otherAccount;

  private AmazonSQS accountSQSClient;
  private AmazonSQS otherAccountSQSClient;

  @BeforeClass
  public void init() throws Exception {
    print("### PRE SUITE SETUP - " + this.getClass().getSimpleName());

    try {
      getCloudInfoAndSqs();
      MAX_RECEIVE_MESSAGE_WAIT_TIME_SECONDS = getLocalConfigInt("MAX_RECEIVE_MESSAGE_WAIT_TIME_SECONDS");
      MAX_VISIBILITY_TIMEOUT = getLocalConfigInt("MAX_VISIBILITY_TIMEOUT");
      MAX_RECEIVE_MESSAGE_MAX_NUMBER_OF_MESSAGES = getLocalConfigInt("MAX_RECEIVE_MESSAGE_MAX_NUMBER_OF_MESSAGES");
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
  public void testReceiveMessageNonExistentAccount() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testReceiveMessageNonExistentAccount");
    String queueName = "queue_name_receive_message_nonexistent_account";

    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);

    try {
      accountSQSClient.receiveMessage(queueUrl.replace(accountId, "000000000000"));
      assertThat(false, "Should fail receiving message on a queue from a non-existent user");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 404, "Correctly fail receiving a message on a queue from a non-existent user");
    }
  }

  @Test
  public void testReceiveMessageOtherAccount() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testReceiveMessageOtherAccount");
    String queueName = "queue_name_receive_message_other_account";
    String otherAccountQueueUrl = otherAccountSQSClient.createQueue(queueName).getQueueUrl();
    try {
      accountSQSClient.receiveMessage(otherAccountQueueUrl);
      assertThat(false, "Should fail receiving a message on a queue from a different user");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail receiving a message on a queue from a different user");
    }
  }

  @Test
  public void testReceiveMessageNonExistentQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testReceiveMessageNonExistentQueue");
    String queueName = "queue_name_receive_message_nonexistent_queue";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    try {
      accountSQSClient.receiveMessage(queueUrl + "-bogus");
      assertThat(false, "Should fail receiveing a message to non-existent queue");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail receiveing a message to non-existent queue");
    }
  }

  @Test
  public void testWaitTimeSeconds() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testWaitTimeSeconds");
    String queueName = "queue_name_receive_message_wait_time_seconds";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
    receiveMessageRequest.setQueueUrl(queueUrl);
    // too small
    receiveMessageRequest.setWaitTimeSeconds(-1);
    try {
      accountSQSClient.receiveMessage(receiveMessageRequest);
      assertThat(false, "Should fail receiving a message with wait time < 0");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail message with wait time < 0");
    }
    // too big
    receiveMessageRequest.setWaitTimeSeconds(1 + MAX_RECEIVE_MESSAGE_WAIT_TIME_SECONDS);
    try {
      accountSQSClient.receiveMessage(receiveMessageRequest);
      assertThat(false, "Should fail receiving a message with wait time time > services.simplequeue.max_receive_message_wait_time_seconds");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail message with wait time time > services.simplequeue.max_receive_message_wait_time_seconds");
    }
    // just right
    receiveMessageRequest.setWaitTimeSeconds(1);
    accountSQSClient.receiveMessage(receiveMessageRequest);
    assertThat(true, "correctly succeeded receiving a message with wait time time 1");
  }

  @Test
  public void testVisibilityTimeout() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testVisibilityTimeout");
    String queueName = "queue_name_receive_message_visibility_timeout";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
    receiveMessageRequest.setQueueUrl(queueUrl);

    // too small
    receiveMessageRequest.setVisibilityTimeout(-1);
    try {
      accountSQSClient.receiveMessage(receiveMessageRequest);
      assertThat(false, "Should fail receiving a message with visibility timeout < 0");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail message with visibility timeout < 0");
    }
    // too big
    receiveMessageRequest.setVisibilityTimeout(1 + MAX_VISIBILITY_TIMEOUT);
    try {
      accountSQSClient.receiveMessage(receiveMessageRequest);
      assertThat(false, "Should fail receiving a message with visibility timeout time > services.simplequeue.max_visibility_timeout");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail message with visibility timeout time > services.simplequeue.max_visibility_timeout");
    }
    // just right
    receiveMessageRequest.setVisibilityTimeout(1);
    accountSQSClient.receiveMessage(receiveMessageRequest);
    assertThat(true, "correctly succeeded receiving a message with visibility timeout time 1");
  }

  @Test
  public void testMaxNumberOfMessages() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testMaxNumberOfMessages");
    String queueName = "queue_name_receive_message_max_num_messages";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
    receiveMessageRequest.setQueueUrl(queueUrl);
    // too small
    receiveMessageRequest.setMaxNumberOfMessages(0);
    try {
      accountSQSClient.receiveMessage(receiveMessageRequest);
      assertThat(false, "Should fail receiving a message with max number of messages < 1");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail message with max number of messages < 1");
    }
    // too big
    receiveMessageRequest.setMaxNumberOfMessages(1 + MAX_RECEIVE_MESSAGE_MAX_NUMBER_OF_MESSAGES);
    try {
      accountSQSClient.receiveMessage(receiveMessageRequest);
      assertThat(false, "Should fail receiving a message with max number of messages time > services.simplequeue.max_receive_message_max_number_of_messages");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail message with max number of messages time > services.simplequeue.max_receive_message_max_number_of_messages");
    }
    // just right
    receiveMessageRequest.setMaxNumberOfMessages(1);
    accountSQSClient.receiveMessage(receiveMessageRequest);
    assertThat(true, "correctly succeeded receiving a message with max number of messages 1");
  }

  @Test
  public void testMessagePartsAndFilters() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testMessagePartsAndFilters");
    String queueName = "queue_name_receive_message_parts_and_filter";
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.getAttributes().put("DelaySeconds","0");
    createQueueRequest.getAttributes().put("VisibilityTimeout","0"); // get message back immediately
    createQueueRequest.setQueueName(queueName);
    String queueUrl = accountSQSClient.createQueue(createQueueRequest).getQueueUrl();
    // send a message with various message attributes
    SendMessageRequest sendMessageRequest = new SendMessageRequest();
    sendMessageRequest.setMessageBody("Message body");
    MessageAttributeValue ma1Value = new MessageAttributeValue();
    ma1Value.setStringValue("String value");
    ma1Value.setDataType("String");
    sendMessageRequest.getMessageAttributes().put("MA1", ma1Value);
    MessageAttributeValue ma2Value = new MessageAttributeValue();
    ma2Value.setStringValue("5");
    ma2Value.setDataType("Number");
    sendMessageRequest.getMessageAttributes().put("MA2", ma2Value);
    MessageAttributeValue ma3Value = new MessageAttributeValue();
    byte[] jenny = new byte[] {8,6,7,5,3,0,9};
    ma3Value.setBinaryValue(ByteBuffer.wrap(jenny));
    ma3Value.setDataType("Binary");
    sendMessageRequest.getMessageAttributes().put("MA3", ma3Value);
    sendMessageRequest.setQueueUrl(queueUrl);
    String messageId = accountSQSClient.sendMessage(sendMessageRequest).getMessageId();

    // see what we get back with attributes and message attributes with nothing in the filter
    ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
    receiveMessageRequest.setQueueUrl(queueUrl);
    Message message = receiveMessage(receiveMessageRequest, messageId);
    assertThat(message.getAttributes() == null || message.getAttributes().isEmpty(), "Successfully tested receiving a message with no attributes in the arg list and no return attributes");
    assertThat(message.getMessageAttributes() == null || message.getMessageAttributes().isEmpty(), "Successfully tested receiving a message with no message attributes in the arg list and no return attributes");

    // see what happens with 'All' in both attributes and message attributes
    receiveMessageRequest.setAttributeNames(Collections.singleton("All"));
    receiveMessageRequest.setMessageAttributeNames(Collections.singleton("All"));
    Collection<String> expectedAttributes = ImmutableSet.of("ApproximateFirstReceiveTimestamp", "ApproximateReceiveCount",
      "SenderId", "SentTimestamp");
    Collection<String> allMessageAttributes = sendMessageRequest.getMessageAttributes().keySet();
    message = receiveMessage(receiveMessageRequest, messageId);
    Set<String> actualAttributeNames = Sets.newHashSet();
    if (message.getAttributes() != null) {
      actualAttributeNames.addAll(message.getAttributes().keySet());
    }
    assertThat(actualAttributeNames.containsAll(expectedAttributes), "Successfully tested " + expectedAttributes + " being returned when 'All' attributes are used in ReceiveMessage");
    Set<String> actualMessageAttributeNames = Sets.newHashSet();
    if (message.getMessageAttributes() != null) {
      actualMessageAttributeNames.addAll(message.getMessageAttributes().keySet());
    }
    assertThat(actualMessageAttributeNames.equals(allMessageAttributes), "Successfully tested " + allMessageAttributes + " being returned when 'All' message attributes are used in ReceiveMessage");

    // look for an exact subset of attributes, and .* (like all) in message attributes
    Collection<String> exactExpectedAttributes1 = ImmutableSet.of("ApproximateFirstReceiveTimestamp", "ApproximateReceiveCount");
    receiveMessageRequest.setAttributeNames(exactExpectedAttributes1);
    receiveMessageRequest.setMessageAttributeNames(Collections.singleton(".*"));
    message = receiveMessage(receiveMessageRequest, messageId);

    actualAttributeNames.clear();
    if (message.getAttributes() != null) {
      actualAttributeNames.addAll(message.getAttributes().keySet());
    }
    assertThat(actualAttributeNames.equals(exactExpectedAttributes1), "Successfully tested " + exactExpectedAttributes1 + " being returned when these attributes are used in ReceiveMessage");
    actualMessageAttributeNames.clear();
    if (message.getMessageAttributes() != null) {
      actualMessageAttributeNames.addAll(message.getMessageAttributes().keySet());
    }
    assertThat(actualMessageAttributeNames.equals(allMessageAttributes), "Successfully tested " + allMessageAttributes + " being returned when '.*' message attributes are used in ReceiveMessage");

    // look for an exact subset of attributes, and 'MA1' and 'MA4' (one of the two exists)  in message attributes
    Collection<String> exactExpectedAttributes2 = ImmutableSet.of("SenderId", "SentTimestamp");
    receiveMessageRequest.setAttributeNames(exactExpectedAttributes2);
    receiveMessageRequest.setMessageAttributeNames(Sets.newHashSet("MA1", "MA4"));
    message = receiveMessage(receiveMessageRequest, messageId);

    actualAttributeNames.clear();
    if (message.getAttributes() != null) {
      actualAttributeNames.addAll(message.getAttributes().keySet());
    }
    assertThat(actualAttributeNames.equals(exactExpectedAttributes2), "Successfully tested " + exactExpectedAttributes2 + " being returned when these attributes are used in ReceiveMessage");
    actualMessageAttributeNames.clear();
    if (message.getMessageAttributes() != null) {
      actualMessageAttributeNames.addAll(message.getMessageAttributes().keySet());
    }
    assertThat(actualMessageAttributeNames.equals(Collections.singleton("MA1")), "Successfully tested 'MA1' being returned when 'MA1' and 'MA4' as message attributes are used in ReceiveMessage");

    // look for only bogus attributes, and 'MA1.*' in message attributes
    receiveMessageRequest.setAttributeNames(Collections.singleton("Bogus"));
    receiveMessageRequest.setMessageAttributeNames(Collections.singleton("MA1.*"));
    message = receiveMessage(receiveMessageRequest, messageId);

    assertThat(message.getAttributes() == null || message.getAttributes().isEmpty(), "Successfully tested receiving a message with no attributes in the arg list and no return attributes");
    actualMessageAttributeNames.clear();
    if (message.getMessageAttributes() != null) {
      actualMessageAttributeNames.addAll(message.getMessageAttributes().keySet());
    }
    assertThat(actualMessageAttributeNames.equals(Collections.singleton("MA1")), "Successfully tested 'MA1' being returned when 'MA1.*' as message attributes are used in ReceiveMessage");

    // no more attribute tests, just a couple more message attribute filters
    receiveMessageRequest.setAttributeNames(Collections.EMPTY_SET);
    // use MA.*, should get all attributes in this case
    receiveMessageRequest.setMessageAttributeNames(Collections.singleton("MA.*"));
    message = receiveMessage(receiveMessageRequest, messageId);
    actualMessageAttributeNames.clear();
    if (message.getMessageAttributes() != null) {
      actualMessageAttributeNames.addAll(message.getMessageAttributes().keySet());
    }
    assertThat(actualMessageAttributeNames.equals(Sets.newHashSet("MA1", "MA2", "MA3")), "Successfully tested all being returned when 'MA.*' as message attributes are used in ReceiveMessage");

    // use bob.* should match no attributes in this case
    receiveMessageRequest.setMessageAttributeNames(Sets.newHashSet("bob.*","bob"));
    message = receiveMessage(receiveMessageRequest, messageId);

    actualMessageAttributeNames.clear();
    if (message.getMessageAttributes() != null) {
      actualMessageAttributeNames.addAll(message.getMessageAttributes().keySet());
    }
    assertThat(actualMessageAttributeNames.isEmpty(), "Successfully tested no being returned when 'bob.*', 'bob' as message attributes are used in ReceiveMessage");

    // finally just check that when all message attributes do come back, they match what was sent (value-wise), as well as the body
    receiveMessageRequest.setMessageAttributeNames(Collections.singleton("All"));
    message = receiveMessage(receiveMessageRequest, messageId);
    assertThat(message.getBody().equals(sendMessageRequest.getMessageBody()), "Checking message body");
    assertThat(Objects.equals(message.getMessageAttributes(), sendMessageRequest.getMessageAttributes()), "Checking message attributes");
  }

  private Message receiveMessage(ReceiveMessageRequest receiveMessageRequest, String messageId) throws InterruptedException {
    long startTime = System.currentTimeMillis();
    while ((System.currentTimeMillis() - startTime) < 5000L) {
      ReceiveMessageResult receiveMessageResult = accountSQSClient.receiveMessage(receiveMessageRequest);
      if (receiveMessageResult != null && receiveMessageResult.getMessages() != null) {
        for (Message message: receiveMessageResult.getMessages()) {
          if (message.getMessageId().equals(messageId)) {
            return message;
          }
        }
      }
      Thread.sleep(1000L);
    }
    throw new InterruptedException("timeout");
  }

  @Test
  public void testReceiveMaxNumberOfMessages() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testMaxNumberOfMessages");
    String queueName = "queue_name_receive_message_max_number_of_messages";
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.getAttributes().put("DelaySeconds", "0");
    createQueueRequest.getAttributes().put("VisibilityTimeout", "0"); // get message back immediately
    createQueueRequest.setQueueName(queueName);
    String queueUrl = accountSQSClient.createQueue(createQueueRequest).getQueueUrl();

    // send a bunch of messages
    for (int i = 0; i < 2 * MAX_RECEIVE_MESSAGE_MAX_NUMBER_OF_MESSAGES; i++) {
      accountSQSClient.sendMessage(queueUrl, "hello");
    }

    int NUM_TRIALS_PER_REQUEST = 10;
    // make sure at most 1 message returned if no value set for MaxNumberOfMessages
    ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
    receiveMessageRequest.setQueueUrl(queueUrl);

    for (int i=0; i< NUM_TRIALS_PER_REQUEST; i++) {
      ReceiveMessageResult receiveMessageResult = accountSQSClient.receiveMessage(receiveMessageRequest);
      assertThat(receiveMessageResult == null || receiveMessageResult.getMessages() == null
      || receiveMessageResult.getMessages().size() <= 1, "Receive at most 1 message if no MaxNumberOfMessages set");
    }

    for (int maxNumberOfMessages = 1; maxNumberOfMessages <= MAX_RECEIVE_MESSAGE_MAX_NUMBER_OF_MESSAGES; maxNumberOfMessages++) {
      receiveMessageRequest.setMaxNumberOfMessages(maxNumberOfMessages);
      for (int i=0; i< NUM_TRIALS_PER_REQUEST; i++) {
        ReceiveMessageResult receiveMessageResult = accountSQSClient.receiveMessage(receiveMessageRequest);
        assertThat(receiveMessageResult == null || receiveMessageResult.getMessages() == null
          || receiveMessageResult.getMessages().size() <= maxNumberOfMessages, "Receive at most " + maxNumberOfMessages + " message if no MaxNumberOfMessages set");
      }
    }
  }
  private int getLocalConfigInt(String propertySuffixInCapsAndUnderscores) throws IOException {
    String propertyName = "services.simplequeue." + propertySuffixInCapsAndUnderscores.toLowerCase();
    return Integer.parseInt(getConfigProperty(LOCAL_EUCTL_FILE, propertyName));
  }

}
