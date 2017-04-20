package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonWebServiceClient;
import com.amazonaws.Request;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.SendMessageBatchResult;
import com.amazonaws.services.sqs.model.SendMessageBatchResultEntry;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
public class TestSQSSendMessageBatch {


  private int MAX_MESSAGE_ATTRIBUTE_NAME_LENGTH;
  private int MAX_MESSAGE_ATTRIBUTE_TYPE_LENGTH;
  private int MAX_DELAY_SECONDS;
  private int MAX_MAXIMUM_MESSAGE_SIZE;
  private int MAX_NUM_BATCH_ENTRIES;
  private int MAX_BATCH_ID_LENGTH;
  private String account;
  private String otherAccount;

  private AmazonSQS accountSQSClient;
  private AmazonSQS otherAccountSQSClient;

  @BeforeClass
  public void init() throws Exception {
    print("### PRE SUITE SETUP - " + this.getClass().getSimpleName());

    try {
      getCloudInfoAndSqs();
      MAX_MESSAGE_ATTRIBUTE_NAME_LENGTH = getLocalConfigInt("MAX_MESSAGE_ATTRIBUTE_NAME_LENGTH");
      MAX_MESSAGE_ATTRIBUTE_TYPE_LENGTH = getLocalConfigInt("MAX_MESSAGE_ATTRIBUTE_TYPE_LENGTH");
      MAX_DELAY_SECONDS = getLocalConfigInt("MAX_DELAY_SECONDS");
      MAX_MAXIMUM_MESSAGE_SIZE = getLocalConfigInt("MAX_MAXIMUM_MESSAGE_SIZE");
      MAX_NUM_BATCH_ENTRIES = getLocalConfigInt("MAX_NUM_BATCH_ENTRIES");
      MAX_BATCH_ID_LENGTH = getLocalConfigInt("MAX_BATCH_ID_LENGTH");
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
  public void testSendMessageBatchNonExistentAccount() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testSendMessageBatchNonExistentAccount");
    String queueName = "queue_name_send_message_batch_nonexistent_account";

    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);

    try {
      SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest();
      SendMessageBatchRequestEntry sendMessageBatchRequestEntry = new SendMessageBatchRequestEntry();
      sendMessageBatchRequestEntry.setId("id");
      sendMessageBatchRequestEntry.setMessageBody("Hello");
      sendMessageBatchRequest.getEntries().add(sendMessageBatchRequestEntry);
      sendMessageBatchRequest.setQueueUrl(queueUrl.replace(accountId, "000000000000"));
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message on a queue from a non-existent account");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 404, "Correctly fail batch sending a message on a queue from a non-existent account");
    }
  }

  @Test
  public void testSendMessageBatchOtherAccount() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testSendMessageBatchOtherAccount");
    String queueName = "queue_name_send_message_batch_other_account";
    String otherAccountQueueUrl = otherAccountSQSClient.createQueue(queueName).getQueueUrl();
    try {
      SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest();
      SendMessageBatchRequestEntry sendMessageBatchRequestEntry = new SendMessageBatchRequestEntry();
      sendMessageBatchRequestEntry.setId("id");
      sendMessageBatchRequestEntry.setMessageBody("Hello");
      sendMessageBatchRequest.getEntries().add(sendMessageBatchRequestEntry);
      sendMessageBatchRequest.setQueueUrl(otherAccountQueueUrl);
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message on a queue from a different user");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail batch sending a message on a queue from a different user");
    }
  }

  @Test
  public void testSendMessageBatchNonExistentQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testSendMessageBatchNonExistentQueue");
    String queueName = "queue_name_send_message_batch_nonexistent_queue";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    try {
      SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest();
      SendMessageBatchRequestEntry sendMessageBatchRequestEntry = new SendMessageBatchRequestEntry();
      sendMessageBatchRequestEntry.setId("id");
      sendMessageBatchRequestEntry.setMessageBody("Hello");
      sendMessageBatchRequest.getEntries().add(sendMessageBatchRequestEntry);
      sendMessageBatchRequest.setQueueUrl(queueUrl + "-bogus");
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message to non-existent queue");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message to non-existent queue");
    }
  }

  @Test
  public void testSendMessageBatchDelaySeconds() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testSendMessageBatchDelaySeconds");
    String queueName = "queue_name_send_message_batch_delay_seconds";
    // set low values for delay seconds and visibility timeout to facilitate quicker responses
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);
    createQueueRequest.getAttributes().put("DelaySeconds", "0");
    createQueueRequest.getAttributes().put("VisibilityTimeout", "0");
    String queueUrl = accountSQSClient.createQueue(createQueueRequest).getQueueUrl();

    SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest();
    SendMessageBatchRequestEntry sendMessageBatchRequestEntry = new SendMessageBatchRequestEntry();
    sendMessageBatchRequestEntry.setId("id");
    sendMessageBatchRequestEntry.setMessageBody("body");
    sendMessageBatchRequestEntry.setDelaySeconds(-1);
    sendMessageBatchRequest.getEntries().add(sendMessageBatchRequestEntry);
    sendMessageBatchRequest.setQueueUrl(queueUrl);
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a delay seconds value that is too low");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a delay seconds value that is too low");
    }

    sendMessageBatchRequestEntry.setDelaySeconds(1 + MAX_DELAY_SECONDS);
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a delay seconds value that is too high");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a delay seconds value that is too high");
    }

    // see if a successful value can be set and have a message sent successfully.
    sendMessageBatchRequestEntry.setDelaySeconds(1);
    String messageId = accountSQSClient.sendMessageBatch(sendMessageBatchRequest).getSuccessful().get(0).getMessageId();
    // try this for 60 seconds, just arbitrary
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() - startTime < 60 * 1000L) {
      ReceiveMessageResult receiveMessagesResult = accountSQSClient.receiveMessage(queueUrl);
      if (receiveMessagesResult != null && receiveMessagesResult.getMessages() != null) {
        for (Message message: receiveMessagesResult.getMessages()) {
          if (message.getMessageId().equals(messageId)) {
            assertThat(messagesMatch(
                ImmutableMap.of(messageId, makeMessage("body")),
                ImmutableMap.of(message.getMessageId(), message)
              ),
              "messages should match"
            );
            return;
          }
        }
      }
      Thread.sleep(1000L);
    }
    assertThat(false, "never received the message that was sent.");
  }

  @Test
  public void testSendMessageBatchAttributeName() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testSendMessageBatchAttributeName");
    String queueName = "queue_name_send_message_batch_attribute_name";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();

    SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest();
    SendMessageBatchRequestEntry sendMessageBatchRequestEntry = new SendMessageBatchRequestEntry();
    sendMessageBatchRequestEntry.setId("id");
    sendMessageBatchRequestEntry.setMessageBody("Hello");
    sendMessageBatchRequest.getEntries().add(sendMessageBatchRequestEntry);
    sendMessageBatchRequest.setQueueUrl(queueUrl);

    MessageAttributeValue attributeValue = new MessageAttributeValue();
    attributeValue.setDataType("String");
    attributeValue.setStringValue("attribute value");

    // test null
    sendMessageBatchRequestEntry.getMessageAttributes().clear();
    sendMessageBatchRequestEntry.getMessageAttributes().put(null, attributeValue);
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a message attribute name that is null");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a message attribute name that is null");
    }

    // test empty
    sendMessageBatchRequestEntry.getMessageAttributes().clear();
    sendMessageBatchRequestEntry.getMessageAttributes().put("", attributeValue);
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a message attribute name that is empty");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a message attribute name that is empty");
    }

    // test too long
    sendMessageBatchRequestEntry.getMessageAttributes().clear();
    sendMessageBatchRequestEntry.getMessageAttributes().put(Strings.repeat("X", MAX_MESSAGE_ATTRIBUTE_NAME_LENGTH + 1), attributeValue);
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a message attribute name that is too long");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a message attribute name that is too long");
    }

    // test reserved prefix
    for (String awsStart: new String[]{"AWS.bob","aWs.bob","AmAzOn.bob","Amazon.bob"}) {
      sendMessageBatchRequestEntry.getMessageAttributes().clear();
      sendMessageBatchRequestEntry.getMessageAttributes().put(awsStart, attributeValue);
      try {
        accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
        assertThat(false, "Should fail batch sending a message with a message attribute name that starts with 'AWS.' or 'Amazon.'");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a message attribute name that starts with 'AWS.' or 'Amazon.'");
      }
    }

    // test contains consecutive dots
    sendMessageBatchRequestEntry.getMessageAttributes().clear();
    sendMessageBatchRequestEntry.getMessageAttributes().put("Test..attribute.dots", attributeValue);
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a message attribute name that contains successive '.' characters");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a message attribute name that contains successive '.' characters");
    }

    // test invalid characters
    sendMessageBatchRequestEntry.getMessageAttributes().clear();
    sendMessageBatchRequestEntry.getMessageAttributes().put("!@#$%^&*()", attributeValue);
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a message attribute name that contains invalid characters");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a message attribute name that contains invalid characters");
    }

    // test duplicate keys
    // send a message attribute twice (i.e. same key, different value.  Since the SDK uses a map, we need
    // to send the parameters directly.
    RequestHandler2 sendTwoAttributesWithSameName = new RequestHandler2() {
      public void beforeRequest(final Request<?> request) {
        request.addParameter("SendMessageBatchRequestEntry.1.MessageAttribute.1.Name", "MA1");
        request.addParameter("SendMessageBatchRequestEntry.1.MessageAttribute.1.Value.DataType", "String");
        request.addParameter("SendMessageBatchRequestEntry.1.MessageAttribute.1.Value.StringValue", "Value1");
        request.addParameter("SendMessageBatchRequestEntry.1.MessageAttribute.2.Name", "MA1");
        request.addParameter("SendMessageBatchRequestEntry.1.MessageAttribute.2.Value.DataType", "String");
        request.addParameter("SendMessageBatchRequestEntry.1.MessageAttribute.2.Value.StringValue", "Value2");
      }
    };
    ((AmazonWebServiceClient) accountSQSClient).addRequestHandler(sendTwoAttributesWithSameName);
    try {
      sendMessageBatchRequestEntry.getMessageAttributes().clear();
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with 'MA1' as attribute name twice");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with 'MA1' as attribute name twice");
    }
    ((AmazonWebServiceClient) accountSQSClient).removeRequestHandler(sendTwoAttributesWithSameName);
  }

  @Test
  public void testSendMessageBatchAttributeValue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testSendMessageBatchAttributeValue");
    String queueName = "queue_name_send_message_batch_attribute_value";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();

    SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest();
    SendMessageBatchRequestEntry sendMessageBatchRequestEntry = new SendMessageBatchRequestEntry();
    sendMessageBatchRequestEntry.setId("id");
    sendMessageBatchRequestEntry.setMessageBody("Hello");
    sendMessageBatchRequest.getEntries().add(sendMessageBatchRequestEntry);
    sendMessageBatchRequest.setQueueUrl(queueUrl);

    // test null value
    MessageAttributeValue messageAttributeValue = null;
    sendMessageBatchRequestEntry.getMessageAttributes().put("myAttribute", messageAttributeValue);
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a message attribute value that is null");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a message attribute value that is null");
    }

    // test null data type
    messageAttributeValue = new MessageAttributeValue();
    sendMessageBatchRequestEntry.getMessageAttributes().put("myAttribute", messageAttributeValue);
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a message attribute value that has a null data type");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a message attribute value that has a null data type");
    }

    // test empty data type
    messageAttributeValue.setDataType("");
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a message attribute value that has an empty data type");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a message attribute value that has an empty data type");
    }

    // test bad data type
    messageAttributeValue.setDataType("Turntable");
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a message attribute value that has an unsupported data type");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a message attribute value that has an unsupported data type");
    }

    // too long
    messageAttributeValue.setDataType("Number." + Strings.repeat("X",MAX_MESSAGE_ATTRIBUTE_TYPE_LENGTH));
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a message attribute value that has a too long data type");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a message attribute value that has a too long data type");
    }

    // unsupported binary list value
    messageAttributeValue.setDataType("Binary");
    messageAttributeValue.setBinaryListValues(Collections.singleton(ByteBuffer.wrap("binaryValue".getBytes(Charsets.UTF_8))));
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a message attribute value that has a binary attribute list value");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a message attribute value that has a binary attribute list value");
    }

    // unsupported string list value
    messageAttributeValue.setDataType("String");
    messageAttributeValue.setBinaryListValues(null);
    messageAttributeValue.setStringListValues(Collections.singleton("stringValue"));
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a message attribute value that has a string attribute list value");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a message attribute value that has a string attribute list value");
    }

    // no value
    messageAttributeValue.setStringListValues(null);
    for (String type: new String[]{"String","Number","Binary"}) {
      messageAttributeValue.setDataType(type);
      try {
        accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
        assertThat(false, "Should fail batch sending a message with a message attribute value that has no (" + type + ") value");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a message attribute value that has no (" + type + ") value");
      }
    }

    // multiple values
    messageAttributeValue.setDataType("String");
    messageAttributeValue.setStringValue("stringValue");
    messageAttributeValue.setBinaryValue(ByteBuffer.wrap("binaryValue".getBytes(Charsets.UTF_8)));
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a message attribute value that has multiple values");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a message attribute value that has multiple values");
    }

    // binary when string
    messageAttributeValue.setStringValue(null);
    messageAttributeValue.setBinaryValue(null);
    messageAttributeValue.setDataType("String");
    messageAttributeValue.setBinaryValue(ByteBuffer.wrap("binaryValue".getBytes(Charsets.UTF_8)));
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a message attribute value that uses a non-string value with a string data type");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a message attribute value that uses a non-string value with a string data type");
    }

    // binary when number
    messageAttributeValue.setStringValue(null);
    messageAttributeValue.setBinaryValue(null);
    messageAttributeValue.setDataType("Number");
    messageAttributeValue.setBinaryValue(ByteBuffer.wrap("binaryValue".getBytes(Charsets.UTF_8)));
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a message attribute value that uses a non-string value with a number data type");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a message attribute value that uses a non-string value with a number data type");
    }

    // string when binary
    messageAttributeValue.setStringValue(null);
    messageAttributeValue.setBinaryValue(null);
    messageAttributeValue.setDataType("Binary");
    messageAttributeValue.setStringValue("stringValue");
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a message attribute value that uses a non-binary value with a binary data type");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a message attribute value that uses a non-binary value with a binary data type");
    }

    // invalid string chars
    messageAttributeValue.setStringValue(null);
    messageAttributeValue.setBinaryValue(null);
    messageAttributeValue.setDataType("String");
    messageAttributeValue.setStringValue("\uFFFF");
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a message attribute value that contains invalid characters");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a message attribute value that contains invalid characters");
    }

    // invalid string chars
    messageAttributeValue.setStringValue(null);
    messageAttributeValue.setBinaryValue(null);
    messageAttributeValue.setDataType("Number");
    messageAttributeValue.setStringValue("X");
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a message attribute value that contains invalid characters as number");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a message attribute value that contains invalid characters as number");
    }
  }

  @Test
  public void testSendMessageBatchBody() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testSendMessageBatchBody");
    String queueName = "queue_name_send_message_batch_body";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();

    SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest();
    SendMessageBatchRequestEntry sendMessageBatchRequestEntry = new SendMessageBatchRequestEntry();
    sendMessageBatchRequestEntry.setId("id");
    sendMessageBatchRequest.getEntries().add(sendMessageBatchRequestEntry);
    sendMessageBatchRequest.setQueueUrl(queueUrl);


    // null body
    try {
      sendMessageBatchRequestEntry.setMessageBody((String) null);
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a null body");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a null body");
    }

    // empty body
    try {
      sendMessageBatchRequestEntry.setMessageBody("");
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a empty body");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a empty body");
    }

    // invalid characters
    // empty body
    try {
      sendMessageBatchRequestEntry.setMessageBody("\uFFFF");
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a bad chars in the body");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with bad chars in the body");
    }
  }

  @Test
  public void testSendMessageBatchTooLongMessage() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testSendMessageBatchTooLongMessage");
    String queueName = "queue_name_send_message_batch_length";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest();
    SendMessageBatchRequestEntry sendMessageBatchRequestEntry = new SendMessageBatchRequestEntry();
    sendMessageBatchRequestEntry.setId("id");
    sendMessageBatchRequest.getEntries().add(sendMessageBatchRequestEntry);
    sendMessageBatchRequest.setQueueUrl(queueUrl);

    // too long body
    try {
      sendMessageBatchRequestEntry.setMessageBody(Strings.repeat("X", MAX_MAXIMUM_MESSAGE_SIZE + 1));
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with too long a body");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with too long a body");
    }

    // try to create a message with too long a length when using body and  different attributes
    int totalBodyLength = MAX_MAXIMUM_MESSAGE_SIZE + 1;

    MessageAttributeValue messageAttributeValue = new MessageAttributeValue();
    messageAttributeValue.setDataType("String");
    totalBodyLength -= "String".getBytes(Charsets.UTF_8).length;
    messageAttributeValue.setStringValue("StringValue");
    totalBodyLength -= "StringValue".getBytes(Charsets.UTF_8).length;
    sendMessageBatchRequestEntry.getMessageAttributes().put("MA1", messageAttributeValue);
    totalBodyLength -= "MA1".getBytes(Charsets.UTF_8).length;

    messageAttributeValue = new MessageAttributeValue();
    messageAttributeValue.setDataType("Binary");
    totalBodyLength -= "Binary".getBytes(Charsets.UTF_8).length;
    byte[] binaryVal = "binaryValue".getBytes(Charsets.UTF_8);
    messageAttributeValue.setBinaryValue(ByteBuffer.wrap(binaryVal));
    totalBodyLength -= binaryVal.length;
    sendMessageBatchRequestEntry.getMessageAttributes().put("MA2", messageAttributeValue);
    totalBodyLength -= "MA2".getBytes(Charsets.UTF_8).length;
    sendMessageBatchRequestEntry.setMessageBody(Strings.repeat("X", totalBodyLength));
    sendMessageBatchRequest.setQueueUrl(queueUrl);
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message whose combined body and attribute length is too long");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message whose combined body and attribute length is too long");
    }
  }

  @Test
  public void testSendMessageBatchSuccess() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testSendMessageBatchSuccess");
    String queueName = "queue_name_send_message_batch_success";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    // now send a few messages, make sure they come back
    Message m1 = makeMessage("Body of Message 1");
    String messageId1 = accountSQSClient.sendMessageBatch(makeSingleMessageRequest(queueUrl, m1)).getSuccessful().get(0).getMessageId();
    Message m2 = makeMessage("Body of Message 2",
      "M2MA1", "String.baby", "String value m2Ma1",
      "M2MA2", "Number.baby", 5,
      "M2MA3", "Binary.baby", ByteBuffer.wrap(new byte[]{8, 6, 7, 5, 3, 0, 9}));
    String messageId2 = accountSQSClient.sendMessageBatch(makeSingleMessageRequest(queueUrl, m2)).getSuccessful().get(0).getMessageId();
    Message m3 = makeMessage("Body of Message 3",
      "M3MA1", "String.baby", "String value m3Ma1",
      "M3MA3", "Number.baby", 6,
      "M3MA3", "Binary.baby", ByteBuffer.wrap(new byte[]{42, 18, 97, 0, 0, 0, 14}));
    String messageId3 = accountSQSClient.sendMessageBatch(makeSingleMessageRequest(queueUrl, m3)).getSuccessful().get(0).getMessageId();
    Map<String, Message> receivedMessages = Maps.newHashMap();

    // try this for 60 seconds, just arbitrary
    long startTime = System.currentTimeMillis();
    while (receivedMessages.size() < 3 && System.currentTimeMillis() - startTime < 60 * 1000L) {
      ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
      receiveMessageRequest.setQueueUrl(queueUrl);
      receiveMessageRequest.setMessageAttributeNames(Collections.singleton("All"));
      ReceiveMessageResult receiveMessagesResult = accountSQSClient.receiveMessage(receiveMessageRequest);
      if (receiveMessagesResult != null && receiveMessagesResult.getMessages() != null) {
        for (Message message: receiveMessagesResult.getMessages()) {
          receivedMessages.put(message.getMessageId(), message);
        }
      }
    }
    Map<String, Message> sentMessages = ImmutableMap.<String, Message>builder().put(messageId1, m1).put(messageId2, m2).put(messageId3, m3).build();
    assertThat(messagesMatch(sentMessages, receivedMessages), "Sent messages match received messages");
  }

  Message makeMessage(String body, Object... attributes) {
    if (attributes != null && (attributes.length % 3 != 0)) {
      throw new IllegalArgumentException("Wrong number of arguments for attributes");
    }
    Message message = new Message();
    message.setBody(body);
    if (attributes != null) {
      for (int i=0;i<attributes.length; i+=3) {
        String attrName = (String) attributes[i];
        String type = (String) attributes[i+1];
        MessageAttributeValue messageAttributeValue = new MessageAttributeValue();
        messageAttributeValue.setDataType(type);
        Object value = attributes[i+2];
        if (value instanceof String || value instanceof Integer) {
          messageAttributeValue.setStringValue(value.toString());
        } else if (value instanceof ByteBuffer) {
          messageAttributeValue.setBinaryValue((ByteBuffer) value);
        } else {
          throw new IllegalArgumentException("Wrong data type for value");
        }
        message.getMessageAttributes().put(attrName, messageAttributeValue);
      }
    }
    return message;
  }

  @Test
  public void testSendMessageBatchTooFewMessages() {
    String queueName = "queue_name_send_message_batch_no_messages";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest();
    sendMessageBatchRequest.setQueueUrl(queueUrl);
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending with no messages");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with no messages");
    }
  }

  @Test
  public void testSendMessageBatchTooManyMessages() {
    String queueName = "queue_name_send_message_batch_too_many_messages";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest();
    for (int i=0;i<MAX_NUM_BATCH_ENTRIES + 1; i++) {
      SendMessageBatchRequestEntry sendMessageBatchRequestEntry = new SendMessageBatchRequestEntry();
      sendMessageBatchRequestEntry.setId("id" + i);
      sendMessageBatchRequestEntry.setMessageBody("hello");
      sendMessageBatchRequest.getEntries().add(sendMessageBatchRequestEntry);
    }
    sendMessageBatchRequest.setQueueUrl(queueUrl);
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending with too many messages");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with too many messages");
    }
  }

  @Test
  public void testSendMessageBatchTooLongTotal() {
    testInfo(this.getClass().getSimpleName() + " - testSendMessageBatchTooLongTotal");
    String queueName = "queue_name_send_message_batch_too_long_total";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest();
    SendMessageBatchRequestEntry sendMessageBatchRequestEntry0 = new SendMessageBatchRequestEntry();
    sendMessageBatchRequestEntry0.setId("id0");
    sendMessageBatchRequest.getEntries().add(sendMessageBatchRequestEntry0);
    sendMessageBatchRequest.setQueueUrl(queueUrl);

    // try to create a message with too long a length when using body and  different attributes
    int totalMessageLength = MAX_MAXIMUM_MESSAGE_SIZE + 1;

    MessageAttributeValue messageAttributeValue = new MessageAttributeValue();
    messageAttributeValue.setDataType("String");
    totalMessageLength -= "String".getBytes(Charsets.UTF_8).length;
    messageAttributeValue.setStringValue("StringValue");
    totalMessageLength -= "StringValue".getBytes(Charsets.UTF_8).length;
    sendMessageBatchRequestEntry0.getMessageAttributes().put("MA1", messageAttributeValue);
    totalMessageLength -= "MA1".getBytes(Charsets.UTF_8).length;

    messageAttributeValue = new MessageAttributeValue();
    messageAttributeValue.setDataType("Binary");
    totalMessageLength -= "Binary".getBytes(Charsets.UTF_8).length;
    byte[] binaryVal = "binaryValue".getBytes(Charsets.UTF_8);
    messageAttributeValue.setBinaryValue(ByteBuffer.wrap(binaryVal));
    totalMessageLength -= binaryVal.length;
    sendMessageBatchRequestEntry0.getMessageAttributes().put("MA2", messageAttributeValue);
    totalMessageLength -= "MA2".getBytes(Charsets.UTF_8).length;
    sendMessageBatchRequestEntry0.setMessageBody("body");
    totalMessageLength -= "body".getBytes(Charsets.UTF_8).length;

    SendMessageBatchRequestEntry sendMessageBatchRequestEntry1 = new SendMessageBatchRequestEntry();
    sendMessageBatchRequestEntry1.setId("id1");
    sendMessageBatchRequestEntry1.setMessageBody(Strings.repeat("X", totalMessageLength));
    sendMessageBatchRequest.getEntries().add(sendMessageBatchRequestEntry1);
    sendMessageBatchRequest.setQueueUrl(queueUrl);
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending messages with a combined length that is too long");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending combined length that is too long");
    }
  }

  @Test
  public void testSendMessageBatchId() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testSendMessageBatchId");
    String queueName = "queue_name_send_message_batch_id";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();

    SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest();
    SendMessageBatchRequestEntry sendMessageBatchRequestEntry = new SendMessageBatchRequestEntry();
    sendMessageBatchRequestEntry.setMessageBody("Hello");
    sendMessageBatchRequest.getEntries().add(sendMessageBatchRequestEntry);
    sendMessageBatchRequest.setQueueUrl(queueUrl);

    // test null
    sendMessageBatchRequestEntry.setId(null);
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a batch id that is null");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a batch id that is null");
    }

    // test empty
    sendMessageBatchRequestEntry.setId("");
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a batch id that is empty");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a batch id that is empty");
    }

    // test too long
    sendMessageBatchRequestEntry.setId(Strings.repeat("X", MAX_BATCH_ID_LENGTH + 1));
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a batch id that is too long");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a batch id that is too long");
    }

    // test invalid characters
    sendMessageBatchRequestEntry.setId("!@#$%^&*()");
    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending a message with a batch id that contains invalid characters");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with a batch id that contains invalid characters");
    }

    // test duplicate keys
    sendMessageBatchRequest = new SendMessageBatchRequest();
    SendMessageBatchRequestEntry sendMessageBatchRequestEntry0 = new SendMessageBatchRequestEntry();
    sendMessageBatchRequestEntry0.setId("id0");
    sendMessageBatchRequestEntry0.setMessageBody("body");
    sendMessageBatchRequest.getEntries().add(sendMessageBatchRequestEntry0);
    SendMessageBatchRequestEntry sendMessageBatchRequestEntry1 = new SendMessageBatchRequestEntry();
    sendMessageBatchRequestEntry1.setId("id0");
    sendMessageBatchRequestEntry1.setMessageBody("body");
    sendMessageBatchRequest.getEntries().add(sendMessageBatchRequestEntry1);
    sendMessageBatchRequest.setQueueUrl(queueUrl);

    try {
      accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should fail batch sending messages with duplicate ids");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail batch sending a message with duplicate ids");
    }
  }

  @Test
  public void testSendMessageBatchSuccessMultiple() {
    testInfo(this.getClass().getSimpleName() + " - testSendMessageBatchSuccess");
    String queueName = "queue_name_send_message_batch_success";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    // now send a few messages, make sure they come back
    Message m1 = makeMessage("Body of Message 1");
    Message m2 = makeMessage("Body of Message 2",
      "M2MA1", "String.baby", "String value m2Ma1",
      "M2MA2", "Number.baby", 5,
      "M2MA3", "Binary.baby", ByteBuffer.wrap(new byte[]{8, 6, 7, 5, 3, 0, 9}));
    Message m3 = makeMessage("Body of Message 3",
      "M3MA1", "String.baby", "String value m3Ma1",
      "M3MA3", "Number.baby", 6,
      "M3MA3", "Binary.baby", ByteBuffer.wrap(new byte[]{42, 18, 97, 0, 0, 0, 14}));
    SendMessageBatchResult sendMessageBatchResult = accountSQSClient.sendMessageBatch(makeMultipleMessageRequest(queueUrl, m1, m2, m3));
    Map<String, String> idToMessageIdMap = Maps.newHashMap();
    for (SendMessageBatchResultEntry entry: sendMessageBatchResult.getSuccessful()) {
      idToMessageIdMap.put(entry.getId(), entry.getMessageId());
    }
    String messageId1 = idToMessageIdMap.get("id0");
    String messageId2 = idToMessageIdMap.get("id1");
    String messageId3 = idToMessageIdMap.get("id2");
    Map<String, Message> receivedMessages = Maps.newHashMap();

    // try this for 60 seconds, just arbitrary
    long startTime = System.currentTimeMillis();
    while (receivedMessages.size() < 3 && System.currentTimeMillis() - startTime < 60 * 1000L) {
      ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
      receiveMessageRequest.setQueueUrl(queueUrl);
      receiveMessageRequest.setMessageAttributeNames(Collections.singleton("All"));
      ReceiveMessageResult receiveMessagesResult = accountSQSClient.receiveMessage(receiveMessageRequest);
      if (receiveMessagesResult != null && receiveMessagesResult.getMessages() != null) {
        for (Message message: receiveMessagesResult.getMessages()) {
          receivedMessages.put(message.getMessageId(), message);
        }
      }
    }
    Map<String, Message> sentMessages = ImmutableMap.<String, Message>builder().put(messageId1, m1).put(messageId2, m2).put(messageId3, m3).build();
    assertThat(messagesMatch(sentMessages, receivedMessages), "Sent messages match received messages");

  }

  @Test
  public void testMessagesMatch() throws Exception {
    Message M_BODY_ONLY_1 = makeMessage("Body 1");
    Message M_BODY_ONLY_2 = makeMessage("Body 2");
    Message M_STRING_ATTR_1 = makeMessage("Body", "MA1", "String", "String value 1");
    Message M_STRING_ATTR_2 = makeMessage("Body", "MA1", "String", "String value 2");
    Message M_STRING_ATTR_3 = makeMessage("Body", "MA1", "String.other", "String value 1");
    Message M_STRING_ATTR_4 = makeMessage("Body", "MA2", "String", "String value 1");
    Message M_NUM_ATTR_1 = makeMessage("Body", "MA1", "Number", 1);
    Message M_NUM_ATTR_2 = makeMessage("Body", "MA1", "Number", 2);
    Message M_NUM_ATTR_3 = makeMessage("Body", "MA1", "Number.other", 1);
    Message M_NUM_ATTR_4 = makeMessage("Body", "MA2", "Number", 1);
    Message M_BIN_ATTR_1 = makeMessage("Body", "MA1", "Binary", ByteBuffer.wrap(new byte[]{1}));
    Message M_BIN_ATTR_2 = makeMessage("Body", "MA1", "Binary", ByteBuffer.wrap(new byte[]{2}));
    Message M_BIN_ATTR_3 = makeMessage("Body", "MA1", "Binary.other", ByteBuffer.wrap(new byte[]{1}));
    Message M_BIN_ATTR_4 = makeMessage("Body", "MA2", "Binary", ByteBuffer.wrap(new byte[]{1}));
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

  private SendMessageBatchRequest makeSingleMessageRequest(String queueUrl, Message m) {
    SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest();
    sendMessageBatchRequest.setQueueUrl(queueUrl);
    SendMessageBatchRequestEntry sendMessageBatchRequestEntry = new SendMessageBatchRequestEntry();
    sendMessageBatchRequestEntry.setId("id");
    sendMessageBatchRequestEntry.setMessageBody(m.getBody());
    sendMessageBatchRequest.getEntries().add(sendMessageBatchRequestEntry);

    if (m.getMessageAttributes() != null) {
      for (String name: m.getMessageAttributes().keySet()) {
        sendMessageBatchRequestEntry.getMessageAttributes().put(name, m.getMessageAttributes().get(name));
      }
    }
    return sendMessageBatchRequest;
  }

  private SendMessageBatchRequest makeMultipleMessageRequest(String queueUrl, Message... mArray) {
    SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest();
    sendMessageBatchRequest.setQueueUrl(queueUrl);
    if (mArray != null) {
      int id = 0;
      for (Message m: mArray) {
        SendMessageBatchRequestEntry sendMessageBatchRequestEntry = new SendMessageBatchRequestEntry();
        sendMessageBatchRequestEntry.setId("id" + (id++));
        sendMessageBatchRequestEntry.setMessageBody(m.getBody());
        sendMessageBatchRequest.getEntries().add(sendMessageBatchRequestEntry);

        if (m.getMessageAttributes() != null) {
          for (String name: m.getMessageAttributes().keySet()) {
            sendMessageBatchRequestEntry.getMessageAttributes().put(name, m.getMessageAttributes().get(name));
          }
        }
      }
    }
    return sendMessageBatchRequest;
  }

  private int getLocalConfigInt(String propertySuffixInCapsAndUnderscores) throws IOException {
    String propertyName = "services.simplequeue." + propertySuffixInCapsAndUnderscores.toLowerCase();
    return Integer.parseInt(getConfigProperty(LOCAL_EUCTL_FILE, propertyName));
  }

}
