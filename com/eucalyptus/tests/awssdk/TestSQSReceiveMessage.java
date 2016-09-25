package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonWebServiceClient;
import com.amazonaws.Request;
import com.amazonaws.handlers.AbstractRequestHandler;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.eucalyptus.tests.awssdk.N4j.*;

/**
 * Created by ethomas on 9/21/16.
 */
public class TestSQSReceiveMessage {

  @Test
  public void testReceiveMessages() throws Exception {
    getCloudInfoAndSqs();

    final int MAX_RECEIVE_MESSAGE_WAIT_TIME_SECONDS = getLocalConfigInt("MAX_RECEIVE_MESSAGE_WAIT_TIME_SECONDS");
    final int MAX_VISIBILITY_TIMEOUT = getLocalConfigInt("MAX_VISIBILITY_TIMEOUT");
    final int MAX_RECEIVE_MESSAGE_MAX_NUMBER_OF_MESSAGES = getLocalConfigInt("MAX_RECEIVE_MESSAGE_MAX_NUMBER_OF_MESSAGES");

    String prefix = UUID.randomUUID().toString() + "-" + System.currentTimeMillis() + "-";
    String otherAccount = "account" + System.currentTimeMillis();
    createAccount(otherAccount);
    AmazonSQS otherSQS = getSqsClientWithNewAccount(otherAccount, "admin");

    try {
      // first create a queue
      String suffix = "-receive-message-test-1";
      String queueName = prefix + suffix;
      String queueUrl = sqs.createQueue(queueName).getQueueUrl();
      print("Creating queue");
      // first make sure we have a queue url with an account id and a queue name
      List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
      assertThat(pathParts.size() == 2, "The queue URL path needs two 'suffix' values: account id, and queue name");
      assertThat(pathParts.get(1).equals(queueName), "The queue URL path needs to end in the queue name");
      String accountId = pathParts.get(0);
      print("accountId="+accountId);

      print("Creating queue in other account");
      String otherAccountQueueUrl = otherSQS.createQueue(queueName).getQueueUrl();

      print("Testing receiving message to non-existant account, should fail");
      // First try to receive message from a nonexistant account
      try {
        sqs.receiveMessage(queueUrl.replace(accountId, "000000000000"));
        assertThat(false, "Should fail receiving message on a queue from a non-existent user");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 404, "Correctly fail receiving a message on a queue from a non-existent user");
      }

      print("Testing receiving message to different account, should fail");
      // Now try to receive message from an account with no access
      try {
        sqs.receiveMessage(otherAccountQueueUrl);
        assertThat(false, "Should fail receiving a message on a queue from a different user");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 403, "Correctly fail receiving a message on a queue from a different user");
      }

      print("Testing receiving message to non-existent queue, should fail");
      // Now try to receive to non-existent-queue
      try {
        sqs.receiveMessage(queueUrl + "-bogus");
        assertThat(false, "Should fail receiveing a message to non-existent queue");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 400, "Correctly fail receiveing a message to non-existent queue");
      }

      // test wait time
      ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
      receiveMessageRequest.setQueueUrl(queueUrl);

      receiveMessageRequest.setWaitTimeSeconds(-1);
      print("Testing receiving a message with wait time < 0, should fail");
      try {
        sqs.receiveMessage(receiveMessageRequest);
        assertThat(false, "Should fail receiving a message with wait time < 0");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 400, "Correctly fail message with wait time < 0");
      }
      print("Testing receiving a message with wait time > services.simplequeue.max_receive_message_wait_time_seconds, should fail");
      receiveMessageRequest.setWaitTimeSeconds(1 + MAX_RECEIVE_MESSAGE_WAIT_TIME_SECONDS);
      try {
        sqs.receiveMessage(receiveMessageRequest);
        assertThat(false, "Should fail receiving a message with wait time time > services.simplequeue.max_receive_message_wait_time_seconds");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 400, "Correctly fail message with wait time time > services.simplequeue.max_receive_message_wait_time_seconds");
      }
      print("Testing receiving a message with wait time 1 (should succeed");
      receiveMessageRequest.setWaitTimeSeconds(1);
      sqs.receiveMessage(receiveMessageRequest);
      assertThat(true, "correctly succeeded receiving a message with wait time time 1");

      // test visibility timeout
      receiveMessageRequest = new ReceiveMessageRequest();
      receiveMessageRequest.setQueueUrl(queueUrl);

      receiveMessageRequest.setVisibilityTimeout(-1);
      print("Testing receiving a message with visibility timeout < 0, should fail");
      try {
        sqs.receiveMessage(receiveMessageRequest);
        assertThat(false, "Should fail receiving a message with visibility timeout < 0");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 400, "Correctly fail message with visibility timeout < 0");
      }
      print("Testing receiving a message with visibility timeout > services.simplequeue.max_visibility_timeout, should fail");
      receiveMessageRequest.setVisibilityTimeout(1 + MAX_VISIBILITY_TIMEOUT);
      try {
        sqs.receiveMessage(receiveMessageRequest);
        assertThat(false, "Should fail receiving a message with visibility timeout time > services.simplequeue.max_visibility_timeout");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 400, "Correctly fail message with visibility timeout time > services.simplequeue.max_visibility_timeout");
      }
      print("Testing receiving a message with visibility timeout 1 (should succeed");
      receiveMessageRequest.setVisibilityTimeout(1);
      sqs.receiveMessage(receiveMessageRequest);
      assertThat(true, "correctly succeeded receiving a message with visibility timeout time 1");

      // test max number of messages
      receiveMessageRequest = new ReceiveMessageRequest();
      receiveMessageRequest.setQueueUrl(queueUrl);

      receiveMessageRequest.setMaxNumberOfMessages(0);
      print("Testing receiving a message with max number of messages < 1, should fail");
      try {
        sqs.receiveMessage(receiveMessageRequest);
        assertThat(false, "Should fail receiving a message with max number of messages < 1");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 400, "Correctly fail message with max number of messages < 1");
      }
      print("Testing receiving a message with max number of messages > services.simplequeue.max_visibility_timeout, should fail");
      receiveMessageRequest.setMaxNumberOfMessages(1 + MAX_RECEIVE_MESSAGE_MAX_NUMBER_OF_MESSAGES);
      try {
        sqs.receiveMessage(receiveMessageRequest);
        assertThat(false, "Should fail receiving a message with max number of messages time > services.simplequeue.max_receive_message_max_number_of_messages");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 400, "Correctly fail message with max number of messages time > services.simplequeue.max_receive_message_max_number_of_messages");
      }
      print("Testing receiving a message with max number of messages 1 (should succeed");
      receiveMessageRequest.setMaxNumberOfMessages(1);
      sqs.receiveMessage(receiveMessageRequest);
      assertThat(true, "correctly succeeded receiving a message with max number of messages 1");

      // send a message, check various attributes
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

      String messageId = sqs.sendMessage(sendMessageRequest).getMessageId();

      // first see what happens with no attributes
      print("Testing receiving a message with no 'attributes' in the arg list, should return no attributes");
      receiveMessageRequest = new ReceiveMessageRequest();
      receiveMessageRequest.setQueueUrl(queueUrl);
      Message message = receiveMessage(receiveMessageRequest, messageId);
      assertThat(message.getAttributes() == null || message.getAttributes().isEmpty(), "Successfully tested receiving a message with no attributes in the arg list and no return attributes");

      print("Testing receiving a message with all 'attributes' in the arg list, should return all attributes");
      Collection<String> expectedAttributes = Sets.newHashSet("ApproximateFirstReceiveTimestamp", "ApproximateReceiveCount",
        "SenderId", "SentTimestamp");
      receiveMessageRequest.setAttributeNames(Collections.singleton("All"));
      message = receiveMessage(receiveMessageRequest, messageId);
      Set<String> actualAttributeNames = Sets.newHashSet();
      if (message.getAttributes() != null) {
        actualAttributeNames.addAll(message.getAttributes().keySet());
      }
      assertThat(actualAttributeNames.containsAll(expectedAttributes), "Successfully tested " + expectedAttributes + " being returned when 'All' attributes are used in ReceiveMessage");

      Collection<String> exactExpectedAttributes = Sets.newHashSet("ApproximateFirstReceiveTimestamp", "ApproximateReceiveCount");
      print("Testing receiving a message with "+exactExpectedAttributes+" 'attributes' in the arg list, should return those attributes");
      receiveMessageRequest.setAttributeNames(exactExpectedAttributes);
      message = receiveMessage(receiveMessageRequest, messageId);
      actualAttributeNames.clear();
      if (message.getAttributes() != null) {
        actualAttributeNames.addAll(message.getAttributes().keySet());
      }
      assertThat(actualAttributeNames.equals(exactExpectedAttributes), "Successfully tested " + exactExpectedAttributes + " being returned when these attributes are used in ReceiveMessage");

      exactExpectedAttributes = Sets.newHashSet("SenderId", "SentTimestamp");
      print("Testing receiving a message with "+exactExpectedAttributes+" 'attributes' in the arg list, should return those attributes");
      receiveMessageRequest.setAttributeNames(exactExpectedAttributes);
      message = receiveMessage(receiveMessageRequest, messageId);
      actualAttributeNames.clear();
      if (message.getAttributes() != null) {
        actualAttributeNames.addAll(message.getAttributes().keySet());
      }
      assertThat(actualAttributeNames.equals(exactExpectedAttributes), "Successfully tested " + exactExpectedAttributes + " being returned when these attributes are used in ReceiveMessage");

      print("Testing receiving a message with only nonexistant 'attributes' in the arg list, should return no attributes");
      receiveMessageRequest = new ReceiveMessageRequest();
      receiveMessageRequest.setQueueUrl(queueUrl);
      receiveMessageRequest.setAttributeNames(Collections.singleton("Bogus"));
      message = receiveMessage(receiveMessageRequest, messageId);
      assertThat(message.getAttributes() == null || message.getAttributes().isEmpty(), "Successfully tested receiving a message with no attributes in the arg list and no return attributes");

      // Test Message attributes
      print("Testing receiving a message with no 'message attributes' in the arg list, should return no message attributes");
      receiveMessageRequest = new ReceiveMessageRequest();
      receiveMessageRequest.setQueueUrl(queueUrl);
      message = receiveMessage(receiveMessageRequest, messageId);
      assertThat(message.getMessageAttributes() == null || message.getMessageAttributes().isEmpty(), "Successfully tested receiving a message with no message attributes in the arg list and no return attributes");

      print("Testing receiving a message with all 'message attributes' in the arg list, should return all message attributes");
      Collection<String> expectedMessageAttributes = sendMessageRequest.getMessageAttributes().keySet();
      receiveMessageRequest.setMessageAttributeNames(Collections.singleton("All"));
      message = receiveMessage(receiveMessageRequest, messageId);
      Set<String> actualMessageAttributeNames = Sets.newHashSet();
      if (message.getMessageAttributes() != null) {
        actualMessageAttributeNames.addAll(message.getMessageAttributes().keySet());
      }
      assertThat(actualMessageAttributeNames.equals(expectedMessageAttributes), "Successfully tested " + expectedMessageAttributes + " being returned when 'All' message attributes are used in ReceiveMessage");

      print("Testing receiving a message with .* 'message attributes' in the arg list, should return .* message attributes");
      expectedMessageAttributes = sendMessageRequest.getMessageAttributes().keySet();
      receiveMessageRequest.setMessageAttributeNames(Collections.singleton(".*"));
      message = receiveMessage(receiveMessageRequest, messageId);
      actualMessageAttributeNames.clear();
      if (message.getMessageAttributes() != null) {
        actualMessageAttributeNames.addAll(message.getMessageAttributes().keySet());
      }
      assertThat(actualMessageAttributeNames.equals(expectedMessageAttributes), "Successfully tested " + expectedMessageAttributes + " being returned when '.*' message attributes are used in ReceiveMessage");

      print("Testing receiving a message with 'MA1' and 'MA4' message attribute names, should only return MA1");
      receiveMessageRequest.setMessageAttributeNames(Sets.newHashSet("MA1", "MA4"));
      message = receiveMessage(receiveMessageRequest, messageId);
      actualMessageAttributeNames.clear();
      if (message.getMessageAttributes() != null) {
        actualMessageAttributeNames.addAll(message.getMessageAttributes().keySet());
      }
      assertThat(actualMessageAttributeNames.equals(Collections.singleton("MA1")), "Successfully tested 'MA1' being returned when 'MA1' and 'MA4' as message attributes are used in ReceiveMessage");

      print("Testing receiving a message with 'MA1.*' should only return MA1");
      receiveMessageRequest.setMessageAttributeNames(Collections.singleton("MA1.*"));
      message = receiveMessage(receiveMessageRequest, messageId);
      actualMessageAttributeNames.clear();
      if (message.getMessageAttributes() != null) {
        actualMessageAttributeNames.addAll(message.getMessageAttributes().keySet());
      }
      assertThat(actualMessageAttributeNames.equals(Collections.singleton("MA1")), "Successfully tested 'MA1' being returned when 'MA1.*' as message attributes are used in ReceiveMessage");

      print("Testing receiving a message with 'MA.*' message attribute names, should only all attributes");
      receiveMessageRequest.setMessageAttributeNames(Collections.singleton("MA.*"));
      message = receiveMessage(receiveMessageRequest, messageId);
      actualMessageAttributeNames.clear();
      if (message.getMessageAttributes() != null) {
        actualMessageAttributeNames.addAll(message.getMessageAttributes().keySet());
      }
      assertThat(actualMessageAttributeNames.equals(Sets.newHashSet("MA1", "MA2", "MA3")), "Successfully tested all being returned when 'MA.*' as message attributes are used in ReceiveMessage");

      print("Testing receiving a message with 'bob.*, 'bob' message attribute names, should return no attributes");
      receiveMessageRequest.setMessageAttributeNames(Sets.newHashSet("bob.*","bob"));
      message = receiveMessage(receiveMessageRequest, messageId);
      actualMessageAttributeNames.clear();
      if (message.getMessageAttributes() != null) {
        actualMessageAttributeNames.addAll(message.getMessageAttributes().keySet());
      }
      assertThat(actualMessageAttributeNames.isEmpty(), "Successfully tested no being returned when 'bob.*', 'bob' as message attributes are used in ReceiveMessage");

      // finally, just check body & message attributes are the same
      receiveMessageRequest.setMessageAttributeNames(Collections.singleton("All"));
      message = receiveMessage(receiveMessageRequest, messageId);
      print("Testing message body in == message body out");
      assertThat(message.getBody().equals(sendMessageRequest.getMessageBody()), "Checking message body");
      print("Testing message attributes in == message attributes out");
      assertThat(Objects.equals(message.getMessageAttributes(), sendMessageRequest.getMessageAttributes()), "Checking message attributes");

    } finally {
      ListQueuesResult listQueuesResult = sqs.listQueues(prefix);
      if (listQueuesResult != null) {
        listQueuesResult.getQueueUrls().forEach(sqs::deleteQueue);
      }
      ListQueuesResult listQueuesResult2 = otherSQS.listQueues(prefix);
      if (listQueuesResult2 != null) {
        listQueuesResult2.getQueueUrls().forEach(otherSQS::deleteQueue);
      }
      deleteAccount(otherAccount);
    }
  }

  private Message receiveMessage(ReceiveMessageRequest receiveMessageRequest, String messageId) throws InterruptedException {
    long startTime = System.currentTimeMillis();
    while ((System.currentTimeMillis() - startTime) < 90000L) {
      ReceiveMessageResult receiveMessageResult = sqs.receiveMessage(receiveMessageRequest);
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



  private int getLocalConfigInt(String propertySuffixInCapsAndUnderscores) throws IOException {
    String propertyName = "services.simplequeue." + propertySuffixInCapsAndUnderscores.toLowerCase();
    return Integer.parseInt(getConfigProperty(LOCAL_EUCTL_FILE, propertyName));
  }

}
