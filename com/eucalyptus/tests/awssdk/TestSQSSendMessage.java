package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonWebServiceClient;
import com.amazonaws.Request;
import com.amazonaws.handlers.AbstractRequestHandler;
import com.amazonaws.services.cloudwatch.model.InvalidParameterValueException;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
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
import java.util.Set;
import java.util.UUID;

import static com.eucalyptus.tests.awssdk.N4j.*;

/**
 * Created by ethomas on 9/21/16.
 */
public class TestSQSSendMessage {

  @Test
  public void testSendMessages() throws Exception {
    getCloudInfoAndSqs();

    final int MAX_MESSAGE_ATTRIBUTE_NAME_LENGTH = getLocalConfigInt("MAX_MESSAGE_ATTRIBUTE_NAME_LENGTH");
    final int MAX_MESSAGE_ATTRIBUTE_TYPE_LENGTH = getLocalConfigInt("MAX_MESSAGE_ATTRIBUTE_TYPE_LENGTH");
    final int MAX_DELAY_SECONDS = getLocalConfigInt("MAX_DELAY_SECONDS");
    final int MAX_MAXIMUM_MESSAGE_SIZE = getLocalConfigInt("MAX_MAXIMUM_MESSAGE_SIZE");

    String prefix = UUID.randomUUID().toString() + "-" + System.currentTimeMillis() + "-";
    String otherAccount = "account" + System.currentTimeMillis();
    createAccount(otherAccount);
    AmazonSQS otherSQS = getSqsClientWithNewAccount(otherAccount, "admin");

    try {
      // first create a queue
      String suffix = "-send-message-test";
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

      print("Testing sending message to non-existant account, should fail");
      // First try to send a nonexistant account
      try {
        sqs.sendMessage(queueUrl.replace(accountId, "000000000000"), "Hello");
        assertThat(false, "Should fail sending a message on a queue from a non-existent user");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 404, "Correctly fail sending a message on a queue from a non-existent user");
      }

      print("Testing sending message to different account, should fail");
      // Now try to send an account with no access
      try {
        sqs.sendMessage(otherAccountQueueUrl, "Hello");
        assertThat(false, "Should fail sending a message on a queue from a different user");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 403, "Correctly fail sending a message on a queue from a different user");
      }

      print("Testing sending message to non-existent queue, should fail");
      // Now try to send to non-existent-queue
      try {
        sqs.sendMessage(queueUrl + "-bogus", "Hello");
        assertThat(false, "Should fail sending a message to non-existent queue");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 400, "Correctly fail sending a message to non-existent queue");
      }

      // test delay seconds, too low or too high
      testFailDelaySeconds(queueUrl, -1, "is too low");
      testFailDelaySeconds(queueUrl, (1 + MAX_DELAY_SECONDS), "is more than " + MAX_DELAY_SECONDS);

      // Now test various attribute name failures:
      testFailAttributeName(queueUrl, "", "is null or empty");
      testFailAttributeName(queueUrl, Strings.repeat("X", MAX_MESSAGE_ATTRIBUTE_NAME_LENGTH + 1), "is longer than " + MAX_MESSAGE_ATTRIBUTE_NAME_LENGTH + " characters");
      for (String awsStart: new String[]{"AWS.bob","aWs.bob","AmAzOn.bob","Amazon.bob"}) {
        testFailAttributeName(queueUrl, awsStart, "starts with 'AWS.' or 'Amazon.'");
      }
      testFailAttributeName(queueUrl, "Test..attribute.dots", "contains successive '.' characters");
      testFailAttributeName(queueUrl, "!@#$%^&*()", "contains invalid characters");

      print("Testing sending a message with message attributes with duplicate keys.  Should fail");
      // send a message attribute twice (i.e. same key, different value.  Since the SDK uses a map, we need
      // to send the parameters directly.
      AbstractRequestHandler sendTwoAttributesWithSameName = new AbstractRequestHandler() {
        public void beforeRequest(final Request<?> request) {
          request.addParameter("MessageAttribute.1.Name", "MA1");
          request.addParameter("MessageAttribute.1.Value.DataType", "String");
          request.addParameter("MessageAttribute.1.Value.StringValue", "Value1");
          request.addParameter("MessageAttribute.2.Name", "MA1");
          request.addParameter("MessageAttribute.2.Value.DataType", "String");
          request.addParameter("MessageAttribute.2.Value.StringValue", "Value2");
        }
      };
      ((AmazonWebServiceClient) sqs).addRequestHandler(sendTwoAttributesWithSameName);
      try {
        sqs.sendMessage(queueUrl, "Hello");
        assertThat(false, "Should fail sending a message with 'MA1' as attribute name twice");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 400, "Correctly fail sending a message with 'MA1' as attribute name twice");
      }
      ((AmazonWebServiceClient) sqs).removeRequestHandler(sendTwoAttributesWithSameName);

      // Now test various attribute value failures:
      testFailAttributeValue(queueUrl, null, " is null");

      MessageAttributeValue messageAttributeValue;

      messageAttributeValue = new MessageAttributeValue();
      testFailAttributeValue(queueUrl, messageAttributeValue, "has a null data type");

      messageAttributeValue = new MessageAttributeValue();
      messageAttributeValue.setDataType("");
      testFailAttributeValue(queueUrl, messageAttributeValue, "has an empty data type");

      messageAttributeValue = new MessageAttributeValue();
      messageAttributeValue.setDataType("Turntable");
      testFailAttributeValue(queueUrl, messageAttributeValue, "has an unsupported data type");

      messageAttributeValue = new MessageAttributeValue();
      messageAttributeValue.setDataType("Number." + Strings.repeat("X",MAX_MESSAGE_ATTRIBUTE_TYPE_LENGTH));
      testFailAttributeValue(queueUrl, messageAttributeValue, "is longer than " + MAX_MESSAGE_ATTRIBUTE_TYPE_LENGTH + " bytes");

      messageAttributeValue = new MessageAttributeValue();
      messageAttributeValue.setDataType("Binary");
      messageAttributeValue.setBinaryListValues(Collections.singleton(ByteBuffer.wrap("binaryValue".getBytes(Charsets.UTF_8))));
      testFailAttributeValue(queueUrl, messageAttributeValue, "has a binary attribute list value");

      messageAttributeValue = new MessageAttributeValue();
      messageAttributeValue.setDataType("String");
      messageAttributeValue.setStringListValues(Collections.singleton("stringValue"));
      testFailAttributeValue(queueUrl, messageAttributeValue, "has a string attribute list value");

      for (String type: new String[]{"String","Number","Binary"}) {
        messageAttributeValue = new MessageAttributeValue();
        messageAttributeValue.setDataType("String");
        testFailAttributeValue(queueUrl, messageAttributeValue, "has no (" + type + ") value");
      }

      messageAttributeValue = new MessageAttributeValue();
      messageAttributeValue.setDataType("String");
      messageAttributeValue.setStringValue("stringValue");
      messageAttributeValue.setBinaryValue(ByteBuffer.wrap("binaryValue".getBytes(Charsets.UTF_8)));
      testFailAttributeValue(queueUrl, messageAttributeValue, "has multiple values");

      messageAttributeValue = new MessageAttributeValue();
      messageAttributeValue.setDataType("String");
      messageAttributeValue.setBinaryValue(ByteBuffer.wrap("binaryValue".getBytes(Charsets.UTF_8)));
      testFailAttributeValue(queueUrl, messageAttributeValue, "must use a string field for String");

      messageAttributeValue = new MessageAttributeValue();
      messageAttributeValue.setDataType("Number");
      messageAttributeValue.setBinaryValue(ByteBuffer.wrap("binaryValue".getBytes(Charsets.UTF_8)));
      testFailAttributeValue(queueUrl, messageAttributeValue, "must use a string field for Number");

      messageAttributeValue = new MessageAttributeValue();
      messageAttributeValue.setDataType("Binary");
      messageAttributeValue.setStringValue("stringValue");
      testFailAttributeValue(queueUrl, messageAttributeValue, "must use a binary field for Binary");

      messageAttributeValue = new MessageAttributeValue();
      messageAttributeValue.setDataType("String");
      messageAttributeValue.setStringValue("\uFFFF");
      testFailAttributeValue(queueUrl, messageAttributeValue, "must contain valid characters as a string");

      messageAttributeValue = new MessageAttributeValue();
      messageAttributeValue.setDataType("Number");
      messageAttributeValue.setStringValue("X");
      testFailAttributeValue(queueUrl, messageAttributeValue, "must contain numeric characters as a number");

      // do a couple of failures with the body
      testFailBody(queueUrl, null, "a null body");
      testFailBody(queueUrl, "", "an empty body");
      testFailBody(queueUrl, Strings.repeat("X", MAX_MAXIMUM_MESSAGE_SIZE) + 1, "too long a body");
      testFailBody(queueUrl, "\uFFFF", "invalid characters in the body");

      // try to create a message with too long a length when using body and  different attributes
      print("Testing sending sending a message whose combined body and attribute length is too long, should fail");
      SendMessageRequest sendMessageRequest = new SendMessageRequest();
      int totalBodyLength = MAX_MAXIMUM_MESSAGE_SIZE + 1;

      messageAttributeValue = new MessageAttributeValue();
      messageAttributeValue.setDataType("String");
      totalBodyLength -= "String".getBytes(Charsets.UTF_8).length;
      messageAttributeValue.setStringValue("StringValue");
      totalBodyLength -= "StringValue".getBytes(Charsets.UTF_8).length;
      sendMessageRequest.getMessageAttributes().put("MA1", messageAttributeValue);
      totalBodyLength -= "MA1".getBytes(Charsets.UTF_8).length;

      messageAttributeValue = new MessageAttributeValue();
      messageAttributeValue.setDataType("Binary");
      totalBodyLength -= "Binary".getBytes(Charsets.UTF_8).length;
      byte[] binaryVal = "binaryValue".getBytes(Charsets.UTF_8);
      messageAttributeValue.setBinaryValue(ByteBuffer.wrap(binaryVal));
      totalBodyLength -= binaryVal.length;
      sendMessageRequest.getMessageAttributes().put("MA2", messageAttributeValue);
      totalBodyLength -= "MA2".getBytes(Charsets.UTF_8).length;
      sendMessageRequest.setMessageBody(Strings.repeat("X", totalBodyLength));
      sendMessageRequest.setQueueUrl(queueUrl);
      try {
        sqs.sendMessage(sendMessageRequest);
        assertThat(false, "Should fail sending a message whose combined body and attribute length is too long");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 400, "Correctly fail sending a message whose combined body and attribute length is too long");
      }
      print("Testing send/receive against valid messages");
      // now send a few messages, make sure they come back
      Message m1 = new Message();
      m1.setBody("Body of message 1");
      String messageId1 = sqs.sendMessage(makeMessageRequest(queueUrl, m1)).getMessageId();

      Message m2 = new Message();
      m2.setBody("Body of Message 2");
      MessageAttributeValue m2Ma1Value = new MessageAttributeValue();
      m2Ma1Value.setStringValue("String value m2Ma1");
      m2Ma1Value.setDataType("String.baby");
      m2.getMessageAttributes().put("M2MA1", m2Ma1Value);
      MessageAttributeValue m2Ma2Value = new MessageAttributeValue();
      m2Ma2Value.setStringValue("5");
      m2Ma2Value.setDataType("Number.baby");
      m2.getMessageAttributes().put("M2MA2", m2Ma2Value);
      MessageAttributeValue m2Ma3Value = new MessageAttributeValue();
      m2Ma3Value.setBinaryValue(ByteBuffer.wrap(new byte[] {8,6,7,5,3,0,9}));
      m2Ma3Value.setDataType("Binary.baby");
      m2.getMessageAttributes().put("M2MA3", m2Ma3Value);
      String messageId2 = sqs.sendMessage(makeMessageRequest(queueUrl, m2)).getMessageId();

      Message m3 = new Message();
      m3.setBody("Body of Message 3");
      MessageAttributeValue m3Ma1Value = new MessageAttributeValue();
      m3Ma1Value.setStringValue("String value m3Ma1");
      m3Ma1Value.setDataType("String");
      m3.getMessageAttributes().put("m3MA1", m3Ma1Value);
      MessageAttributeValue m3Ma2Value = new MessageAttributeValue();
      m3Ma2Value.setStringValue("6");
      m3Ma2Value.setDataType("Number");
      m3.getMessageAttributes().put("m3MA2", m3Ma2Value);
      MessageAttributeValue m3Ma3Value = new MessageAttributeValue();
      m3Ma3Value.setBinaryValue(ByteBuffer.wrap(new byte[] {42,18,97,0,0,0,14}));
      m3Ma3Value.setDataType("Binary");
      m3.getMessageAttributes().put("m3MA3", m3Ma3Value);
      String messageId3 = sqs.sendMessage(makeMessageRequest(queueUrl, m3)).getMessageId();

      HashMap<String, Message> receivedMessages = Maps.newHashMap();

      // try this for 90 seconds, just arbitrary
      long startTime = System.currentTimeMillis();
      while (receivedMessages.size() < 3 && System.currentTimeMillis() - startTime < 90 * 1000L) {
        ReceiveMessageResult receiveMessagesResult = sqs.receiveMessage(queueUrl);
        if (receiveMessagesResult != null && receiveMessagesResult.getMessages() != null) {
          for (Message message: receiveMessagesResult.getMessages()) {
            receivedMessages.put(message.getMessageId(), message);
          }
        }
      }
      Map<String, Message> sentMessages = ImmutableMap.<String, Message>builder().put(messageId1, m1).put(messageId2, m2).put(messageId3, m3).build();
      assertThat(messagesMatch(sentMessages, receivedMessages), "Sent messages match received messages");
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

  Message message(String body) {
    Message message = new Message();
    message.setBody(body);
    return message;
  }

  private Message message(String body, String attrName, String type, String value) {
    Message message = new Message();
    message.setBody(body);
    MessageAttributeValue messageAttributeValue = new MessageAttributeValue();
    messageAttributeValue.setDataType(type);
    messageAttributeValue.setStringValue(value);
    message.getMessageAttributes().put(attrName, messageAttributeValue);
    return message;
  }

  private Message message(String body, String attrName, String type, int value) {
    Message message = new Message();
    message.setBody(body);
    MessageAttributeValue messageAttributeValue = new MessageAttributeValue();
    messageAttributeValue.setDataType(type);
    messageAttributeValue.setStringValue(""+value);
    message.getMessageAttributes().put(attrName, messageAttributeValue);
    return message;
  }

  private Message message(String body, String attrName, String type, ByteBuffer value) {
    Message message = new Message();
    message.setBody(body);
    MessageAttributeValue messageAttributeValue = new MessageAttributeValue();
    messageAttributeValue.setDataType(type);
    messageAttributeValue.setBinaryValue(value);
    message.getMessageAttributes().put(attrName, messageAttributeValue);
    return message;
  }

  @Test
  public void testMessagesMatch() throws Exception {
    Message M_BODY_ONLY_1 = message("Body 1");
    Message M_BODY_ONLY_2 = message("Body 2");
    Message M_STRING_ATTR_1 = message("Body", "MA1", "String", "String value 1");
    Message M_STRING_ATTR_2 = message("Body", "MA1", "String", "String value 2");
    Message M_STRING_ATTR_3 = message("Body", "MA1", "String.other", "String value 1");
    Message M_STRING_ATTR_4 = message("Body", "MA2", "String", "String value 1");
    Message M_NUM_ATTR_1 = message("Body", "MA1", "Number", 1);
    Message M_NUM_ATTR_2 = message("Body", "MA1", "Number", 2);
    Message M_NUM_ATTR_3 = message("Body", "MA1", "Number.other", 1);
    Message M_NUM_ATTR_4 = message("Body", "MA2", "Number", 1);
    Message M_BIN_ATTR_1 = message("Body", "MA1", "Binary", ByteBuffer.wrap(new byte[]{1}));
    Message M_BIN_ATTR_2 = message("Body", "MA1", "Binary", ByteBuffer.wrap(new byte[]{2}));
    Message M_BIN_ATTR_3 = message("Body", "MA1", "Binary.other", ByteBuffer.wrap(new byte[]{1}));
    Message M_BIN_ATTR_4 = message("Body", "MA2", "Binary", ByteBuffer.wrap(new byte[]{1}));
    List<Message> allMessages = Lists.newArrayList(M_BODY_ONLY_1, M_BODY_ONLY_2, 
      M_STRING_ATTR_1, M_STRING_ATTR_2, M_STRING_ATTR_3, M_STRING_ATTR_4,
      M_NUM_ATTR_1, M_NUM_ATTR_2, M_NUM_ATTR_3, M_NUM_ATTR_4,
      M_BIN_ATTR_1, M_BIN_ATTR_2, M_BIN_ATTR_3, M_BIN_ATTR_4);
    // start different message ids
    Map<String, Message> map1 = ImmutableMap.<String, Message>builder().put("m-id-1", M_BODY_ONLY_1).build();
    Map<String, Message> map2 = ImmutableMap.<String, Message>builder().put("m-id-2", M_BODY_ONLY_1).build();
    assertThat(!messagesMatch(map1, map2), "Different message ids");
    map1 = ImmutableMap.<String, Message>builder().put("m-id-1", M_BODY_ONLY_1).put("m-id-2", M_BODY_ONLY_1).build();
    map2 = ImmutableMap.<String, Message>builder().put("m-id-2", M_BODY_ONLY_1).build();
    assertThat(!messagesMatch(map1, map2), "Different message ids");
    map1 = ImmutableMap.<String, Message>builder().put("m-id-2", M_BODY_ONLY_1).build();
    map2 = ImmutableMap.<String, Message>builder().put("m-id-1", M_BODY_ONLY_1).put("m-id-2", M_BODY_ONLY_1).build();
    assertThat(!messagesMatch(map1, map2), "Different message ids");

    for (Message m1: allMessages) {
      for (Message m2: allMessages) {
        if (m1 == m2) continue;
        map1 = ImmutableMap.<String, Message>builder().put("m-id-1", m1).build();
        map2 = ImmutableMap.<String, Message>builder().put("m-id-1", m2).build();
        assertThat(!messagesMatch(map1, map2), "Something different in the messages");
      }
    }

    // now match
    Map<String, Message> finalMap = Maps.newHashMap();
    for (Message m: allMessages) {
      finalMap.put(UUID.randomUUID().toString(), m);
    }
    assertThat(messagesMatch(finalMap, finalMap), "same map should Match");
  }


  private boolean messagesMatch(Map<String, Message> sentMessages, Map<String, Message> receivedMessages) {
    if (!sentMessages.keySet().containsAll(receivedMessages.keySet())) return false;
    if (!receivedMessages.keySet().containsAll(sentMessages.keySet())) return false;
    for (String key: sentMessages.keySet()) {
      Message m1 = sentMessages.get(key);
      Message m2 = receivedMessages.get(key);
      if (!m1.getBody().equals(m2.getBody())) {
        return false;
      }
      int m1MessAttrSize = m1.getMessageAttributes() == null ? 0 : m1.getMessageAttributes().size();
      int m2MessAttrSize = m2.getMessageAttributes() == null ? 0 : m2.getMessageAttributes().size();
      if (m1MessAttrSize != m2MessAttrSize) return false;
      if (m1.getMessageAttributes() != null && m2.getMessageAttributes() != null) {
        if (!m1.getMessageAttributes().keySet().containsAll(m2.getMessageAttributes().keySet())) return false;
        if (!m2.getMessageAttributes().keySet().containsAll(m1.getMessageAttributes().keySet())) return false;
        for (String attrName : m1.getMessageAttributes().keySet()) {
          MessageAttributeValue mav1 = m1.getMessageAttributes().get(attrName);
          MessageAttributeValue mav2 = m2.getMessageAttributes().get(attrName);
          // data type should not be null (won't check)
          if (!mav1.getDataType().equals(mav2.getDataType())) return false;
          if (mav1.getStringValue() == null && mav2.getStringValue() != null) return false;
          if (mav1.getStringValue() != null && mav2.getStringValue() == null) return false;
          if (mav1.getBinaryValue() == null && mav2.getBinaryValue() != null) return false;
          if (mav1.getBinaryValue() != null && mav2.getBinaryValue() == null) return false;
          if (mav1.getStringValue() != null && !mav1.getStringValue().equals(mav2.getStringValue())) return false;
          if (mav1.getBinaryValue() != null && !mav1.getBinaryValue().equals(mav2.getBinaryValue())) return false;
        }
      }
    }
    return true;
  }

  private SendMessageRequest makeMessageRequest(String queueUrl, Message m) {
    SendMessageRequest sendMessageRequest = new SendMessageRequest();
    sendMessageRequest.setQueueUrl(queueUrl);
    sendMessageRequest.setMessageBody(m.getBody());
    if (m.getMessageAttributes() != null) {
      for (String name: m.getMessageAttributes().keySet()) {
        sendMessageRequest.getMessageAttributes().put(name, m.getMessageAttributes().get(name));
      }
    }
    return sendMessageRequest;
  }

  private void testFailBody(String queueUrl, String body, String withClause) {
    print("Testing sending message with " + withClause + ", should fail");
    try {
      sqs.sendMessage(queueUrl, body);
      assertThat(false, "Should fail sending a message with " + withClause);
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail sending a message with " + withClause);
    }
  }

  private void testFailDelaySeconds(String queueUrl, int delaySeconds, String thatClause) {
    try {
      print("Testing sending message with delay second value that " + thatClause + " , should fail");
      SendMessageRequest sendMessageRequest = new SendMessageRequest();
      sendMessageRequest.setMessageBody("body");
      sendMessageRequest.setQueueUrl(queueUrl);
      sendMessageRequest.setDelaySeconds(delaySeconds);
      sqs.sendMessage(sendMessageRequest);
      assertThat(false, "Should fail sending a message with a delay seconds value that " + thatClause);
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail sending a message with a delay seconds value that " + thatClause);
    }
  }

  private void testFailAttributeValue(String queueUrl, MessageAttributeValue messageAttributeValue, String thatClause) {
    try {
      print("Testing sending message with message attribute value that " + thatClause + " , should fail");
      SendMessageRequest sendMessageRequest = new SendMessageRequest();
      sendMessageRequest.setMessageBody("body");
      sendMessageRequest.setQueueUrl(queueUrl);
      sendMessageRequest.getMessageAttributes().put("myAttribute", messageAttributeValue);
      sqs.sendMessage(sendMessageRequest);
      assertThat(false, "Should fail sending a message with a message attribute value that " + thatClause);
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail sending a message with a message attribute value that " + thatClause);
    }
  }

  private void testFailAttributeName(String queueUrl, String attributeName, String thatClause) {
    try {
      print("Testing sending message with message attribute name " + attributeName + " , should fail");
      SendMessageRequest sendMessageRequest = new SendMessageRequest();
      sendMessageRequest.setMessageBody("body");
      sendMessageRequest.setQueueUrl(queueUrl);
      MessageAttributeValue attributeValue = new MessageAttributeValue();
      attributeValue.setDataType("String");
      attributeValue.setStringValue("attribute value");
      sendMessageRequest.getMessageAttributes().put(attributeName, attributeValue);
      sqs.sendMessage(sendMessageRequest);
      assertThat(false, "Should fail sending a message with a message attribute name that " + thatClause);
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail sending a message with a message attribute name that " + thatClause);
    }
  }

  private int getLocalConfigInt(String propertySuffixInCapsAndUnderscores) throws IOException {
    String propertyName = "services.simplequeue." + propertySuffixInCapsAndUnderscores.toLowerCase();
    return Integer.parseInt(getConfigProperty(LOCAL_EUCTL_FILE, propertyName));
  }

}
